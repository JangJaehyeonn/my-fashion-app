package com.fashionapp.domain.wear;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/** FR-10 date rules: "today" is the Korean calendar day of the injected clock, weeks start on Monday. */
public final class WearCalendar {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private WearCalendar() {
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.ofInstant(clock.instant(), ZONE);
    }

    public static LocalDate weekStart(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static LocalDate monthStart(LocalDate day) {
        return day.withDayOfMonth(1);
    }
}
