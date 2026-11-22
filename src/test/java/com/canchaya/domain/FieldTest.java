package com.canchaya.domain;

import com.canchaya.exception.BusinessException;
import com.canchaya.policy.PercentageDepositPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests of the Information Expert and Creator responsibilities of Field. */
class FieldTest {

    private static final LocalDate SATURDAY = LocalDate.of(2030, 1, 5);

    private final PercentageDepositPolicy deposit30 = new PercentageDepositPolicy(new BigDecimal("30"));
    private Field field;
    private User player;

    @BeforeEach
    void setUp() {
        User owner = new User("Owner", "owner@test.co", "x", Role.OWNER);
        player = new User("Player", "player@test.co", "x", Role.PLAYER);
        field = new Field("El Ejido", "Pasto", null, null, new BigDecimal("80000"), owner, Zone.CENTER);
        field.setSchedule(DayOfWeek.SATURDAY, 8, 22);
    }

    @Test
    void calculatesPriceForHours() {
        assertThat(field.priceFor(2)).isEqualByComparingTo("160000");
    }

    @Test
    void bookCreatesReservationWithTotalAndDeposit() {
        Reservation r = field.book(player, SATURDAY, 19, 21, deposit30);

        assertThat(r.durationHours()).isEqualTo(2);
        assertThat(r.getTotalAmount()).isEqualByComparingTo("160000");
        assertThat(r.getDepositAmount()).isEqualByComparingTo("48000");
        assertThat(r.remainingAmount()).isEqualByComparingTo("112000");
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
    }

    @Test
    void rejectsBookingOutsideOpeningHours() {
        assertThatThrownBy(() -> field.book(player, SATURDAY, 21, 23, deposit30))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void rejectsBookingOnDayWithoutSchedule() {
        LocalDate sunday = SATURDAY.plusDays(1);
        assertThatThrownBy(() -> field.book(player, sunday, 10, 11, deposit30))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsInvalidSchedule() {
        assertThatThrownBy(() -> field.setSchedule(DayOfWeek.SUNDAY, 22, 8))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void settingScheduleTwiceForSameDayUpdatesIt() {
        field.setSchedule(DayOfWeek.SATURDAY, 10, 20);

        assertThat(field.getSchedules()).hasSize(1);
        assertThat(field.isOpen(DayOfWeek.SATURDAY, 8, 9)).isFalse();
        assertThat(field.isOpen(DayOfWeek.SATURDAY, 10, 12)).isTrue();
    }

    @Test
    void calculatesDistanceToAPoint() {
        User owner = new User("Owner", "o2@test.co", "x", Role.OWNER);
        // Canchas Sintéticas Monumental -> Plaza de Nariño (about 1.2 km)
        Field monumental = new Field("Monumental", "Pasto", 1.22063, -77.28655, BigDecimal.TEN, owner, Zone.NORTH);

        assertThat(monumental.distanceKmTo(1.22063, -77.28655)).isZero();
        assertThat(monumental.distanceKmTo(1.21361, -77.28111)).isBetween(0.9, 1.3);
        assertThat(field.distanceKmTo(1.2, -77.2)).isNull();   // no coordinates
    }

    @Test
    void onlyOwnersCanPublishFields() {
        assertThatThrownBy(() -> new Field("X", "Pasto", null, null, BigDecimal.TEN, player, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void reservationOverlapsAreDetected() {
        Reservation r = field.book(player, SATURDAY, 19, 21, deposit30);

        assertThat(r.overlaps(20, 22)).isTrue();
        assertThat(r.overlaps(18, 20)).isTrue();
        assertThat(r.overlaps(21, 22)).isFalse();
        assertThat(r.overlaps(17, 19)).isFalse();
    }

    @Test
    void approvedPaymentConfirmsReservation() {
        Reservation r = field.book(player, SATURDAY, 19, 21, deposit30);

        Payment p = r.registerPayment(PaymentMethodType.NEQUI, PaymentStatus.APPROVED, "NEQUI-1");

        assertThat(p.getAmount()).isEqualByComparingTo("48000");
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThatThrownBy(() -> r.registerPayment(PaymentMethodType.NEQUI, PaymentStatus.APPROVED, "again"))
                .isInstanceOf(BusinessException.class);
    }
}
