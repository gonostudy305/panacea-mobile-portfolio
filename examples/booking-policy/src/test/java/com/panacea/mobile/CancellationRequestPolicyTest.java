package com.panacea.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.panacea.mobile.utils.CancellationRequestPolicy;

import org.junit.Test;

public class CancellationRequestPolicyTest {
    @Test
    public void keyIsStableAcrossForegroundAndBackgroundRetries() {
        assertEquals("cancel:BK-123", CancellationRequestPolicy.idempotencyKey(" BK-123 "));
        assertEquals(CancellationRequestPolicy.idempotencyKey("BK-123"),
                CancellationRequestPolicy.idempotencyKey("BK-123"));
    }

    @Test
    public void emptyBookingIdFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> CancellationRequestPolicy.idempotencyKey("  "));
    }
}
