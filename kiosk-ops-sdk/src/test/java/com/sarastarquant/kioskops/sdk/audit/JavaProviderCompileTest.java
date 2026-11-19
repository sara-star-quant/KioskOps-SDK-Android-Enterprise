/*
 * Copyright (c) 2026 SARA STAR QUANT LLC
 * SPDX-License-Identifier: BUSL-1.1
 */

package com.sarastarquant.kioskops.sdk.audit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.security.cert.X509Certificate;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

/** A Java implementor written against 1.6.0 must still compile and link. */
public class JavaProviderCompileTest {

  private static final class LegacyProvider implements DeviceAttestationProvider {
    @Nullable @Override public String signAuditEntry(@NotNull String payload) { return null; }
    @Override public boolean verifySignature(@NotNull String payload, @NotNull String signature) { return false; }
    @Nullable @Override public List<X509Certificate> getAttestationChain() { return null; }
    @Override public boolean isHardwareBacked() { return false; }
    @Nullable @Override public byte[] getAttestationBlob() { return null; }
  }

  @Test
  public void legacyImplementorGetsDefaults() {
    DeviceAttestationProvider p = new LegacyProvider();
    assertNull(p.signingKeyId());
    assertNull(p.signingPublicKey());
    assertFalse(p.verifyWithPublicKey(new byte[] {1}, "p", "s"));
  }
}
