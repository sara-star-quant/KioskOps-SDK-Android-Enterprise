package com.peterz.kioskops.sdk

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import com.peterz.kioskops.sdk.audit.AuditStatistics
import com.peterz.kioskops.sdk.audit.AuditTrail
import com.peterz.kioskops.sdk.audit.ChainVerificationResult
import com.peterz.kioskops.sdk.audit.KeystoreAttestationProvider
import com.peterz.kioskops.sdk.audit.PersistentAuditTrail
import com.peterz.kioskops.sdk.compliance.DataRightsManager
import com.peterz.kioskops.sdk.crypto.AesGcmKeystoreCryptoProvider
import com.peterz.kioskops.sdk.crypto.ConditionalCryptoProvider
import com.peterz.kioskops.sdk.crypto.CryptoProvider
import com.peterz.kioskops.sdk.diagnostics.DiagnosticsExporter
import com.peterz.kioskops.sdk.diagnostics.RemoteDiagnosticsTrigger
import com.peterz.kioskops.sdk.diagnostics.TriggerResult
import com.peterz.kioskops.sdk.fleet.DefaultDeviceGroupProvider
import com.peterz.kioskops.sdk.fleet.DeviceGroupProvider
import com.peterz.kioskops.sdk.fleet.DevicePostureCollector
import com.peterz.kioskops.sdk.fleet.DiagnosticsUploader
import com.peterz.kioskops.sdk.fleet.PolicyDriftDetector
import com.peterz.kioskops.sdk.fleet.config.ConfigRollbackResult
import com.peterz.kioskops.sdk.fleet.config.ConfigVersion
import com.peterz.kioskops.sdk.fleet.config.RemoteConfigManager
import com.peterz.kioskops.sdk.logging.LogExporter
import com.peterz.kioskops.sdk.logging.RingLog
import com.peterz.kioskops.sdk.queue.QueueRepository
import com.peterz.kioskops.sdk.telemetry.EncryptedTelemetryStore
import com.peterz.kioskops.sdk.util.Clock
import com.peterz.kioskops.sdk.util.DeviceId
import com.peterz.kioskops.sdk.util.Hashing
import com.peterz.kioskops.sdk.work.DiagnosticsSchedulerWorker
import com.peterz.kioskops.sdk.work.KioskOpsSyncWorker
import com.peterz.kioskops.sdk.work.KioskOpsEventSyncWorker
import com.peterz.kioskops.sdk.sync.SyncEngine
import com.peterz.kioskops.sdk.sync.SyncOnceResult
import com.peterz.kioskops.sdk.transport.AuthProvider
import com.peterz.kioskops.sdk.transport.OkHttpTransport
import com.peterz.kioskops.sdk.transport.RequestSigner
import com.peterz.kioskops.sdk.transport.Transport
import com.peterz.kioskops.sdk.transport.TransportResult
import com.peterz.kioskops.sdk.transport.security.CertificatePinningInterceptor
import com.peterz.kioskops.sdk.transport.security.CertificateTransparencyValidator
import com.peterz.kioskops.sdk.transport.security.MtlsClientBuilder
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

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

  private val queue = QueueRepository(appContext, logs, queueCrypto)

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
      crypto = telemetryCrypto,
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

  private val syncEngine = SyncEngine(
    context = appContext,
    cfgProvider = { cfg() },
    queue = queue,
    transport = transport,
    logs = logs,
    telemetry = telemetry,
    audit = audit,
    clock = clock
  )

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

  val dataRights = DataRightsManager(appContext, telemetry, audit)

  fun currentConfig(): KioskOpsConfig = cfg()

  fun devicePosture() = postureCollector.collect()

  fun currentPolicyHash(): String = Hashing.sha256Base64Url(driftDetector.sanitizedProjection(cfg()))


  /**
   * Backwards-compatible enqueue API.
   *
   * For enterprise pilots, prefer [enqueueDetailed] to get rejection reasons.
   */
  suspend fun enqueue(type: String, payloadJson: String): Boolean {
    return enqueueDetailed(type = type, payloadJson = payloadJson).isAccepted
  }

  /**
   * Enqueue an event with optional stable id for deterministic idempotency.
   *
   * - If [idempotencyKeyOverride] is provided, it is used as-is.
   * - Else if stableEventId is provided and deterministic idempotency is enabled,
   *   the SDK computes a deterministic key (HMAC-SHA256).
   */
  suspend fun enqueueDetailed(
    type: String,
    payloadJson: String,
    stableEventId: String? = null,
    idempotencyKeyOverride: String? = null,
  ): com.peterz.kioskops.sdk.queue.EnqueueResult {
    val res = queue.enqueue(type, payloadJson, cfg(), stableEventId = stableEventId, idempotencyKeyOverride = idempotencyKeyOverride)
    when (res) {
      is com.peterz.kioskops.sdk.queue.EnqueueResult.Accepted -> {
        audit.record("event_enqueued", mapOf("type" to type))
        telemetry.emit("event_enqueued", mapOf("type" to type))
        if (res.droppedOldest > 0) {
          audit.record("queue_overflow_dropped", mapOf("dropped" to res.droppedOldest.toString()))
          telemetry.emit("queue_overflow_dropped", mapOf("dropped" to res.droppedOldest.toString(), "strategy" to "DROP_OLDEST"))
        }
      }
      is com.peterz.kioskops.sdk.queue.EnqueueResult.Rejected -> {
        audit.record("event_rejected", mapOf("type" to type, "reason" to res::class.java.simpleName))
        telemetry.emit("event_rejected", mapOf("type" to type, "reason" to res::class.java.simpleName))
      }
    }
    return res
  }

  suspend fun queueDepth(): Long = queue.countActive()

  /**
   * Returns lightweight metadata for quarantined events (no payload).
   * Useful for support dashboards and diagnostics.
   */
  suspend fun quarantinedEvents(limit: Int = 50): List<com.peterz.kioskops.sdk.queue.QuarantinedEventSummary> =
    queue.quarantinedSummaries(limit)

  /**
   * Opt-in network sync.
   *
   * Returns a TransportResult so callers (and WorkManager) can decide whether to retry.
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
      is TransportResult.TransientFailure -> telemetry.emit(
        "sync_once_transient_failure",
        mapOf(
          "httpStatus" to (res.httpStatus?.toString() ?: ""),
          "syncResult" to "transient_failure"
        )
      )
      is TransportResult.PermanentFailure -> telemetry.emit(
        "sync_once_permanent_failure",
        mapOf(
          "httpStatus" to (res.httpStatus?.toString() ?: ""),
          "syncResult" to "permanent_failure"
        )
      )
    }
    return res
  }

  /**
   * Maximalist heartbeat:
   * - emits a redacted heartbeat (allow-list keys)
   * - detects policy drift (local)
   * - records an audit event
   * - applies retention across queue + telemetry + audit + exported artifacts
   */
  suspend fun heartbeat(reason: String = "periodic") {
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
      audit.record("policy_drift_detected", mapOf("prev" to (drift.previousHash ?: ""), "curr" to drift.currentHash))
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
    audit.record("heartbeat", mapOf("reason" to reason, "queueDepth" to depth.toString()))

    // Retention across storages
    queue.applyRetention(cfg)
    telemetry.purgeOldFiles()
    audit.purgeOldFiles()
    persistentAudit.applyRetention()
    purgeOldExports(cfg)
  }

  suspend fun exportDiagnostics(): File {
    audit.record("diagnostics_export_requested")
    telemetry.emit("diagnostics_export_requested", mapOf("sdkVersion" to SDK_VERSION))
    val out = diagnostics.export()
    audit.record("diagnostics_export_created", mapOf("file" to out.name))
    return out
  }

  /**
   * Host-controlled diagnostics upload.
   *
   * Returns false if no uploader is configured.
   * The SDK never auto-uploads.
   */
  suspend fun uploadDiagnosticsNow(metadata: Map<String, String> = emptyMap()): Boolean {
    val uploader = diagnosticsUploader ?: return false
    audit.record("diagnostics_upload_requested")
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

    audit.record("diagnostics_upload_completed")
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
   */
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
   * @return The exported file.
   */
  suspend fun exportSignedAuditRange(fromTs: Long, toTs: Long): File {
    audit.record("audit_export_requested", mapOf("fromTs" to fromTs.toString(), "toTs" to toTs.toString()))
    telemetry.emit("audit_export_requested", mapOf("sdkVersion" to SDK_VERSION))
    return persistentAudit.exportSignedAuditRange(fromTs, toTs)
  }

  // ========================================================================
  // v0.3.0 Fleet Operations APIs
  // ========================================================================

  /**
   * Get available config versions for rollback.
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
   * @return Result indicating acceptance or rejection.
   * @since 0.3.0
   */
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
   * @since 0.3.0
   */
  fun getRemainingDiagnosticsTriggersToday(): Int =
    diagnosticsTrigger.getRemainingTriggersToday()

  /**
   * Schedules periodic heartbeat work.
   * The worker intentionally does NOT upload data; it only maintains local observability.
   */
  fun applySchedulingFromConfig() {
    val cfg = cfg()
    val heartbeat = PeriodicWorkRequestBuilder<KioskOpsSyncWorker>(cfg.syncIntervalMinutes, TimeUnit.MINUTES)
      .addTag(KioskOpsSyncWorker.WORK_TAG)
      .build()

    val wm = WorkManager.getInstance(appContext)
    wm.enqueueUniquePeriodicWork(KioskOpsSyncWorker.WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, heartbeat)

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

      wm.enqueueUniquePeriodicWork(KioskOpsEventSyncWorker.WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, netSync)
    }

    // v0.3.0: Schedule diagnostics collection based on policy
    DiagnosticsSchedulerWorker.schedule(appContext, cfg.diagnosticsSchedulePolicy)
  }

  fun logger(): RingLog = logs

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
   * Applies certificate pinning, mTLS, and CT validation based on
   * the transport security policy from the initial config.
   */
  private fun buildSecureHttpClient(): OkHttpClient {
    val policy = cfg().transportSecurityPolicy

    var client = OkHttpClient.Builder().build()

    // Apply certificate pinning interceptor
    CertificatePinningInterceptor.fromPolicy(policy) { hostname, reason ->
      logs.w("TransportSecurity", "Certificate pinning failed for $hostname: $reason")
      audit.record("certificate_pin_failure", mapOf("hostname" to hostname, "reason" to reason))
    }?.let { interceptor ->
      client = client.newBuilder()
        .addInterceptor(interceptor)
        .build()
    }

    // Apply Certificate Transparency validation
    CertificateTransparencyValidator.fromPolicy(policy) { hostname, reason ->
      logs.w("TransportSecurity", "CT validation failed for $hostname: $reason")
      audit.record("certificate_transparency_failure", mapOf("hostname" to hostname, "reason" to reason))
    }?.let { validator ->
      client = client.newBuilder()
        .addInterceptor(validator)
        .build()
    }

    // Apply mTLS configuration
    client = MtlsClientBuilder.fromPolicy(client, policy)

    return client
  }

  companion object {
    const val SDK_VERSION = "0.3.0"

    @Volatile private var INSTANCE: KioskOpsSdk? = null

    fun init(
      context: Context,
      configProvider: () -> KioskOpsConfig,
      cryptoProviderOverride: CryptoProvider? = null,
      authProvider: AuthProvider? = null,
      requestSigner: RequestSigner? = null,
      transportOverride: Transport? = null,
      okHttpClientOverride: OkHttpClient? = null,
    ): KioskOpsSdk {
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
      created.applySchedulingFromConfig()
      return created
    }

    fun get(): KioskOpsSdk = INSTANCE ?: throw IllegalStateException("KioskOpsSdk not initialized")

    fun getOrNull(): KioskOpsSdk? = INSTANCE
  }
}
