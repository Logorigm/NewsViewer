package com.newshub.app.domain.text;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Human-readable timestamps in the user's time zone. All times are stored as UTC epoch millis
 * and converted only for display.
 */
public final class TimeFormat {

    private TimeFormat() {
    }

    private static final String[] MONTHS_SHORT = {
            "янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек"};
    private static final String[] MONTHS_GENITIVE = {
            "января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября",
            "октября", "ноября", "декабря"};

    /** "только что", "5 мин назад", "3 ч назад", "вчера, 14:05", "16 окт, 09:30", "16 окт 2025". */
    public static String relative(long timeMillis, long nowMillis, ZoneId zone) {
        long diff = nowMillis - timeMillis;
        if (diff < 60_000L) return "только что";
        if (diff < 3_600_000L) return (diff / 60_000L) + " мин назад";
        ZonedDateTime t = Instant.ofEpochMilli(timeMillis).atZone(zone);
        ZonedDateTime now = Instant.ofEpochMilli(nowMillis).atZone(zone);
        LocalDate day = t.toLocalDate();
        LocalDate today = now.toLocalDate();
        if (diff < 6 * 3_600_000L && day.equals(today)) return (diff / 3_600_000L) + " ч назад";
        String hm = hhmm(t);
        if (day.equals(today)) return "сегодня, " + hm;
        if (day.equals(today.minusDays(1))) return "вчера, " + hm;
        String date = t.getDayOfMonth() + " " + MONTHS_SHORT[t.getMonthValue() - 1];
        if (t.getYear() != now.getYear()) return date + " " + t.getYear();
        return date + ", " + hm;
    }

    /** "16 октября 2026, 14:05" */
    public static String full(long timeMillis, ZoneId zone) {
        ZonedDateTime t = Instant.ofEpochMilli(timeMillis).atZone(zone);
        return t.getDayOfMonth() + " " + MONTHS_GENITIVE[t.getMonthValue() - 1] + " " + t.getYear()
                + ", " + hhmm(t);
    }

    /** "14:05" for today, otherwise "16 окт, 14:05". */
    public static String compact(long timeMillis, long nowMillis, ZoneId zone) {
        ZonedDateTime t = Instant.ofEpochMilli(timeMillis).atZone(zone);
        ZonedDateTime now = Instant.ofEpochMilli(nowMillis).atZone(zone);
        if (t.toLocalDate().equals(now.toLocalDate())) return hhmm(t);
        return t.getDayOfMonth() + " " + MONTHS_SHORT[t.getMonthValue() - 1] + ", " + hhmm(t);
    }

    private static String hhmm(ZonedDateTime t) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d", t.getHour(), t.getMinute());
    }
}
