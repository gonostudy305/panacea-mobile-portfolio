package com.panacea.mobile.utils;

/** Stable request identity for booking cancellation retries across UI and background sync. */
public final class CancellationRequestPolicy {
    private CancellationRequestPolicy() { }

    public static String idempotencyKey(String bookingId) {
        String normalized = bookingId == null ? "" : bookingId.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("bookingId is required");
        }
        return "cancel:" + normalized;
    }
}
