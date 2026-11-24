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

/**
 * Cancha sintética publicada por un dueño (OWNER).
 *
 * <p>Es una entidad JPA: cada objeto Field es una fila de la tabla {@code fields}.
 * Además de guardar datos, tiene comportamiento propio (modelo de dominio rico):
 * calcula precios, sabe si está abierta, crea sus horarios y sus reservas.
 */
@Entity
@Table(name = "fields")
public class Field {

    /** Llave primaria; la genera la base de datos (columna IDENTITY / autoincremental). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nombre comercial de la cancha, p. ej. "La Pasión del Fútbol". */
    @Column(nullable = false, length = 120)
    private String name;

    /** Dirección en Pasto. */
    @Column(nullable = false, length = 200)
    private String address;

    /** Coordenadas GPS para el mapa y para calcular distancias (pueden ser null). */
    private Double latitude;
    private Double longitude;

    /** Precio por hora en pesos colombianos. BigDecimal evita errores de redondeo con dinero. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerHour;

    /** Dueño de la cancha. LAZY: el usuario solo se carga de la BD cuando se necesita. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    /**
     * Horarios de apertura, uno por día de la semana.
     * cascade ALL + orphanRemoval: al guardar/borrar la cancha se guardan/borran sus horarios.
     */
    @OneToMany(mappedBy = "field", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Schedule> schedules = new ArrayList<>();

    /** Si es false, la cancha no acepta reservas nuevas. */
    @Column(nullable = false)
    private boolean active = true;

    /** Zona de Pasto (NORTE, CENTRO, ...) para filtrar el catálogo. Se guarda como texto. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Zone zone;

    /**
     * TRUE para canchas reales cargadas desde datos públicos (Google Maps) cuyos dueños
     * todavía no están en la plataforma: su precio y horario son estimados.
     */
    @Column(name = "reference_listing", nullable = false)
    private boolean referenceListing = false;

    /** Constructor vacío obligatorio para JPA/Hibernate. protected para que nadie más lo use. */
    protected Field() {
        // requerido por JPA
    }

    /**
     * Crea una cancha validando las reglas de negocio:
     * el precio debe ser mayor a cero y solo un usuario OWNER puede publicarla.
     */
    public Field(String name, String address, Double latitude, Double longitude,
                 BigDecimal pricePerHour, User owner, Zone zone) {
        // Regla: no se permiten canchas gratis ni con precio negativo.
        if (pricePerHour == null || pricePerHour.signum() <= 0) {
            throw new BusinessException("Price per hour must be greater than zero");
        }
        // Regla: solo los dueños (OWNER) pueden publicar canchas.
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

    /** Marca la cancha como "de referencia" (real, pero su dueño no usa la plataforma). Devuelve this para encadenar. */
    public Field asReferenceListing() {
        this.referenceListing = true;
        return this;
    }

    // ------------------------------------------------------------------
    // GRASP Creador: una cancha CONTIENE sus horarios, así que es la cancha
    // quien los crea y valida. Nadie más hace "new Schedule(...)".
    // ------------------------------------------------------------------

    /**
     * Define (o actualiza) el horario de un día. Si ese día ya tenía horario,
     * solo cambia las horas; si no, crea uno nuevo.
     */
    public Schedule setSchedule(DayOfWeek day, int openingHour, int closingHour) {
        // Validación: horas entre 0 y 24 y el cierre después de la apertura.
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
    // GRASP Creador: la cancha tiene los datos necesarios para crear una
    // Reserva (precio y horarios), así que es ella quien la crea.
    // ------------------------------------------------------------------

    /**
     * Reserva la cancha para un jugador en una fecha y rango de horas.
     *
     * @param depositPolicy política que calcula el anticipo (p. ej. 30 %); se recibe
     *                      por parámetro para no amarrar la cancha a un porcentaje fijo.
     * @return la reserva nueva, en estado PENDING_PAYMENT (aún sin guardar en la BD).
     */
    public Reservation book(User player, LocalDate date, int startHour, int endHour, DepositPolicy depositPolicy) {
        if (!active) {
            throw new BusinessException("Field " + name + " is not accepting reservations");
        }
        if (endHour <= startHour) {
            throw new BusinessException("End hour must be after start hour");
        }
        // La cancha debe estar abierta ese día en todo el rango pedido.
        if (!isOpen(date.getDayOfWeek(), startHour, endHour)) {
            throw new BusinessException("Field " + name + " is closed at that time");
        }
        BigDecimal total = priceFor(endHour - startHour);
        return new Reservation(this, player, date, startHour, endHour, total, depositPolicy.depositFor(total));
    }

    // ------------------------------------------------------------------
    // GRASP Experto en Información: la cancha tiene el precio y los horarios,
    // así que ella calcula precios y responde "¿estás abierta a esta hora?".
    // ------------------------------------------------------------------

    /** Precio total de jugar N horas = precio por hora × horas. */
    public BigDecimal priceFor(int hours) {
        return pricePerHour.multiply(BigDecimal.valueOf(hours));
    }

    /** true si algún horario de la cancha cubre ese día y ese rango de horas. */
    public boolean isOpen(DayOfWeek day, int startHour, int endHour) {
        return schedules.stream().anyMatch(s -> s.covers(day, startHour, endHour));
    }

    /**
     * GRASP Experto en Información: la cancha conoce sus propias coordenadas, así que ella
     * calcula qué tan lejos está de un punto (fórmula de haversine, km en línea recta).
     * Devuelve null si la cancha no tiene coordenadas.
     */
    public Double distanceKmTo(double lat, double lng) {
        if (latitude == null || longitude == null) {
            return null;
        }
        final double earthRadiusKm = 6371.0; // radio medio de la Tierra
        double dLat = Math.toRadians(latitude - lat);
        double dLng = Math.toRadians(longitude - lng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(latitude))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Busca el horario de un día concreto (vacío si ese día no abre). */
    public Optional<Schedule> scheduleFor(DayOfWeek day) {
        return schedules.stream().filter(s -> s.getDayOfWeek() == day).findFirst();
    }

    /** true si el usuario con ese id es el dueño de la cancha (se usa para permisos). */
    public boolean isOwnedBy(Long userId) {
        return owner.getId().equals(userId);
    }

    // Getters de solo lectura. No hay setters: los cambios pasan por los métodos de negocio.
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
    /** Lista no modificable: desde afuera no se pueden agregar horarios sin pasar por setSchedule. */
    public List<Schedule> getSchedules() { return Collections.unmodifiableList(schedules); }
}
