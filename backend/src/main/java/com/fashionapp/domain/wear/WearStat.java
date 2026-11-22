package com.fashionapp.domain.wear;

import java.time.LocalDate;
import java.util.UUID;

/** FR-10: how many days a piece was worn and the last of those days. */
public record WearStat(UUID clothesId, long count, LocalDate lastWornOn) {
}
