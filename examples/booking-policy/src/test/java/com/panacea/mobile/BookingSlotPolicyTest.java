package com.panacea.mobile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.panacea.mobile.utils.DateTimeHelper;

import org.junit.Test;

import java.time.Instant;
import java.util.Calendar;

/**
 * Booking slot policy checks for time-lock helpers shared by the mobile flow.
 */
public class BookingSlotPolicyTest {

    @Test
    public void isTodayFullyLocked_matchesLastBackendSlotAndLeadTime() {
        assertFalse(DateTimeHelper.isTodayFullyLocked(
                Instant.parse("2026-07-19T15:00:00Z"))); // 22:00 Vietnam, 22:30 is valid
        assertTrue(DateTimeHelper.isTodayFullyLocked(
                Instant.parse("2026-07-19T15:01:00Z"))); // 22:01 Vietnam, no 30-min lead remains
    }

    @Test
    public void todaySlot_exactlyThirtyMinutesAhead_remainsBookable() {
        Calendar now = Calendar.getInstance();
        now.set(Calendar.HOUR_OF_DAY, 10);
        now.set(Calendar.MINUTE, 0);
        now.set(Calendar.SECOND, 0);
        now.set(Calendar.MILLISECOND, 0);

        assertTrue(DateTimeHelper.isPastOrTooSoonForToday(10.0f, now));
        assertFalse(DateTimeHelper.isPastOrTooSoonForToday(10.5f, now));
    }

    @Test
    public void todaySlot_underThirtyMinutesAhead_isBlocked() {
        Calendar now = Calendar.getInstance();
        now.set(Calendar.HOUR_OF_DAY, 10);
        now.set(Calendar.MINUTE, 10);
        now.set(Calendar.SECOND, 0);
        now.set(Calendar.MILLISECOND, 0);

        assertTrue(DateTimeHelper.isPastOrTooSoonForToday(10.5f, now));
        assertFalse(DateTimeHelper.isPastOrTooSoonForToday(11.0f, now));
    }

    @Test
    public void advanceRule_usesVietnamTimezoneAtUtcDayBoundary() {
        Instant now = Instant.parse("2026-07-18T17:40:00Z"); // 00:40 on 19/07 in Vietnam

        assertTrue(DateTimeHelper.isAtLeastMinutesAhead(
                "2026-07-19", "01:10", 30, now));
        assertFalse(DateTimeHelper.isAtLeastMinutesAhead(
                "2026-07-19", "01:00", 30, now));
        assertFalse(DateTimeHelper.isAtLeastMinutesAhead(
                "2026-07-18", "23:30", 30, now));
    }
}
