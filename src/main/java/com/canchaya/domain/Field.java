package com.canchaya.domain;

import com.canchaya.exception.BusinessException;
import com.canchaya.policy.DepositPolicy;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** A synthetic football field ("cancha sintética") published by an owner. */
@Entity
@Table(name = "fields")
public class Field {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 200)
    private String address;

    private Double latitude;
    private Double longitude;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerHour;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @OneToMany(mappedBy = "field", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Schedule> schedules = new ArrayList<>();

    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Zone zone;

    /**
     * TRUE for real fields loaded from public data whose owners have not joined
     * the platform: their price and opening hours are estimates.
     */
    @Column(name = "reference_listing", nullable = false)
    private boolean referenceListing = false;

    protected Field() {
        // required by JPA
    }

    public Field(String name, String address, Double latitude, Double longitude,
                 BigDecimal pricePerHour, User owner, Zone zone) {
        if (pricePerHour == null || pricePerHour.signum() <= 0) {
            throw new BusinessException("Price per hour must be greater than zero");
        }
        if (!owner.hasRole(Role.OWNER)) {
            throw new BusinessException("Only users with role OWNER can publish fields");
        }
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.pricePerHour = pricePerHour;
        this.owner = owner;
        this.zone = zone;
    }

    /** Marks a real field loaded from public data (owner not on the platform). */
    public Field asReferenceListing() {
        this.referenceListing = true;
        return this;
    }

    // ------------------------------------------------------------------
    // GRASP Creator: a Field CONTAINS its schedules, so the Field creates
    // and validates them. Nobody else calls "new Schedule(...)".
    // ------------------------------------------------------------------
    public Schedule setSchedule(DayOfWeek day, int openingHour, int closingHour) {
        if (openingHour < 0 || closingHour > 24 || closingHour <= openingHour) {
            throw new BusinessException("Invalid schedule for " + day + ": " + openingHour + "-" + closingHour);
        }
        Optional<Schedule> existing = scheduleFor(day);
        if (existing.isPresent()) {
            existing.get().changeHours(openingHour, closingHour);
            return existing.get();
        }
        Schedule schedule = new Schedule(this, day, openingHour, closingHour);
        schedules.add(schedule);
        return schedule;
    }

    // ------------------------------------------------------------------
    // GRASP Creator: the Field has the data needed to initialise a
    // Reservation (price and opening hours), so the Field creates it.
    // ------------------------------------------------------------------
    public Reservation book(User player, LocalDate date, int startHour, int endHour, DepositPolicy depositPolicy) {
        if (!active) {
            throw new BusinessException("Field " + name + " is not accepting reservations");
        }
        if (endHour <= startHour) {
            throw new BusinessException("End hour must be after start hour");
        }
        if (!isOpen(date.getDayOfWeek(), startHour, endHour)) {
            throw new BusinessException("Field " + name + " is closed at that time");
        }
        BigDecimal total = priceFor(endHour - startHour);
        return new Reservation(this, player, date, startHour, endHour, total, depositPolicy.depositFor(total));
    }

    // ------------------------------------------------------------------
    // GRASP Information Expert: the Field owns the price and the schedules,
    // so it calculates prices and answers "are you open at this time?".
    // ------------------------------------------------------------------
    public BigDecimal priceFor(int hours) {
        return pricePerHour.multiply(BigDecimal.valueOf(hours));
    }

    public boolean isOpen(DayOfWeek day, int startHour, int endHour) {
        return schedules.stream().anyMatch(s -> s.covers(day, startHour, endHour));
    }

    public Optional<Schedule> scheduleFor(DayOfWeek day) {
        return schedules.stream().filter(s -> s.getDayOfWeek() == day).findFirst();
    }

    public boolean isOwnedBy(Long userId) {
        return owner.getId().equals(userId);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public BigDecimal getPricePerHour() { return pricePerHour; }
    public User getOwner() { return owner; }
    public boolean isActive() { return active; }
    public Zone getZone() { return zone; }
    public boolean isReferenceListing() { return referenceListing; }
    public List<Schedule> getSchedules() { return Collections.unmodifiableList(schedules); }
}
