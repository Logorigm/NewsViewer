package com.newshub.app.domain;

import static org.junit.Assert.assertEquals;

import com.newshub.app.domain.parse.DateParser;

import org.junit.Test;

import java.time.Instant;

public class DateParserTest {

    private static long utc(String iso) {
        return Instant.parse(iso).toEpochMilli();
    }

    @Test
    public void rfc822Variants() {
        assertEquals(utc("2026-10-16T07:15:00Z"), DateParser.parse("Fri, 16 Oct 2026 10:15:00 +0300"));
        assertEquals(utc("2026-10-16T10:15:00Z"), DateParser.parse("Fri, 16 Oct 2026 10:15:00 GMT"));
        assertEquals(utc("2026-10-16T07:15:00Z"), DateParser.parse("16 Oct 2026 10:15 MSK"));
        assertEquals(utc("2026-10-16T10:15:00Z"), DateParser.parse("Fri, 16 Oct 26 10:15:00 +0000"));
        assertEquals(utc("2026-10-16T07:15:00Z"), DateParser.parse("Fri, 16 Oct 2026 10:15:00 +03:00"));
        assertEquals(utc("2026-10-16T10:15:00Z"), DateParser.parse("fri, 16 OCT 2026 10:15:00 UT"));
    }

    @Test
    public void wrongWeekdayIsIgnored() {
        // 16 Oct 2026 is a Friday; feeds sometimes get the weekday wrong
        assertEquals(utc("2026-10-16T10:15:00Z"), DateParser.parse("Mon, 16 Oct 2026 10:15:00 +0000"));
    }

    @Test
    public void russianMonthNames() {
        assertEquals(utc("2026-10-16T07:15:00Z"), DateParser.parse("16 окт 2026 10:15:00 +0300"));
        assertEquals(utc("2026-05-01T09:00:00Z"), DateParser.parse("Пт, 01 мая 2026 12:00:00 +0300"));
    }

    @Test
    public void isoVariants() {
        assertEquals(utc("2026-10-16T07:30:00Z"), DateParser.parse("2026-10-16T10:30:00+03:00"));
        assertEquals(utc("2026-10-16T07:30:00Z"), DateParser.parse("2026-10-16T10:30:00+0300"));
        assertEquals(utc("2026-10-16T10:30:00Z"), DateParser.parse("2026-10-16T10:30:00Z"));
        assertEquals(utc("2026-10-16T10:30:00.500Z"), DateParser.parse("2026-10-16T10:30:00.500Z"));
        assertEquals(utc("2026-10-16T10:30:00Z"), DateParser.parse("2026-10-16 10:30:00"));
        assertEquals(utc("2026-10-16T00:00:00Z"), DateParser.parse("2026-10-16"));
    }

    @Test
    public void garbageReturnsZero() {
        assertEquals(0, DateParser.parse(null));
        assertEquals(0, DateParser.parse(""));
        assertEquals(0, DateParser.parse("вчера вечером"));
        assertEquals(0, DateParser.parse("32 Foo 2026 99:99"));
    }

    @Test
    public void sanitize() {
        long now = utc("2026-10-16T12:00:00Z");
        assertEquals(now, DateParser.sanitize(now + 3_600_000L, now));
        assertEquals(0, DateParser.sanitize(utc("1990-01-01T00:00:00Z"), now));
        long ok = utc("2026-10-15T12:00:00Z");
        assertEquals(ok, DateParser.sanitize(ok, now));
    }
}
