package com.fashionapp.domain.event;

/** FR-14 core user actions stored in {@code events.type} (plain string column, no DB check constraint). */
public enum EventType {
    SIGNUP,
    CLOTHES_ADDED,
    OUTFIT_RECOMMENDED,
    WEAR_LOGGED,
    PURCHASE_CHECKED,
    PURCHASE_DECIDED,
    VTON_USED,
    APP_OPENED
}
