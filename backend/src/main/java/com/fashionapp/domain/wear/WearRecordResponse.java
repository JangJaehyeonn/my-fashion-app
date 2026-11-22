package com.fashionapp.domain.wear;

import java.time.LocalDate;

/** FR-10 POST /api/wears result: the day recorded and how many new records were added (duplicates are not counted). */
public record WearRecordResponse(LocalDate date, int recorded) {
}
