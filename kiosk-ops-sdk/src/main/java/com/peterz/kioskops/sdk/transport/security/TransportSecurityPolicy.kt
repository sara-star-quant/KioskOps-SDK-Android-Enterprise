/*
 * Copyright (c) 2026 Petro Zverkov
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.peterz.kioskops.sdk.transport.security

/**
 * Transport layer security policy configuration.
 *
 * This policy controls certificate pinning, mutual TLS authentication,
 * and certificate transparency validation for SDK network communications.
 *
 * @property certificatePins List of certificate pins for hostname-based validation.
 *           Multiple pins per hostname support key rotation (primary + backup).
 * @property mtlsConfig Mutual TLS configuration for client certificate authentication.
 *           When set, the SDK will present a client certificate during TLS handshake.
 * @property certificateTransparencyEnabled When true, validates certificates against
 *           Certificate Transparency logs. Requires network access to CT log servers.
 */
data class TransportSecurityPolicy(
  val certificatePins: List<CertificatePin> = emptyList(),
  val mtlsConfig: MtlsConfig? = null,
  val certificateTransparencyEnabled: Boolean = false,
)

/**
 * Certificate pin for a specific hostname.
 *
 * Each pin represents a SHA-256 hash of a certificate's Subject Public Key Info (SPKI).
 * Multiple pins per hostname allow for key rotation without service disruption.
 *
 * @property hostname The hostname to pin. Supports wildcard prefixes (e.g., "*.example.com").
 *           Wildcard only matches one level (*.example.com matches api.example.com but not a.b.example.com).
 * @property sha256Pins List of Base64-encoded SHA-256 pin hashes for this hostname.
 *           Include both current and backup pins to support rotation.
 */
data class CertificatePin(
  val hostname: String,
  val sha256Pins: List<String>,
)

/**
 * Mutual TLS configuration.
 *
 * When configured, the SDK will present a client certificate during TLS handshake.
 * This provides two-way authentication where both client and server verify each other.
 *
 * @property clientCertificateProvider Provider for client certificate and private key.
 */
data class MtlsConfig(
  val clientCertificateProvider: ClientCertificateProvider,
)
