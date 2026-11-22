package com.canchaya.dto;

import com.canchaya.domain.Field;
import com.canchaya.domain.Schedule;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.List;

/** Request/response bodies for /api/fields. The API never exposes JPA entities directly. */
public final class FieldDtos {

    private FieldDtos() {
    }

    public record CreateFieldRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 200) String address,
            @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @NotNull @Positive BigDecimal pricePerHour) {
    }

    public record ScheduleRequest(
            @NotNull DayOfWeek dayOfWeek,
            @Min(0) @Max(23) int openingHour,
            @Min(1) @Max(24) int closingHour) {
    }

    public record ScheduleResponse(DayOfWeek dayOfWeek, int openingHour, int closingHour) {
        public static ScheduleResponse from(Schedule s) {
            return new ScheduleResponse(s.getDayOfWeek(), s.getOpeningHour(), s.getClosingHour());
        }
    }

    public record FieldResponse(Long id, String name, String address, Double latitude, Double longitude,
                                BigDecimal pricePerHour, Long ownerId, String ownerName,
                                List<ScheduleResponse> schedules) {
        public static FieldResponse from(Field f) {
            return new FieldResponse(f.getId(), f.getName(), f.getAddress(), f.getLatitude(), f.getLongitude(),
                    f.getPricePerHour(), f.getOwner().getId(), f.getOwner().getName(),
                    f.getSchedules().stream()
                            .sorted(Comparator.comparing(Schedule::getDayOfWeek)) // Monday -> Sunday
                            .map(ScheduleResponse::from).toList());
        }
    }

    public record SlotResponse(int hour, boolean available) {
    }
}
