package com.canchaya.domain;

public enum ReservationStatus {
    /** Created, waiting for the deposit to be paid. */
    PENDING_PAYMENT,
    /** Deposit paid: the slot is secured. */
    CONFIRMED,
    CANCELLED
}
