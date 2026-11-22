package com.canchaya.domain;

import jakarta.persistence.*;

import java.time.DayOfWeek;

/**
 * Opening hours of a field for one day of the week, e.g. SATURDAY 08:00-22:00.
 * Only {@link Field} creates instances (GRASP Creator), so the constructor is package-private.
 */
@Entity
@Table(name = "schedules",
        uniqueConstraints = @UniqueConstraint(columnNames = {"field_id", "day_of_week"}))
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "field_id")
    private Field field;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private int openingHour;

    @Column(nullable = false)
    private int closingHour;

    protected Schedule() {
        // required by JPA
    }

    Schedule(Field field, DayOfWeek dayOfWeek, int openingHour, int closingHour) {
        this.field = field;
        this.dayOfWeek = dayOfWeek;
        this.openingHour = openingHour;
        this.closingHour = closingHour;
    }

    /** GRASP Information Expert: the schedule knows its own hours, so it answers whether a range fits. */
    public boolean covers(DayOfWeek day, int startHour, int endHour) {
        return dayOfWeek == day && startHour >= openingHour && endHour <= closingHour;
    }

    void changeHours(int openingHour, int closingHour) {
        this.openingHour = openingHour;
        this.closingHour = closingHour;
    }

    public Long getId() { return id; }
    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public int getOpeningHour() { return openingHour; }
    public int getClosingHour() { return closingHour; }
}
