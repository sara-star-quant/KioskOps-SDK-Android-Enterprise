package com.sarastarquant.kioskops.sdk

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import com.sarastarquant.kioskops.sdk.anomaly.AnomalyAction
import com.sarastarquant.kioskops.sdk.anomaly.AnomalyDetector
import com.sarastarquant.kioskops.sdk.anomaly.StatisticalAnomalyDetector
import com.sarastarquant.kioskops.sdk.audit.AuditStatistics
import com.sarastarquant.kioskops.sdk.audit.AuditTrail
import com.sarastarquant.kioskops.sdk.audit.ChainVerificationResult
import com.sarastarquant.kioskops.sdk.audit.KeystoreAttestationProvider
import com.sarastarquant.kioskops.sdk.audit.PersistentAuditTrail
import com.sarastarquant.kioskops.sdk.compliance.DataRightsManager
import com.sarastarquant.kioskops.sdk.compliance.RetentionEnforcer
import com.sarastarquant.kioskops.sdk.crypto.AesGcmKeystoreCryptoProvider
import com.sarastarquant.kioskops.sdk.crypto.ConditionalCryptoProvider
import com.sarastarquant.kioskops.sdk.crypto.CryptoProvider
import com.sarastarquant.kioskops.sdk.crypto.FieldLevelEncryptor
import com.sarastarquant.kioskops.sdk.debug.DebugOverlay
import com.sarastarquant.kioskops.sdk.diagnostics.DiagnosticsExporter
import com.sarastarquant.kioskops.sdk.diagnostics.RemoteDiagnosticsTrigger
import com.sarastarquant.kioskops.sdk.diagnostics.TriggerResult
import com.sarastarquant.kioskops.sdk.pii.DataClassification
import com.sarastarquant.kioskops.sdk.pii.PiiAction
import com.sarastarquant.kioskops.sdk.pii.PiiDetector
import com.sarastarquant.kioskops.sdk.pii.PiiRedactor
import com.sarastarquant.kioskops.sdk.pii.RegexPiiDetector
import com.sarastarquant.kioskops.sdk.validation.JsonSchemaValidator
import com.sarastarquant.kioskops.sdk.validation.SchemaRegistry
import com.sarastarquant.kioskops.sdk.validation.UnknownEventTypeAction
import com.sarastarquant.kioskops.sdk.validation.ValidationResult
import com.sarastarquant.kioskops.sdk.fleet.DefaultDeviceGroupProvider
import com.sarastarquant.kioskops.sdk.fleet.DeviceGroupProvider
import com.sarastarquant.kioskops.sdk.fleet.DevicePostureCollector
import com.sarastarquant.kioskops.sdk.fleet.DiagnosticsUploader
import com.sarastarquant.kioskops.sdk.fleet.PolicyDriftDetector
import com.sarastarquant.kioskops.sdk.fleet.config.ConfigRollbackResult
import com.sarastarquant.kioskops.sdk.fleet.config.ConfigVersion
import com.sarastarquant.kioskops.sdk.fleet.config.RemoteConfigManager
import com.sarastarquant.kioskops.sdk.logging.LogExporter
import com.sarastarquant.kioskops.sdk.logging.RingLog
import com.sarastarquant.kioskops.sdk.queue.EnqueueResult
import com.sarastarquant.kioskops.sdk.queue.QueueRepository
import com.sarastarquant.kioskops.sdk.telemetry.EncryptedTelemetryStore
import com.sarastarquant.kioskops.sdk.util.Clock
import com.sarastarquant.kioskops.sdk.util.DeviceId
import com.sarastarquant.kioskops.sdk.util.Hashing
import com.sarastarquant.kioskops.sdk.work.DiagnosticsSchedulerWorker
import com.sarastarquant.kioskops.sdk.work.KioskOpsSyncWorker
import com.sarastarquant.kioskops.sdk.work.KioskOpsEventSyncWorker
import com.sarastarquant.kioskops.sdk.sync.SyncEngine
import com.sarastarquant.kioskops.sdk.sync.SyncOnceResult
import com.sarastarquant.kioskops.sdk.transport.AuthProvider
import com.sarastarquant.kioskops.sdk.transport.OkHttpTransport
import com.sarastarquant.kioskops.sdk.transport.RequestSigner
import com.sarastarquant.kioskops.sdk.transport.Transport
import com.sarastarquant.kioskops.sdk.transport.TransportResult
import com.sarastarquant.kioskops.sdk.transport.security.CertificatePinningInterceptor
import com.sarastarquant.kioskops.sdk.transport.security.CertificateTransparencyValidator
import com.sarastarquant.kioskops.sdk.transport.security.MtlsClientBuilder
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

@Suppress("TooManyFunctions", "LargeClass")
class KioskOpsSdk private constructor(
  private val appContext: Context,
  private val configProvider: () -> KioskOpsConfig,
  cryptoProviderOverride: CryptoProvider?,
  private val authProvider: AuthProvider?,
  private val requestSigner: RequestSigner?,
  transportOverride: Transport?,
  okHttpClientOverride: OkHttpClient?,
) {
  internal val logs = RingLog(appContext)

  private val clock: Clock = Clock.SYSTEM

  private fun cfg(): KioskOpsConfig = configProvider()

  private val driftDetector = PolicyDriftDetector(appContext)

  // v0.3.0 Fleet Operations
  /** Device group provider for fleet segmentation. */
  val deviceGroupProvider: DeviceGroupProvider by lazy {
    DefaultDeviceGroupProvider(appContext)
  }

  private val postureCollector = DevicePostureCollector(appContext, deviceGroupProvider)

  @Volatile private var diagnosticsUploader: DiagnosticsUploader? = null

  fun setDiagnosticsUploader(uploader: DiagnosticsUploader?) {
    diagnosticsUploader = uploader
  }

  // Queue encryption is controlled per-event via cfg.securityPolicy.encryptQueuePayloads
  private val queueCrypto: CryptoProvider = cryptoProviderOverride
    ?: ConditionalCryptoProvider(
      enabledProvider = { cfg().securityPolicy.encryptQueuePayloads },
      delegate = AesGcmKeystoreCryptoProvider(alias = "kioskops_queue_aesgcm_v1")
    )

  // Telemetry/audit encryption-at-rest is controlled by cfg.securityPolicy.encryptTelemetryAtRest
  private val telemetryCrypto: CryptoProvider = ConditionalCryptoProvider(
    enabledProvider = { cfg().securityPolicy.encryptTelemetryAtRest },
    delegate = AesGcmKeystoreCryptoProvider(alias = "kioskops_telemetry_aesgcm_v1")
  )

  // Export encryption is controlled by cfg.securityPolicy.encryptExportedLogs
  private val exportCrypto: CryptoProvider = ConditionalCryptoProvider(
    enabledProvider = { cfg().securityPolicy.encryptExportedLogs },
    delegate = AesGcmKeystoreCryptoProvider(alias = "kioskops_exports_aesgcm_v1")
  )

  // v0.8.0 Database encryption (SQLCipher) with v1.2.0 corruption handling.
  // Wraps the delegate factory (SQLCipher when db encryption is on, framework default otherwise)
  // so corruption surfaces to the host via KioskOpsErrorListener and lands in the audit trail
  // before Room's default delete-and-recreate runs.
  private val dbOpenHelperFactory: androidx.sqlite.db.SupportSQLiteOpenHelper.Factory =
    com.sarastarquant.kioskops.sdk.queue.CorruptionHandlingOpenHelperFactory(
      delegate = if (cfg().databaseEncryptionPolicy.enabled) {
        com.sarastarquant.kioskops.sdk.crypto.DatabaseEncryptionProvider.createFactory(appContext)
      } else {
        androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
      },
      onCorruption = { db ->
        val path = try { db.path } catch (_: Throwable) { null }
        logs.e("SDK", "Database corruption detected at ${path ?: "unknown"}")
        notifyError(
          KioskOpsError.StorageError(
            message = "Database corruption detected at ${path ?: "unknown"}; database will be recreated",
          )
        )
        audit.record(
          "database_corruption_detected",
          mapOf("path" to (path ?: "unknown")),
        )
      },
    )

  init {
    // Set audit database encryption before any getInstance() calls
    com.sarastarquant.kioskops.sdk.audit.db.AuditDatabase.setOpenHelperFactory(dbOpenHelperFactory)
  }

  private val queue = QueueRepository(
    appContext, logs, queueCrypto,
    openHelperFactory = dbOpenHelperFactory,
  )

  // v0.5.0 Validation & PII pipeline components
  /** @since 0.5.0 */
  val schemaRegistry = SchemaRegistry()

  private val schemaValidator: JsonSchemaValidator by lazy {
    JsonSchemaValidator(schemaRegistry)
  }

  @Volatile private var piiDetectorOverride: PiiDetector? = null

  private val piiDetector: PiiDetector
    get() = piiDetectorOverride ?: RegexPiiDetector(
      minimumConfidence = cfg().piiPolicy.minimumConfidence,
      fieldExclusions = cfg().piiPolicy.fieldExclusions,
    )

  private val piiRedactor = PiiRedactor()

  @Volatile private var anomalyDetectorOverride: AnomalyDetector? = null

  private val anomalyDetector: AnomalyDetector
    get() = anomalyDetectorOverride ?: StatisticalAnomalyDetector(cfg().anomalyPolicy)

  private val fieldEncryptor: FieldLevelEncryptor by lazy {
    FieldLevelEncryptor(queueCrypto)
  }

  /**
   * Set a custom PII detector implementation.
   * @since 0.5.0
   */
  fun setPiiDetector(detector: PiiDetector?) {
    piiDetectorOverride = detector
  }

  /**
   * Set a custom anomaly detector implementation.
   * @since 0.5.0
   */
  fun setAnomalyDetector(detector: AnomalyDetector?) {
    anomalyDetectorOverride = detector
  }

  // v0.7.0 Error callback
  @Volatile private var errorListener: KioskOpsErrorListener? = null

  /**
   * Set a listener for non-fatal SDK operational errors.
   *
   * Pass `null` to remove the listener. The listener may be called from background threads.
   *
   * @since 0.7.0
   */
  fun setErrorListener(listener: KioskOpsErrorListener?) {
    errorListener = listener
  }

  private fun notifyError(error: KioskOpsError) {
    @Suppress("TooGenericExceptionCaught")
    try {
      errorListener?.onError(error)
    } catch (t: Throwable) {
      // Listener must not crash the SDK. Surface the listener-side bug at warn level so
      // host-app teams can notice their handler is failing; before 1.2 we swallowed
      // silently and the listener misbehavior was invisible.
      logs.w(
        "SDK",
        "KioskOpsErrorListener threw: ${t::class.java.simpleName}: ${t.message ?: "no message"}",
      )
    }
  }

  private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
  }

  private val okHttpClient: OkHttpClient = okHttpClientOverride ?: buildSecureHttpClient()

  private val telemetry = EncryptedTelemetryStore(
    context = appContext,
    policyProvider = { cfg().telemetryPolicy },
    retentionProvider = { cfg().retentionPolicy },
    clock = clock,
    crypto = telemetryCrypto
  )

  private val audit = AuditTrail(
    context = appContext,
    retentionProvider = { cfg().retentionPolicy },
    clock = clock,
    crypto = telemetryCrypto
  )

  // Persistent audit trail with Room database and optional signing
  private val persistentAudit: PersistentAuditTrail by lazy {
    PersistentAuditTrail(
      context = appContext,
      retentionProvider = { cfg().retentionPolicy },
      clock = clock,
      attestationProvider = if (cfg().securityPolicy.signAuditEntries) {
        { KeystoreAttestationProvider(appContext) }
      } else {
        null
      }
    )
  }

  // v0.3.0 Remote Configuration Manager
  /** Remote configuration manager for config versioning and A/B testing. */
  val remoteConfigManager: RemoteConfigManager by lazy {
    RemoteConfigManager.create(
      context = appContext,
      policyProvider = { cfg().remoteConfigPolicy },
      auditTrail = audit,
      clock = clock,
    )
  }

  // v0.3.0 Remote Diagnostics Trigger
  private val diagnosticsTrigger: RemoteDiagnosticsTrigger by lazy {
    RemoteDiagnosticsTrigger(
      context = appContext,
      policyProvider = { cfg().diagnosticsSchedulePolicy },
      auditTrail = audit,
      clock = clock,
    )
  }

  private val transport: Transport = transportOverride ?: OkHttpTransport(
    client = okHttpClient,
    json = json,
    logs = logs,
    authProvider = authProvider,
    requestSigner = requestSigner
  )

  private val syncEngine: SyncEngine by lazy {
    SyncEngine(
      context = appContext,
      cfgProvider = { cfg() },
      queue = queue,
      transport = transport,
      logs = logs,
      telemetry = telemetry,
      audit = persistentAudit,
      clock = clock,
    )
  }

  private val logExporter = LogExporter(appContext, logs, exportCrypto)

  private val diagnostics = DiagnosticsExporter(
    context = appContext,
    cfgProvider = { cfg() },
    logs = logs,
    logExporter = logExporter,
    telemetry = telemetry,
    audit = audit,
    clock = clock,
    sdkVersion = SDK_VERSION,
    queueDepthProvider = { queue.countActive() },
    quarantinedSummaryProvider = { queue.quarantinedSummaries(100) },
    postureProvider = { postureCollector.collect() },
    policyHashProvider = {
      Hashing.sha256Base64Url(driftDetector.sanitizedProjection(cfg()))
    }
  )

  val dataRights: DataRightsManager by lazy {
    DataRightsManager(appContext, telemetry, audit, queue, persistentAudit,
      requireAuthorization = cfg().requireDataRightsAuthorization)
  }

  /**
   * Set the authorization callback for data rights operations.
   *
   * On shared kiosk devices, this prevents one user from accessing or erasing
   * another user's local data. Required when using CUI or CJIS presets.
   *
   * @since 1.0.0
   */
  fun setDataRightsAuthorizer(authorizer: com.sarastarquant.kioskops.sdk.compliance.DataRightsAuthorizer?) {
    dataRights.setAuthorizer(authorizer)
  }

  private val retentionEnforcer: RetentionEnforcer by lazy {
    RetentionEnforcer(queue, telemetry, audit, persistentAudit, clock)
  }

  fun currentConfig(): KioskOpsConfig = cfg()

  fun devicePosture() = postureCollector.collect()

  fun currentPolicyHash(): String = Hashing.sha256Base64Url(driftDetector.sanitizedProjection(cfg()))


  /**
   * Backwards-compatible enqueue API.
   *
   * For enterprise pilots, prefer [enqueueDetailed] to get rejection reasons.
   *
   * **Threading**: Main-safe. Database IO is dispatched internally.
   */
  @JvmOverloads
  suspend fun enqueue(
    type: String,
    payloadJson: String,
    userId: String? = null,
  ): Boolean {
    return enqueueDetailed(type = type, payloadJson = payloadJson, userId = userId).isAccepted
  }

  /**
   * Enqueue an event through the v0.5.0 pipeline:
   *
   * 1. Size guardrail
   * 2. Legacy denylist (fast-path, deprecated)
   * 3. Event Validation (JsonSchemaValidator)
   * 4. PII Detection -> REJECT / REDACT / FLAG_AND_ALLOW
   * 5. Anomaly Detection (statistical + optional ML)
   * 6. Data Classification tagging
   * 7. Field-Level Encryption (specified JSON paths)
   * 8. Queue Pressure check
   * 9. Idempotency Key
   * 10. Document Encryption (PayloadCodec -> AES-256-GCM)
   * 11. Room Insert (with userId, dataClassification, anomalyScore)
   * 12. Audit Record (PersistentAuditTrail)
   *
   * **Threading**: Main-safe. Database and crypto IO dispatched internally.
   *
   * @param userId Optional user identifier for GDPR data subject tracking.
   * @since 0.5.0 userId parameter, pipeline steps 3-7 added
   */
  @Suppress("ReturnCount", "CyclomaticComplexMethod", "LongMethod")
  @JvmOverloads
  suspend fun enqueueDetailed(
    type: String,
    payloadJson: String,
    stableEventId: String? = null,
    idempotencyKeyOverride: String? = null,
    userId: String? = null,
  ): EnqueueResult {
    val cfg = cfg()
    var processedPayload = payloadJson

    // Step 3: Event Validation
    runValidation(type, processedPayload, cfg)?.let { return it }

    // Step 4: PII Detection
    val piiResult = runPiiScan(type, processedPayload, userId, cfg)
    piiResult.rejected?.let { return it }
    processedPayload = piiResult.processedPayload
    val piiRedactedFields = piiResult.redactedFields

    // Step 5: Anomaly Detection
    val anomalyResult = runAnomalyCheck(type, processedPayload, cfg)
    anomalyResult.rejected?.let { return it }
    val anomalyScore = anomalyResult.score

    // Step 6: Data Classification tagging
    val classification = if (cfg.dataClassificationPolicy.enabled) {
      if (piiRedactedFields != null) cfg.dataClassificationPolicy.piiClassification.name
      else cfg.dataClassificationPolicy.defaultClassification.name
    } else {
      null
    }

    // Step 7: Field-Level Encryption.
    // If encryption fails we REJECT the event. Silently falling back to plaintext
    // would leak the same PII the feature exists to protect.
    if (cfg.fieldEncryptionPolicy.enabled) {
      val fields = cfg.fieldEncryptionPolicy.fieldsForEventType(type)
      if (fields.isNotEmpty()) {
        try {
          processedPayload = fieldEncryptor.encryptFields(processedPayload, fields)
        } catch (e: com.sarastarquant.kioskops.sdk.crypto.FieldEncryptionException) {
          val rejection = EnqueueResult.Rejected.FieldEncryptionFailed(
            reason = e.message ?: "field encryption failed",
          )
          persistentAudit.record(
            "event_rejected",
            mapOf("type" to type, "reason" to "FieldEncryptionFailed"),
          )
          telemetry.emit(
            "event_rejected",
            mapOf("type" to type, "reason" to "FieldEncryptionFailed"),
          )
          return rejection
        }
      }
    }

    // Steps 8-11: Queue enqueue (size guardrail, pressure, idempotency, document encryption, Room insert)
    val res = queue.enqueue(
      type, processedPayload, cfg,
      stableEventId = stableEventId,
      idempotencyKeyOverride = idempotencyKeyOverride,
      userId = userId,
      dataClassification = classification,
      anomalyScore = anomalyScore,
    )

    // Step 12: Audit and telemetry
    when (res) {
      is EnqueueResult.Accepted -> {
        persistentAudit.record("event_enqueued", mapOf("type" to type), userId = userId)
        telemetry.emit("event_enqueued", mapOf("type" to type))
        if (res.droppedOldest > 0) {
          persistentAudit.record("queue_overflow_dropped", mapOf("dropped" to res.droppedOldest.toString()))
          telemetry.emit("queue_overflow_dropped", mapOf("dropped" to res.droppedOldest.toString(), "strategy" to "DROP_OLDEST"))
        }
        if (piiRedactedFields != null) {
          return EnqueueResult.PiiRedacted(
            id = res.id,
            idempotencyKey = res.idempotencyKey,
            redactedFields = piiRedactedFields,
            droppedOldest = res.droppedOldest,
          )
        }
      }
      is EnqueueResult.Rejected -> {
        persistentAudit.record("event_rejected", mapOf("type" to type, "reason" to res::class.java.simpleName))
        telemetry.emit("event_rejected", mapOf("type" to type, "reason" to res::class.java.simpleName))
      }
      is EnqueueResult.PiiRedacted -> { /* unreachable from queue.enqueue */ }
    }
    return res
  }

  private class PiiScanResult(
    val rejected: EnqueueResult.Rejected? = null,
    val processedPayload: String,
    val redactedFields: List<String>? = null,
  )

  private class AnomalyCheckResult(
    val rejected: EnqueueResult.Rejected? = null,
    val score: Float? = null,
  )

  private suspend fun runValidation(
    type: String,
    payload: String,
    cfg: KioskOpsConfig,
  ): EnqueueResult.Rejected? {
    if (!cfg.validationPolicy.enabled) return null
    return try {
      val vResult = schemaValidator.validate(type, payload)
      when {
        vResult is ValidationResult.Valid -> null
        vResult is ValidationResult.Invalid && cfg.validationPolicy.strictMode -> {
          persistentAudit.record("event_rejected", mapOf("type" to type, "reason" to "validation_failed"))
          telemetry.emit("event_rejected", mapOf("type" to type, "reason" to "validation_failed"))
          EnqueueResult.Rejected.ValidationFailed(vResult.errors)
        }
        vResult is ValidationResult.SchemaNotFound &&
            cfg.validationPolicy.unknownEventTypeAction == UnknownEventTypeAction.REJECT -> {
          persistentAudit.record("event_rejected", mapOf("type" to type, "reason" to "schema_not_found"))
          telemetry.emit("event_rejected", mapOf("type" to type, "reason" to "schema_not_found"))
          EnqueueResult.Rejected.ValidationFailed(
            listOf("No schema registered for event type '$type'")
          )
        }
        else -> null
      }
    } catch (_: Exception) {
      null // Fail-safe: validation failure must not block the pipeline
    }
  }

  private suspend fun runPiiScan(
    type: String,
    payload: String,
    userId: String?,
    cfg: KioskOpsConfig,
  ): PiiScanResult {
    val passThrough = PiiScanResult(processedPayload = payload)
    val scanResult = if (cfg.piiPolicy.enabled) {
      try { piiDetector.scan(payload) } catch (_: Exception) { null }
    } else {
      null
    }
    if (scanResult == null || !scanResult.hasPii) return passThrough
    return when (cfg.piiPolicy.action) {
      PiiAction.REJECT -> {
        val findings = scanResult.findings.map { "${it.jsonPath}:${it.piiType}" }
        persistentAudit.record("event_rejected", mapOf("type" to type, "reason" to "pii_detected"), userId = userId)
        telemetry.emit("event_rejected", mapOf("type" to type, "reason" to "pii_detected"))
        PiiScanResult(
          rejected = EnqueueResult.Rejected.PiiDetected(findings),
          processedPayload = payload,
        )
      }
      PiiAction.REDACT_VALUE -> {
        val redaction = piiRedactor.redact(payload, scanResult.findings)
        PiiScanResult(
          processedPayload = redaction.redactedJson,
          redactedFields = redaction.redactedPaths,
        )
      }
      PiiAction.FLAG_AND_ALLOW -> passThrough
    }
  }

  private suspend fun runAnomalyCheck(
    type: String,
    payload: String,
    cfg: KioskOpsConfig,
  ): AnomalyCheckResult {
    if (!cfg.anomalyPolicy.enabled) return AnomalyCheckResult()
    return try {
      val anomalyResult = anomalyDetector.analyze(type, payload)
      if (anomalyResult.recommendedAction == AnomalyAction.REJECT) {
        persistentAudit.record("event_rejected", mapOf("type" to type, "reason" to "anomaly_rejected"))
        telemetry.emit("event_rejected", mapOf("type" to type, "reason" to "anomaly_rejected"))
        AnomalyCheckResult(
          rejected = EnqueueResult.Rejected.AnomalyRejected(anomalyResult.score, anomalyResult.reasons),
          score = anomalyResult.score,
        )
      } else {
        AnomalyCheckResult(score = anomalyResult.score)
      }
    } catch (_: Exception) {
      AnomalyCheckResult() // Fail-safe: anomaly detection failure must not block the pipeline
    }
  }

  /** **Threading**: Main-safe. */
  suspend fun queueDepth(): Long = queue.countActive()

  /**
   * Returns lightweight metadata for quarantined events (no payload).
   * Useful for support dashboards and diagnostics.
   *
   * **Threading**: Main-safe. Database IO dispatched internally.
   */
  @JvmOverloads
  suspend fun quarantinedEvents(limit: Int = 50): List<com.sarastarquant.kioskops.sdk.queue.QuarantinedEventSummary> =
    queue.quarantinedSummaries(limit)

  /**
   * Opt-in network sync.
   *
   * Returns a TransportResult so callers (and WorkManager) can decide whether to retry.
   *
   * **Threading**: Main-safe. Network IO dispatched to OkHttp dispatcher.
   */
  suspend fun syncOnce(): TransportResult<SyncOnceResult> {
    val cfg = cfg()
    if (!cfg.syncPolicy.enabled) {
      telemetry.emit("sync_disabled", mapOf("syncResult" to "disabled"))
      return TransportResult.Success(SyncOnceResult(attempted = 0, sent = 0, permanentFailed = 0, transientFailed = 0, rejected = 0))
    }
    telemetry.emit("sync_once_requested", mapOf("syncResult" to "requested"))
    val res = syncEngine.flushOnce()
    when (res) {
      is TransportResult.Success -> telemetry.emit(
        "sync_once_success",
        mapOf(
          "batchSize" to res.value.attempted.toString(),
          "sent" to res.value.sent.toString(),
          "rejected" to res.value.rejected.toString(),
          "httpStatus" to res.httpStatus.toString(),
          "syncResult" to "success"
        )
      )
      is TransportResult.TransientFailure -> {
        telemetry.emit(
          "sync_once_transient_failure",
          mapOf(
            "httpStatus" to (res.httpStatus?.toString() ?: ""),
            "syncResult" to "transient_failure"
          )
        )
        notifyError(KioskOpsError.SyncFailed(res.message, res.httpStatus, res.cause))
      }
      is TransportResult.PermanentFailure -> {
        telemetry.emit(
          "sync_once_permanent_failure",
          mapOf(
            "httpStatus" to (res.httpStatus?.toString() ?: ""),
            "syncResult" to "permanent_failure"
          )
        )
        notifyError(KioskOpsError.SyncFailed(res.message, res.httpStatus, res.cause))
      }
    }
    return res
  }

  /**
   * Maximalist heartbeat:
   * - emits a redacted heartbeat (allow-list keys)
   * - detects policy drift (local)
   * - records an audit event
   * - applies retention across queue + telemetry + audit + exported artifacts
   *
   * **Threading**: Main-safe. Database and file IO dispatched internally.
   */
  @JvmOverloads
  suspend fun heartbeat(reason: String = "periodic") {
    lastHeartbeatReason = reason
    val cfg = cfg()
    val now = clock.nowMs()
    val depth = queue.countActive()

    val posture = postureCollector.collect()
    val drift = driftDetector.checkAndStore(cfg = cfg, nowMs = now)

    val fields = mutableMapOf(
      "reason" to reason,
      "queueDepth" to depth.toString(),
      "sdkVersion" to SDK_VERSION,
      "os" to android.os.Build.VERSION.SDK_INT.toString(),
      "model" to (android.os.Build.MODEL ?: "unknown"),
      "manufacturer" to (android.os.Build.MANUFACTURER ?: "unknown"),
      "securityPatch" to (posture.securityPatch ?: ""),
      "isDeviceOwner" to posture.isDeviceOwner.toString(),
      "isInLockTaskMode" to posture.isLockTaskPermitted.toString(),
      "policyHash" to drift.currentHash,
      "policyDrifted" to drift.drifted.toString(),
    )

    if (drift.drifted) {
      fields["policyPrevHash"] = drift.previousHash ?: ""
      persistentAudit.record("policy_drift_detected", mapOf("prev" to (drift.previousHash ?: ""), "curr" to drift.currentHash))
      telemetry.emit(
        "policy_drift_detected",
        mapOf(
          "policyPrevHash" to (drift.previousHash ?: ""),
          "policyHash" to drift.currentHash,
          "sdkVersion" to SDK_VERSION
        )
      )
    }

    if (cfg.telemetryPolicy.includeDeviceId) {
      fields["deviceId"] = DeviceId.get(appContext)
    }

    telemetry.emit("heartbeat", fields)
    persistentAudit.record("heartbeat", mapOf("reason" to reason, "queueDepth" to depth.toString()))

    // Retention across storages (centralized via RetentionEnforcer)
    retentionEnforcer.enforce(cfg)
    purgeOldExports(cfg)
  }

  /** **Threading**: Main-safe. File IO dispatched internally. */
  suspend fun exportDiagnostics(): File {
    persistentAudit.record("diagnostics_export_requested")
    telemetry.emit("diagnostics_export_requested", mapOf("sdkVersion" to SDK_VERSION))
    val out = diagnostics.export()
    persistentAudit.record("diagnostics_export_created", mapOf("file" to out.name))
    return out
  }

  /**
   * Host-controlled diagnostics upload.
   *
   * Returns false if no uploader is configured.
   * The SDK never auto-uploads.
   *
   * **Threading**: Main-safe. File and network IO dispatched internally.
   */
  @JvmOverloads
  suspend fun uploadDiagnosticsNow(metadata: Map<String, String> = emptyMap()): Boolean {
    val uploader = diagnosticsUploader ?: return false
    persistentAudit.record("diagnostics_upload_requested")
    telemetry.emit("diagnostics_upload_requested", mapOf("sdkVersion" to SDK_VERSION))

    val file = exportDiagnostics()

    val cfg = cfg()
    val meta = buildMap {
      putAll(metadata)
      put("sdkVersion", SDK_VERSION)
      put("locationId", cfg.locationId)
      cfg.telemetryPolicy.regionTag?.let { put("regionTag", it) }
    }

    uploader.upload(file, meta)

    persistentAudit.record("diagnostics_upload_completed")
    telemetry.emit("diagnostics_upload_completed", mapOf("sdkVersion" to SDK_VERSION))
    return true
  }

  /**
   * Verify the integrity of the persistent audit trail.
   *
   * Checks that the hash chain is unbroken and all event hashes are valid.
   * For high-security deployments with signed entries, also verifies signatures.
   *
   * @param fromTs Optional start timestamp (inclusive).
   * @param toTs Optional end timestamp (inclusive).
   * @return Verification result indicating chain validity or location of break.
   *
   * **Threading**: Main-safe. Database IO dispatched internally.
   */
  @JvmOverloads
  suspend fun verifyAuditIntegrity(
    fromTs: Long? = null,
    toTs: Long? = null,
  ): ChainVerificationResult {
    return persistentAudit.verifyChainIntegrity(fromTs, toTs)
  }

  /**
   * Get statistics about the persistent audit trail.
   *
   * @return Statistics including event counts, time range, and chain generation.
   *
   * **Threading**: Main-safe. Database IO dispatched internally.
   */
  suspend fun getAuditStatistics(): AuditStatistics {
    return persistentAudit.getStatistics()
  }

  /**
   * Export a range of signed audit events to a file.
   *
   * The exported file is a gzipped JSONL file containing all audit events
   * in the specified time range, including signatures and chain metadata.
   *
   * @param fromTs Start timestamp.
   * @param toTs End timestamp.
   * **Threading**: Main-safe. File and database IO dispatched internally.
   *
   * @return The exported file.
   */
  suspend fun exportSignedAuditRange(fromTs: Long, toTs: Long): File {
    persistentAudit.record("audit_export_requested", mapOf("fromTs" to fromTs.toString(), "toTs" to toTs.toString()))
    telemetry.emit("audit_export_requested", mapOf("sdkVersion" to SDK_VERSION))
    return persistentAudit.exportSignedAuditRange(fromTs, toTs)
  }

  // ========================================================================
  // v0.3.0 Fleet Operations APIs
  // ========================================================================

  /**
   * Get available config versions for rollback.
   *
   * **Threading**: Main-safe. Database IO dispatched via Dispatchers.IO.
   *
   * @return List of available config versions, newest first.
   * @since 0.3.0
   */
  suspend fun getConfigVersionHistory(): List<ConfigVersion> =
    remoteConfigManager.getAvailableVersions()

  /**
   * Rollback to a previous configuration version.
   *
   * Security (BSI APP.4.4.A5): Rollback is blocked if target version
   * is below the minimum allowed version configured in RemoteConfigPolicy.
   *
   * @param targetVersion The version number to restore.
   * **Threading**: Main-safe. Database IO dispatched via Dispatchers.IO.
   *
   * @return Result indicating success or failure reason.
   * @since 0.3.0
   */
  suspend fun rollbackConfig(targetVersion: Long): ConfigRollbackResult =
    remoteConfigManager.rollbackToVersion(targetVersion)

  /**
   * Get A/B test variant for an experiment.
   *
   * Assignment is deterministic based on device ID and experiment ID
   * to ensure consistent experience across app restarts.
   *
   * @param experimentId Unique experiment identifier.
   * @param variants List of variant names.
   * @return Assigned variant name.
   * **Threading**: Main-safe. No IO; pure deterministic assignment.
   * @since 0.3.0
   */
  fun getAbTestVariant(experimentId: String, variants: List<String>): String =
    remoteConfigManager.getAbVariant(experimentId, variants)

  /**
   * Process a remote diagnostics trigger request.
   *
   * Security (BSI APP.4.4.A7):
   * - Rate limited by maxRemoteTriggersPerDay
   * - Cooldown enforced between triggers
   * - All triggers are audit logged
   *
   * @param triggerId Unique identifier for this trigger.
   * @param metadata Additional context.
   * **Threading**: Main-safe. File IO dispatched internally.
   *
   * @return Result indicating acceptance or rejection.
   * @since 0.3.0
   */
  @JvmOverloads
  suspend fun processRemoteDiagnosticsTrigger(
    triggerId: String,
    metadata: Map<String, String> = emptyMap(),
  ): TriggerResult {
    val result = diagnosticsTrigger.processTrigger(triggerId, metadata)
    if (result is TriggerResult.Accepted) {
      exportDiagnostics()
    }
    return result
  }

  /**
   * Get remaining remote diagnostics triggers allowed today.
   *
   * @return Number of triggers remaining.
   * **Threading**: Main-safe. In-memory counter read; no IO.
   * @since 0.3.0
   */
  fun getRemainingDiagnosticsTriggersToday(): Int =
    diagnosticsTrigger.getRemainingTriggersToday()

  /**
   * Schedules periodic heartbeat work.
   * The worker intentionally does NOT upload data; it only maintains local observability.
   *
   * **Threading**: Main-safe. Delegates to WorkManager.enqueueUniquePeriodicWork which
   * hands off to the WorkManager scheduler thread.
   */
  fun applySchedulingFromConfig() {
    val cfg = cfg()
    val heartbeat = PeriodicWorkRequestBuilder<KioskOpsSyncWorker>(cfg.syncIntervalMinutes, TimeUnit.MINUTES)
      .addTag(KioskOpsSyncWorker.WORK_TAG)
      .build()

    val wm = WorkManager.getInstance(appContext)
    // KEEP preserves WorkManager's exponential backoff counter across restarts; UPDATE would
    // replace an existing run and reset backoff, effectively retrying immediately after a
    // crash-loop. Config changes that legitimately need to re-schedule should go through a
    // dedicated reschedule path that cancels and re-enqueues.
    wm.enqueueUniquePeriodicWork(KioskOpsSyncWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, heartbeat)

    // Network sync is scheduled only when explicitly enabled.
    if (cfg.syncPolicy.enabled) {
      val constraints = Constraints.Builder()
        .setRequiredNetworkType(if (cfg.syncPolicy.requireUnmeteredNetwork) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

      val netSync = PeriodicWorkRequestBuilder<KioskOpsEventSyncWorker>(cfg.syncIntervalMinutes, TimeUnit.MINUTES)
        .addTag(KioskOpsEventSyncWorker.WORK_TAG)
        .setConstraints(constraints)
        .build()

      wm.enqueueUniquePeriodicWork(KioskOpsEventSyncWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, netSync)
    }

    // v0.3.0: Schedule diagnostics collection based on policy
    DiagnosticsSchedulerWorker.schedule(appContext, cfg.diagnosticsSchedulePolicy)
  }

  fun logger(): RingLog = logs

  /**
   * Debug overlay for development diagnostics.
   * @since 0.5.0
   */
  val debugOverlay: DebugOverlay by lazy {
    DebugOverlay(
      cfgProvider = { cfg() },
      queue = queue,
      persistentAudit = persistentAudit,
      policyHashProvider = { Hashing.sha256Base64Url(driftDetector.sanitizedProjection(cfg())) },
    )
  }

  // ========================================================================
  // v0.7.0 Health Check & Java Interop
  // ========================================================================

  /**
   * Returns a structured health snapshot of the SDK.
   *
   * **Threading**: Main-safe. Dispatches to IO for the queue depth query.
   *
   * @since 0.7.0
   */
  suspend fun healthCheck(): HealthCheckResult {
    val cfg = cfg()
    return HealthCheckResult(
      isInitialized = true,
      queueDepth = queue.countActive(),
      syncEnabled = cfg.syncPolicy.enabled,
      lastHeartbeatReason = lastHeartbeatReason,
      authProviderConfigured = authProvider != null,
      encryptionEnabled = cfg.securityPolicy.encryptQueuePayloads,
      sdkVersion = SDK_VERSION,
    )
  }

  @Volatile private var lastHeartbeatReason: String? = null

  // ========================================================================
  // v0.8.0 Reactive APIs
  // ========================================================================

  /**
   * Observe queue depth as a Room-reactive [kotlinx.coroutines.flow.Flow].
   *
   * Emits the current queue depth whenever the underlying `queue_events` table changes
   * in a way that affects the non-SENT row count (insert, state transition, delete).
   * Does not tick on a timer, so the flow is quiet when the queue is quiet.
   *
   * **Behavior change in 1.2.0**: previously timer-based, emitting every `intervalMs`;
   * now emits on database change only. The [intervalMs] parameter is retained for
   * binary compatibility with 0.8.0-1.1.x callers but is ignored. UI consumers that
   * relied on the ticker as a refresh cue should drive their own ticker and combine.
   *
   * @param intervalMs Ignored since 1.2.0; retained for source/binary compatibility.
   * **Threading**: Main-safe. Collection dispatches Room IO on the collector's context.
   * @since 0.8.0
   */
  @Suppress("UNUSED_PARAMETER")
  fun queueDepthFlow(intervalMs: Long = 5000L): kotlinx.coroutines.flow.Flow<Long> =
    queue.countActiveFlow()

  /**
   * Observe SDK health status as a [kotlinx.coroutines.flow.Flow].
   *
   * Emits a [HealthCheckResult] on either a queue-depth change or a fixed interval,
   * whichever fires first. The ticker ensures that fields that are not Room-reactive
   * (connectivity, auth state, last sync timestamp) still refresh on schedule; the
   * DB-reactive arm ensures queue-depth UI updates are immediate.
   *
   * @param intervalMs Fallback tick interval in milliseconds. Default 10000ms.
   * **Threading**: Main-safe. Each emission runs [healthCheck] which dispatches IO
   * internally; the ticker runs on the collector's context.
   * @since 0.8.0
   */
  fun healthStatusFlow(intervalMs: Long = 10000L): kotlinx.coroutines.flow.Flow<HealthCheckResult> {
    val ticker = kotlinx.coroutines.flow.flow {
      while (true) {
        emit(Unit)
        kotlinx.coroutines.delay(intervalMs)
      }
    }
    return kotlinx.coroutines.flow.combine(
      queue.countActiveFlow(),
      ticker,
    ) { _, _ -> healthCheck() }
  }

  /**
   * Observe configuration changes as a [kotlinx.coroutines.flow.Flow].
   *
   * Emits events when config is applied, rejected, or rolled back via
   * [RemoteConfigManager]. Useful for reacting to config changes in the UI
   * or triggering downstream operations.
   *
   * **Threading**: Main-safe. Backed by a `MutableSharedFlow` with `DROP_OLDEST`
   * overflow since 1.2.0, so a slow collector cannot back-pressure the applier.
   * @since 0.9.0
   */
  fun configUpdateFlow(): kotlinx.coroutines.flow.Flow<com.sarastarquant.kioskops.sdk.fleet.config.ConfigUpdateEvent> =
    remoteConfigManager.configUpdateFlow()

  // ========================================================================
  // v0.8.0 SDK Lifecycle
  // ========================================================================

  private val sdkJob = kotlinx.coroutines.SupervisorJob()

  private val javaInteropScope = kotlinx.coroutines.CoroutineScope(
    sdkJob + kotlinx.coroutines.Dispatchers.IO
  )

  /**
   * Gracefully shut down the SDK.
   *
   * Flushes a final audit record and telemetry event before cancelling the SDK's coroutine
   * scope, so the teardown entry actually reaches the audit trail. After cancellation, the
   * SDK instance is no longer usable; call [init] to create a new instance.
   *
   * **Threading**: Main-safe. Final record/emit run under `NonCancellable` on the SDK's
   * IO scope; this suspends until those writes complete, then cancels the scope.
   * @since 0.8.0
   */
  suspend fun shutdown() {
    // Record teardown while the scope is still live. Wrap in NonCancellable in case the
    // caller's coroutine is itself being cancelled (e.g. Activity onDestroy).
    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
      persistentAudit.record("sdk_shutdown")
      telemetry.emit("sdk_shutdown", mapOf("sdkVersion" to SDK_VERSION))
    }
    sdkJob.cancel()
    INSTANCE = null
  }

  /**
   * Java-friendly wrapper for [enqueue].
   * Returns a [java.util.concurrent.CompletableFuture] that completes on a background thread.
   *
   * @since 0.7.0
   */
  @JvmOverloads
  fun enqueueBlocking(
    type: String,
    payloadJson: String,
    userId: String? = null,
  ): java.util.concurrent.CompletableFuture<Boolean> {
    val future = java.util.concurrent.CompletableFuture<Boolean>()
    javaInteropScope.launch {
      @Suppress("TooGenericExceptionCaught")
      try { future.complete(enqueue(type, payloadJson, userId)) }
      catch (e: Exception) { future.completeExceptionally(e) }
    }
    return future
  }

  /**
   * Java-friendly wrapper for [enqueueDetailed].
   * @since 0.7.0
   */
  @JvmOverloads
  fun enqueueDetailedBlocking(
    type: String,
    payloadJson: String,
    stableEventId: String? = null,
    idempotencyKeyOverride: String? = null,
    userId: String? = null,
  ): java.util.concurrent.CompletableFuture<com.sarastarquant.kioskops.sdk.queue.EnqueueResult> {
    val future = java.util.concurrent.CompletableFuture<com.sarastarquant.kioskops.sdk.queue.EnqueueResult>()
    javaInteropScope.launch {
      @Suppress("TooGenericExceptionCaught")
      try { future.complete(enqueueDetailed(type, payloadJson, stableEventId, idempotencyKeyOverride, userId)) }
      catch (e: Exception) { future.completeExceptionally(e) }
    }
    return future
  }

  /**
   * Java-friendly wrapper for [syncOnce].
   * @since 0.7.0
   */
  fun syncOnceBlocking(): java.util.concurrent.CompletableFuture<TransportResult<SyncOnceResult>> {
    val future = java.util.concurrent.CompletableFuture<TransportResult<SyncOnceResult>>()
    javaInteropScope.launch {
      @Suppress("TooGenericExceptionCaught")
      try { future.complete(syncOnce()) }
      catch (e: Exception) { future.completeExceptionally(e) }
    }
    return future
  }

  /**
   * Java-friendly wrapper for [heartbeat].
   * @since 0.7.0
   */
  @JvmOverloads
  fun heartbeatBlocking(reason: String = "periodic"): java.util.concurrent.CompletableFuture<Unit> {
    val future = java.util.concurrent.CompletableFuture<Unit>()
    javaInteropScope.launch {
      @Suppress("TooGenericExceptionCaught")
      try { future.complete(heartbeat(reason)) }
      catch (e: Exception) { future.completeExceptionally(e) }
    }
    return future
  }

  /**
   * Java-friendly wrapper for [queueDepth].
   * @since 0.7.0
   */
  fun queueDepthBlocking(): java.util.concurrent.CompletableFuture<Long> {
    val future = java.util.concurrent.CompletableFuture<Long>()
    javaInteropScope.launch {
      @Suppress("TooGenericExceptionCaught")
      try { future.complete(queueDepth()) }
      catch (e: Exception) { future.completeExceptionally(e) }
    }
    return future
  }

  /**
   * Java-friendly wrapper for [healthCheck].
   * @since 0.7.0
   */
  fun healthCheckBlocking(): java.util.concurrent.CompletableFuture<HealthCheckResult> {
    val future = java.util.concurrent.CompletableFuture<HealthCheckResult>()
    javaInteropScope.launch {
      @Suppress("TooGenericExceptionCaught")
      try { future.complete(healthCheck()) }
      catch (e: Exception) { future.completeExceptionally(e) }
    }
    return future
  }

  private fun purgeOldExports(cfg: KioskOpsConfig) {
    val days = cfg.retentionPolicy.retainLogsDays.coerceAtLeast(1)
    val cutoffMs = clock.nowMs() - days.toLong() * 24 * 60 * 60 * 1000

    val dirs = listOf(appContext.cacheDir, File(appContext.filesDir, "kioskops_logs"))
    for (dir in dirs) {
      val files = dir.listFiles() ?: continue
      for (f in files) {
        val name = f.name
        val isRelevant = name.startsWith("kioskops_diagnostics_") || name.startsWith("kioskops_log_") || name.startsWith("kioskops_logs_")
        if (!isRelevant) continue
        if (f.lastModified() < cutoffMs) {
          f.delete()
        }
      }
    }
  }

  /**
   * Build OkHttpClient with transport security features.
   *
   * Pins run during the TLS handshake via OkHttp's native [okhttp3.CertificatePinner], so a
   * misconfigured or rogue certificate fails the connection before any request body is
   * transmitted. Pin failures surface through an [okhttp3.EventListener] into the audit
   * trail and the host error listener.
   *
   * Certificate Transparency uses [CertificateTransparencyValidator]'s SCT-presence check
   * for now. Full SCT verification (signature + inclusion proof + IANA log-list integration
   * per RFC 6962) is tracked for v1.3; see ROADMAP.md. The placeholder is still safer than
   * disabling CT entirely: a certificate without any SCT extension is rejected.
   *
   * mTLS is applied last so its SSLSocketFactory sees all previous builder mutations.
   */
  @Suppress("DEPRECATION")
  private fun buildSecureHttpClient(): OkHttpClient {
    val snapshot = cfg()
    val policy = snapshot.transportSecurityPolicy
    val syncPolicy = snapshot.syncPolicy

    val builder = OkHttpClient.Builder()
      .connectTimeout(syncPolicy.connectTimeoutSeconds, TimeUnit.SECONDS)
      .readTimeout(syncPolicy.readTimeoutSeconds, TimeUnit.SECONDS)
      .writeTimeout(syncPolicy.writeTimeoutSeconds, TimeUnit.SECONDS)
      .callTimeout(syncPolicy.callTimeoutSeconds, TimeUnit.SECONDS)

    if (policy.certificatePins.isNotEmpty()) {
      builder.certificatePinner(buildCertificatePinner(policy))
      builder.eventListenerFactory(buildPinFailureEventListenerFactory())
    }

    CertificateTransparencyValidator.fromPolicy(policy) { hostname, reason ->
      logs.w("TransportSecurity", "CT validation failed for $hostname: $reason")
      audit.record("certificate_transparency_failure", mapOf("hostname" to hostname, "reason" to reason))
    }?.let { validator ->
      builder.addInterceptor(validator)
    }

    return MtlsClientBuilder.fromPolicy(builder.build(), policy)
  }

  private fun buildCertificatePinner(
    policy: com.sarastarquant.kioskops.sdk.transport.security.TransportSecurityPolicy,
  ): okhttp3.CertificatePinner {
    val pinnerBuilder = okhttp3.CertificatePinner.Builder()
    for (pin in policy.certificatePins) {
      for (sha256 in pin.sha256Pins) {
        val formatted = if (sha256.startsWith("sha256/")) sha256 else "sha256/$sha256"
        pinnerBuilder.add(pin.hostname, formatted)
      }
    }
    return pinnerBuilder.build()
  }

  private fun buildPinFailureEventListenerFactory(): okhttp3.EventListener.Factory {
    return okhttp3.EventListener.Factory { _ ->
      object : okhttp3.EventListener() {
        override fun connectFailed(
          call: okhttp3.Call,
          inetSocketAddress: java.net.InetSocketAddress,
          proxy: java.net.Proxy,
          protocol: okhttp3.Protocol?,
          ioe: java.io.IOException,
        ) {
          if (ioe is javax.net.ssl.SSLPeerUnverifiedException) {
            val host = call.request().url.host
            val reason = ioe.message ?: ioe::class.java.simpleName
            logs.w("TransportSecurity", "Certificate pinning failed for $host: $reason")
            audit.record(
              "certificate_pin_failure",
              mapOf("hostname" to host, "reason" to reason),
            )
          }
        }
      }
    }
  }

  companion object {
    @JvmStatic val SDK_VERSION: String = BuildConfig.SDK_VERSION

    // Rows older than this in SENDING on init are assumed crash-abandoned. Five minutes is
    // well past OkHttp's default call timeout and typical worker budget, so genuinely in-flight
    // requests in a concurrent process won't be reset mid-send.
    private const val STALE_SENDING_WINDOW_MS = 5L * 60L * 1000L

    @Volatile private var INSTANCE: KioskOpsSdk? = null

    @JvmStatic
    @JvmOverloads
    fun init(
      context: Context,
      configProvider: () -> KioskOpsConfig,
      cryptoProviderOverride: CryptoProvider? = null,
      authProvider: AuthProvider? = null,
      requestSigner: RequestSigner? = null,
      transportOverride: Transport? = null,
      okHttpClientOverride: OkHttpClient? = null,
    ): KioskOpsSdk {
      if (INSTANCE != null) {
        throw KioskOpsAlreadyInitializedException()
      }
      val appCtx = context.applicationContext
      val created = KioskOpsSdk(
        appCtx,
        configProvider,
        cryptoProviderOverride,
        authProvider,
        requestSigner,
        transportOverride,
        okHttpClientOverride
      )
      INSTANCE = created
      created.logs.i("SDK", "Initialized")
      created.audit.record("sdk_initialized")
      created.telemetry.emit("sdk_initialized", mapOf("sdkVersion" to SDK_VERSION))
      // Rescue SENDING rows stranded by a prior process crash. Gate on an age > STALE_SENDING_WINDOW_MS
      // so any in-flight rows from a concurrent process (rare but possible) are left alone.
      created.javaInteropScope.launch {
        val staleBefore = created.clock.nowMs() - STALE_SENDING_WINDOW_MS
        created.queue.reconcileStaleSending(staleBefore)
      }
      created.applySchedulingFromConfig()
      com.sarastarquant.kioskops.sdk.lifecycle.SdkLifecycleObserver.register(
        sdkProvider = { INSTANCE },
        scope = created.javaInteropScope,
      )
      return created
    }

    @JvmStatic
    fun get(): KioskOpsSdk = INSTANCE ?: throw KioskOpsNotInitializedException()

    @JvmStatic
    fun getOrNull(): KioskOpsSdk? = INSTANCE

    @androidx.annotation.VisibleForTesting
    internal fun resetForTesting() {
      INSTANCE?.sdkJob?.cancel()
      INSTANCE = null
    }
  }
}
