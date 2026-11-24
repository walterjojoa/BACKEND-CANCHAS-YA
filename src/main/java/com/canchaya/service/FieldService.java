package com.canchaya.service;

import com.canchaya.domain.Field;
import com.canchaya.domain.Reservation;
import com.canchaya.domain.Schedule;
import com.canchaya.domain.User;
import com.canchaya.domain.Zone;
import com.canchaya.dto.FieldDtos.CreateFieldRequest;
import com.canchaya.dto.FieldDtos.FieldResponse;
import com.canchaya.dto.FieldDtos.NearbyFieldResponse;
import com.canchaya.exception.BusinessException;
import com.canchaya.dto.FieldDtos.ScheduleRequest;
import com.canchaya.dto.FieldDtos.SlotResponse;
import com.canchaya.exception.ForbiddenException;
import com.canchaya.exception.NotFoundException;
import com.canchaya.repository.FieldRepository;
import com.canchaya.repository.ReservationRepository;
import com.canchaya.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Casos de uso de las canchas: publicarlas, configurarlas y consultarlas.
 * GRASP Alta Cohesión: todo lo de publicar y buscar canchas, y nada más.
 */
@Service
public class FieldService {

    private final FieldRepository fields;
    private final UserRepository users;
    private final ReservationRepository reservations;

    public FieldService(FieldRepository fields, UserRepository users, ReservationRepository reservations) {
        this.fields = fields;
        this.users = users;
        this.reservations = reservations;
    }

    /** Catálogo de canchas activas ordenado por nombre; si llega una zona, filtra por ella. */
    @Transactional(readOnly = true)
    public List<FieldResponse> listActive(Zone zone) {
        List<Field> found = zone == null
                ? fields.findByActiveTrueOrderByNameAsc()
                : fields.findByActiveTrueAndZoneOrderByNameAsc(zone);
        // Se convierten las entidades a DTO para no exponer la entidad JPA en la API.
        return found.stream().map(FieldResponse::from).toList();
    }

    /** Canchas activas más cercanas a un punto GPS, de la más cercana a la más lejana. */
    @Transactional(readOnly = true)
    public List<NearbyFieldResponse> nearby(double lat, double lng, int limit) {
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new BusinessException("Invalid coordinates");
        }
        // El límite se acota entre 1 y 50 para no devolver listas enormes.
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return fields.findByActiveTrueOrderByNameAsc().stream()
                .filter(f -> f.distanceKmTo(lat, lng) != null)              // descarta canchas sin coordenadas
                .map(f -> new NearbyFieldResponse(FieldResponse.from(f),
                        Math.round(f.distanceKmTo(lat, lng) * 100) / 100.0))   // Experto: la cancha calcula su distancia (2 decimales)
                .sorted(Comparator.comparingDouble(NearbyFieldResponse::distanceKm))
                .limit(safeLimit)
                .toList();
    }

    /** Canchas de un dueño (panel del dueño). */
    @Transactional(readOnly = true)
    public List<FieldResponse> listByOwner(Long ownerId) {
        return fields.findByOwnerIdOrderByNameAsc(ownerId).stream().map(FieldResponse::from).toList();
    }

    /** Detalle de una cancha (404 si no existe). */
    @Transactional(readOnly = true)
    public FieldResponse get(Long fieldId) {
        return FieldResponse.from(load(fieldId));
    }

    /** Publica una cancha nueva. Las reglas (precio > 0, rol OWNER) las valida el constructor de Field. */
    @Transactional
    public FieldResponse create(Long ownerId, CreateFieldRequest request) {
        User owner = users.findById(ownerId).orElseThrow(() -> new NotFoundException("User " + ownerId + " not found"));
        Field field = new Field(request.name().trim(), request.address().trim(), request.latitude(),
                request.longitude(), request.pricePerHour(), owner, request.zone());
        return FieldResponse.from(fields.save(field));
    }

    /** Define el horario de un día. Solo el dueño de esa cancha puede hacerlo (403 si no). */
    @Transactional
    public FieldResponse setSchedule(Long ownerId, Long fieldId, ScheduleRequest request) {
        Field field = load(fieldId);
        if (!field.isOwnedBy(ownerId)) {
            throw new ForbiddenException("You are not the owner of this field");
        }
        // Creador: la cancha construye su propio Schedule.
        field.setSchedule(request.dayOfWeek(), request.openingHour(), request.closingHour());
        // saveAndFlush escribe ya en la BD para que la respuesta incluya el id del horario.
        return FieldResponse.from(fields.saveAndFlush(field));
    }

    /**
     * Disponibilidad de una fecha: una franja por cada hora en que la cancha abre ese día,
     * indicando si todavía se puede reservar. Lista vacía si ese día no abre.
     */
    @Transactional(readOnly = true)
    public List<SlotResponse> availability(Long fieldId, LocalDate date) {
        Field field = load(fieldId);
        Optional<Schedule> schedule = field.scheduleFor(date.getDayOfWeek());
        if (schedule.isEmpty()) {
            return List.of();
        }
        List<Reservation> booked = reservations.findByFieldIdAndDate(fieldId, date);
        List<SlotResponse> slots = new ArrayList<>();
        // Recorre hora por hora desde la apertura hasta el cierre.
        for (int hour = schedule.get().getOpeningHour(); hour < schedule.get().getClosingHour(); hour++) {
            final int h = hour; // variable final para usarla dentro de la lambda
            // Experto: cada Reserva responde si ocupa esa hora.
            boolean taken = booked.stream().anyMatch(r -> r.occupies(h));
            slots.add(new SlotResponse(hour, !taken));
        }
        return slots;
    }

    /** Busca la cancha o lanza 404. */
    private Field load(Long fieldId) {
        return fields.findById(fieldId).orElseThrow(() -> new NotFoundException("Field " + fieldId + " not found"));
    }
}
