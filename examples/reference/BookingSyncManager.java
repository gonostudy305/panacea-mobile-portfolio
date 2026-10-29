package com.panacea.mobile.data;

import android.content.Context;

import androidx.annotation.WorkerThread;

import com.panacea.mobile.database.DatabaseHelper;
import com.panacea.mobile.models.Booking;
import com.panacea.mobile.network.NetworkModule;
import com.panacea.mobile.network.dto.BookingResponse;
import com.panacea.mobile.utils.Constants;
import com.panacea.mobile.utils.CancellationRequestPolicy;
import com.panacea.mobile.utils.NetworkUtils;
import com.panacea.mobile.utils.SessionManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Background booking work is deliberately limited to cancellation requests the
 * customer has already approved. Creating or confirming a booking can carry
 * monetary consent and must remain a foreground checkout action.
 */
public class BookingSyncManager {
    public static final class BlockingSyncResult {
        public final int syncedCount;
        public final boolean shouldRetry;

        BlockingSyncResult(int syncedCount, boolean shouldRetry) {
            this.syncedCount = syncedCount;
            this.shouldRetry = shouldRetry;
        }
    }
    public interface SyncCallback {
        void onComplete(int syncedCount, int remainingCount);
    }

    private final Context context;
    private final DatabaseHelper db;
    private final SessionManager session;

    public BookingSyncManager(Context context) {
        this.context = context.getApplicationContext();
        this.db = DatabaseHelper.getInstance(this.context);
        this.session = new SessionManager(this.context);
    }

    public void syncPendingBookings(SyncCallback callback) {
        if (!NetworkUtils.isOnline(context) || !session.isAuthenticatedSession()) {
            notifyComplete(callback, 0);
            return;
        }
        String ownerId = session.getUserId();
        List<Booking> cancellations = db.getPendingCancellationBookingsForOwner(ownerId);
        if (cancellations.isEmpty()) {
            notifyComplete(callback, 0);
            return;
        }
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger synced = new AtomicInteger();
        int total = cancellations.size();
        for (Booking booking : cancellations) {
            NetworkModule.getApi(session).cancelBooking(
                    booking.id, CancellationRequestPolicy.idempotencyKey(booking.id)).enqueue(new Callback<BookingResponse>() {
                @Override
                public void onResponse(Call<BookingResponse> call, Response<BookingResponse> response) {
                    if (response.isSuccessful()) {
                        db.markBookingCancelled(booking.id, booking.clientRequestId, Constants.SYNC_SYNCED);
                        synced.incrementAndGet();
                    }
                    finishOne(callback, completed, synced, total);
                }

                @Override
                public void onFailure(Call<BookingResponse> call, Throwable throwable) {
                    finishOne(callback, completed, synced, total);
                }
            });
        }
    }

    /** Blocking variant for WorkManager; runs on Worker background thread and remains cancellation-only. */
    @WorkerThread
    public BlockingSyncResult syncPendingCancellationsBlocking() {
        if (!session.isAuthenticatedSession()) return new BlockingSyncResult(0, false);
        if (!NetworkUtils.isOnline(context)) return new BlockingSyncResult(0, true);
        List<Booking> cancellations = db.getPendingCancellationBookingsForOwner(session.getUserId());
        int synced = 0;
        boolean retry = false;
        for (Booking booking : cancellations) {
            try {
                Response<BookingResponse> response = NetworkModule.getApi(session)
                        .cancelBooking(booking.id, CancellationRequestPolicy.idempotencyKey(booking.id)).execute();
                if (response.isSuccessful()) {
                    db.markBookingCancelled(booking.id, booking.clientRequestId, Constants.SYNC_SYNCED);
                    synced++;
                } else if (response.code() >= 500 || response.code() == 429) {
                    retry = true;
                }
            } catch (IOException e) {
                retry = true;
            }
        }
        return new BlockingSyncResult(synced, retry);
    }

    private void finishOne(SyncCallback callback, AtomicInteger completed, AtomicInteger synced, int total) {
        if (completed.incrementAndGet() == total) notifyComplete(callback, synced.get());
    }

    private void notifyComplete(SyncCallback callback, int syncedCount) {
        if (callback == null) return;
        String ownerId = session.isAuthenticatedSession() ? session.getUserId() : null;
        int remaining = db.getPendingCancellationBookingsForOwner(ownerId).size();
        callback.onComplete(syncedCount, remaining);
    }
}
