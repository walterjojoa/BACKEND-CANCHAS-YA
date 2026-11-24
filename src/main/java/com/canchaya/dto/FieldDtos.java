package com.canchaya.dto;

import com.canchaya.domain.Field;
import com.canchaya.domain.Schedule;
import com.canchaya.domain.Zone;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.List;

/**
 * DTOs de /api/fields. La API nunca expone directamente las entidades JPA: así se controla
 * qué datos salen y se evitan problemas de carga perezosa (LAZY) y ciclos en el JSON.
 */
public final class FieldDtos {

    private FieldDtos() {
    }

    /** Cuerpo para publicar una cancha. Las coordenadas y la zona son opcionales. */
    public record CreateFieldRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 200) String address,
            @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @NotNull @Positive BigDecimal pricePerHour,
            Zone zone) {
    }

    /** Cuerpo para definir el horario de un día (dayOfWeek en inglés: MONDAY ... SUNDAY). */
    public record ScheduleRequest(
            @NotNull DayOfWeek dayOfWeek,
            @Min(0) @Max(23) int openingHour,
            @Min(1) @Max(24) int closingHour) {
    }

    /** Horario de un día tal como sale en la respuesta. */
    public record ScheduleResponse(DayOfWeek dayOfWeek, int openingHour, int closingHour) {
        /** Convierte la entidad Schedule en DTO. */
        public static ScheduleResponse from(Schedule s) {
            return new ScheduleResponse(s.getDayOfWeek(), s.getOpeningHour(), s.getClosingHour());
        }
    }

    /**
     * Cancha tal como la ve el frontend.
     * referenceListing = true significa una cancha real cargada desde datos públicos cuyo dueño no
     * se ha unido a CanchaYa: el frontend debe mostrar su precio y horario como estimados.
     */
    public record FieldResponse(Long id, String name, String address, Double latitude, Double longitude,
                                BigDecimal pricePerHour, Zone zone, boolean referenceListing,
                                Long ownerId, String ownerName,
                                List<ScheduleResponse> schedules) {
        /** Convierte la entidad Field en DTO, con los horarios ordenados de lunes a domingo. */
        public static FieldResponse from(Field f) {
            return new FieldResponse(f.getId(), f.getName(), f.getAddress(), f.getLatitude(), f.getLongitude(),
                    f.getPricePerHour(), f.getZone(), f.isReferenceListing(),
                    f.getOwner().getId(), f.getOwner().getName(),
                    f.getSchedules().stream()
                            .sorted(Comparator.comparing(Schedule::getDayOfWeek)) // lunes → domingo
                            .map(ScheduleResponse::from).toList());
        }
    }

    /** Una franja de una hora en la disponibilidad: la hora y si está libre. */
    public record SlotResponse(int hour, boolean available) {
    }

    /** Una cancha más su distancia en línea recta (km) desde la posición del usuario. */
    public record NearbyFieldResponse(FieldResponse field, double distanceKm) {
    }
}
