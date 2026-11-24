package com.canchaya.domain;

import jakarta.persistence.*;

import java.time.DayOfWeek;

/**
 * Horario de apertura de una cancha para un día de la semana, p. ej. SÁBADO 08:00-22:00.
 * Solo {@link Field} crea instancias (GRASP Creador), por eso el constructor es de paquete.
 */
@Entity
// Restricción única: una cancha no puede tener dos horarios para el mismo día.
@Table(name = "schedules",
        uniqueConstraints = @UniqueConstraint(columnNames = {"field_id", "day_of_week"}))
public class Schedule {

    /** Llave primaria generada por la base de datos. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Cancha a la que pertenece este horario. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "field_id")
    private Field field;

    /** Día de la semana (MONDAY ... SUNDAY), guardado como texto. */
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    /** Hora en que abre (0-23). */
    @Column(nullable = false)
    private int openingHour;

    /** Hora en que cierra (1-24). */
    @Column(nullable = false)
    private int closingHour;

    /** Constructor vacío obligatorio para JPA. */
    protected Schedule() {
        // requerido por JPA
    }

    /** Visibilidad de paquete: solo Field lo llama. */
    Schedule(Field field, DayOfWeek dayOfWeek, int openingHour, int closingHour) {
        this.field = field;
        this.dayOfWeek = dayOfWeek;
        this.openingHour = openingHour;
        this.closingHour = closingHour;
    }

    /**
     * GRASP Experto en Información: el horario conoce sus propias horas, así que él
     * responde si un rango [startHour, endHour) cabe dentro de ese día.
     */
    public boolean covers(DayOfWeek day, int startHour, int endHour) {
        return dayOfWeek == day && startHour >= openingHour && endHour <= closingHour;
    }

    /** Cambia las horas de apertura/cierre (lo usa Field.setSchedule). */
    void changeHours(int openingHour, int closingHour) {
        this.openingHour = openingHour;
        this.closingHour = closingHour;
    }

    public Long getId() { return id; }
    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public int getOpeningHour() { return openingHour; }
    public int getClosingHour() { return closingHour; }
}
