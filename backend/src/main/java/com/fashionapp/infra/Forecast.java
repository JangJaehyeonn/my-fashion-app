package com.fashionapp.infra;

import java.util.List;

/** Forecast slots of one (rounded) coordinate, sorted by time. Wrapped in a record so it caches as one JSON value. */
public record Forecast(List<ForecastSlot> slots) {

    public Forecast {
        slots = slots == null ? List.of() : List.copyOf(slots);
    }
}
