package com.newshub.app.domain.parse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;

/**
 * Lenient parser for the date formats seen in real feeds: RFC 822/1123 (RSS), ISO-8601 (Atom,
 * dc:date, Telegram), with named zones such as MSK, two-digit years, missing seconds, wrong
 * weekday names and Russian month abbreviations.
 */
public final class DateParser {

    private DateParser() {
    }

    private static final DateTimeFormatter[] RFC_LIKE = {
            formatter("d MMM yyyy HH:mm[:ss] Z"),
            formatter("d MMM yyyy HH:mm[:ss] XXX"),
            formatter("d MMMM yyyy HH:mm[:ss] Z"),
            formatter("d MMM yy HH:mm[:ss] Z"),
            formatter("d MMM yyyy HH:mm[:ss]"),
            formatter("yyyy-MM-dd HH:mm[:ss] Z"),
            formatter("yyyy-MM-dd HH:mm[:ss]"),
            formatter("dd.MM.yyyy HH:mm[:ss]"),
    };

    private static final String[][] ZONES = {
            {"GMT", "+0000"}, {"UTC", "+0000"}, {"UT", "+0000"}, {"Z", "+0000"},
            {"MSK", "+0300"}, {"MSD", "+0400"}, {"EST", "-0500"}, {"EDT", "-0400"},
            {"CST", "-0600"}, {"CDT", "-0500"}, {"MST", "-0700"}, {"MDT", "-0600"},
            {"PST", "-0800"}, {"PDT", "-0700"}, {"CET", "+0100"}, {"CEST", "+0200"},
            {"EET", "+0200"}, {"EEST", "+0300"}, {"BST", "+0100"},
    };

    private static final String[][] RU_MONTHS = {
            {"янв", "Jan"}, {"фев", "Feb"}, {"мар", "Mar"}, {"апр", "Apr"}, {"мая", "May"},
            {"май", "May"}, {"июн", "Jun"}, {"июл", "Jul"}, {"авг", "Aug"}, {"сен", "Sep"},
            {"окт", "Oct"}, {"ноя", "Nov"}, {"дек", "Dec"},
    };

    private static DateTimeFormatter formatter(String pattern) {
        return new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern)
                .toFormatter(Locale.ENGLISH);
    }

    /** @return epoch millis (UTC) or 0 if the value cannot be parsed. */
    public static long parse(String raw) {
        if (raw == null) return 0;
        String s = raw.trim().replace(' ', ' ');
        if (s.isEmpty()) return 0;

        long iso = parseIso(s);
        if (iso != 0) return iso;

        String r = s.replaceFirst("^\\p{L}+\\.?,\\s*", "");   // drop weekday: "Tue, " / "Вт, "
        r = r.replaceAll("\\s+", " ");
        r = translateRussianMonths(r);
        r = replaceNamedZone(r);
        for (DateTimeFormatter f : RFC_LIKE) {
            try {
                return ZonedDateTime.parse(r, f).toInstant().toEpochMilli();
            } catch (Exception ignored) {
                // try next
            }
            try {
                return LocalDateTime.parse(r, f).toInstant(ZoneOffset.UTC).toEpochMilli();
            } catch (Exception ignored) {
                // try next
            }
        }
        return 0;
    }

    private static long parseIso(String s) {
        if (s.length() < 10 || !Character.isDigit(s.charAt(0)) || s.charAt(4) != '-') return 0;
        String t = s.replace(' ', 'T');
        t = t.replaceFirst("([+-]\\d{2})(\\d{2})$", "$1:$2");
        try {
            return OffsetDateTime.parse(t).toInstant().toEpochMilli();
        } catch (Exception ignored) {
            // fall through
        }
        try {
            return Instant.parse(t).toEpochMilli();
        } catch (Exception ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(t).toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (Exception ignored) {
            // fall through
        }
        if (t.length() == 10) {
            try {
                return LocalDate.parse(t).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
            } catch (Exception ignored) {
                // fall through
            }
        }
        return 0;
    }

    private static String replaceNamedZone(String s) {
        int space = s.lastIndexOf(' ');
        if (space < 0) return s;
        String tail = s.substring(space + 1);
        for (String[] z : ZONES) {
            if (z[0].equalsIgnoreCase(tail)) return s.substring(0, space + 1) + z[1];
        }
        if (tail.matches("(?i)(GMT|UTC)[+-]\\d{1,2}(:?\\d{2})?")) {
            String off = tail.substring(3);
            String sign = off.substring(0, 1);
            String digits = off.substring(1).replace(":", "");
            if (digits.length() <= 2) digits = String.format(Locale.ROOT, "%02d00", Integer.parseInt(digits));
            return s.substring(0, space + 1) + sign + digits;
        }
        return s;
    }

    private static String translateRussianMonths(String s) {
        StringBuilder out = new StringBuilder();
        for (String part : s.split(" ")) {
            String lower = part.toLowerCase(Locale.ROOT);
            String replaced = part;
            for (String[] m : RU_MONTHS) {
                if (lower.startsWith(m[0])) {
                    replaced = m[1];
                    break;
                }
            }
            if (out.length() > 0) out.append(' ');
            out.append(replaced);
        }
        return out.toString();
    }

    /**
     * Sanity rules for publication dates: dates in the future (wrong server clock or time zone)
     * are clamped to {@code now}; implausibly old dates are treated as missing.
     */
    public static long sanitize(long parsed, long now) {
        if (parsed <= 0) return 0;
        if (parsed < 788918400000L) return 0; // before 1995-01-01
        if (parsed > now + 10 * 60_000L) return now;
        return parsed;
    }
}
