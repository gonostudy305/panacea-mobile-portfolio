package com.panacea.mobile.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public final class DateTimeHelper {
    public static final String BOOKING_TIME_ZONE_ID = "Asia/Ho_Chi_Minh";
    private static final ZoneId BOOKING_ZONE = ZoneId.of(BOOKING_TIME_ZONE_ID);
    private static final DateTimeFormatter BOOKING_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private DateTimeHelper() {
    }

    public static String tomorrowDate() {
        return LocalDate.now(BOOKING_ZONE).plusDays(1).format(BOOKING_DATE_FORMAT);
    }

    /** Locale-invariant yyyy-MM-dd for DatePickerDialog (month is 0-based). */
    public static String formatBookingDatePickerDay(int year, int monthZeroBased, int dayOfMonth) {
        return String.format(Locale.ROOT, "%04d-%02d-%02d", year, monthZeroBased + 1, dayOfMonth);
    }

    /**
     * Converts yyyy-MM-dd to UTC midnight millis for {@code MaterialDatePicker} selection.
     * Returns today's UTC midnight when input is null/invalid.
     */
    public static long bookingDateToUtcMillis(String yyyyMmDd) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.set(Calendar.HOUR_OF_DAY, 0);
        utc.set(Calendar.MINUTE, 0);
        utc.set(Calendar.SECOND, 0);
        utc.set(Calendar.MILLISECOND, 0);
        if (yyyyMmDd != null && yyyyMmDd.trim().length() >= 10) {
            try {
                String[] p = yyyyMmDd.trim().split("-");
                utc.set(Calendar.YEAR, Integer.parseInt(p[0]));
                utc.set(Calendar.MONTH, Integer.parseInt(p[1]) - 1);
                utc.set(Calendar.DAY_OF_MONTH, Integer.parseInt(p[2]));
                return utc.getTimeInMillis();
            } catch (Exception ignored) {
                // fall through to today
            }
        }
        String[] todayParts = todayDate().split("-");
        utc.set(Calendar.YEAR, Integer.parseInt(todayParts[0]));
        utc.set(Calendar.MONTH, Integer.parseInt(todayParts[1]) - 1);
        utc.set(Calendar.DAY_OF_MONTH, Integer.parseInt(todayParts[2]));
        return utc.getTimeInMillis();
    }

    /**
     * Converts MaterialDatePicker UTC-midnight selection back to yyyy-MM-dd.
     */
    public static String utcMillisToBookingDate(long utcMillis) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(utcMillis);
        return formatBookingDatePickerDay(
                utc.get(Calendar.YEAR),
                utc.get(Calendar.MONTH),
                utc.get(Calendar.DAY_OF_MONTH));
    }

    /** Start of today in UTC midnight (for calendar min-date constraints). */
    public static long todayUtcMillis() {
        return bookingDateToUtcMillis(todayDate());
    }

    public static String defaultTime() {
        return "14:00";
    }

    /** Returns today's date in yyyy-MM-dd format. */
    public static String todayDate() {
        return LocalDate.now(BOOKING_ZONE).format(BOOKING_DATE_FORMAT);
    }

    /** Returns true if the given yyyy-MM-dd string is today. */
    public static boolean isToday(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return false;
        return todayDate().equals(dateStr.trim());
    }

    /**
     * Returns the current time as a float: 14:30 → 14.5, 9:00 → 9.0.
     * Used to grey out past slots when the selected date is today.
     */
    public static float currentHourFloat() {
        ZonedDateTime now = ZonedDateTime.now(BOOKING_ZONE);
        int hour = now.getHour();
        int minute = now.getMinute();
        return hour + (minute >= 30 ? 0.5f : 0f);
    }

    /**
     * Returns true when a slot on today's date is already in the past or violates the
     * minimum 30-minute lead-time rule enforced by the backend.
     */
    public static boolean isPastOrTooSoonForToday(float hourFloat) {
        ZonedDateTime now = ZonedDateTime.now(BOOKING_ZONE);
        if (hourFloat < 0f) return false;
        int slotMinutes = Math.round(hourFloat * 60f);
        int nowMinutes = now.getHour() * 60 + now.getMinute();
        return slotMinutes < (nowMinutes + 30);
    }

    /**
     * Same as {@link #isPastOrTooSoonForToday(float)} but accepts an injected clock for tests.
     * A slot is considered blocked when it starts before {@code now + 30 minutes}. A slot
     * exactly 30 minutes ahead remains bookable.
     */
    public static boolean isPastOrTooSoonForToday(float hourFloat, Calendar now) {
        if (hourFloat < 0f || now == null) return false;
        int slotMinutes = Math.round(hourFloat * 60f);
        int nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        return slotMinutes < (nowMinutes + 30);
    }

    /** No 30-minute slot remains once Vietnam time is later than 22:00 (last start 22:30). */
    public static boolean isTodayFullyLocked() {
        return isTodayFullyLocked(Instant.now());
    }

    /** Clock-injected overload for the 22:00/22:01 operating-hours boundary. */
    public static boolean isTodayFullyLocked(Instant now) {
        ZonedDateTime vietnamNow = now.atZone(BOOKING_ZONE);
        int nowMinutes = vietnamNow.getHour() * 60 + vietnamNow.getMinute();
        int lastSlotStartMinutes = 22 * 60 + 30;
        return nowMinutes + 30 > lastSlotStartMinutes;
    }

    /** Validates a local Panacea booking time against the Vietnam business clock. */
    public static boolean isAtLeastMinutesAhead(
            String date,
            String time,
            int minimumMinutes
    ) {
        return isAtLeastMinutesAhead(date, time, minimumMinutes, Instant.now());
    }

    /** Clock-injected overload for deterministic timezone-boundary tests. */
    public static boolean isAtLeastMinutesAhead(
            String date,
            String time,
            int minimumMinutes,
            Instant now
    ) {
        try {
            LocalDate localDate = LocalDate.parse(date, BOOKING_DATE_FORMAT);
            LocalTime localTime = LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT));
            Instant selected = LocalDateTime.of(localDate, localTime).atZone(BOOKING_ZONE).toInstant();
            return !selected.isBefore(now.plusSeconds(Math.max(0, minimumMinutes) * 60L));
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Converts a slot float (14.5) to a display string ("14:30"). */
    public static String hourFloatToTime(float hourFloat) {
        int hours = (int) hourFloat;
        int minutes = (hourFloat - hours) >= 0.5f ? 30 : 0;
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
    }

    /** Converts a time string ("14:30") to a slot float (14.5). Returns -1 on parse failure. */
    public static float timeToHourFloat(String time) {
        if (time == null || time.isEmpty()) return -1f;
        try {
            String[] parts = time.split(":");
            int h = Integer.parseInt(parts[0]);
            int m = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return h + (m >= 30 ? 0.5f : 0f);
        } catch (Exception e) {
            return -1f;
        }
    }
}
