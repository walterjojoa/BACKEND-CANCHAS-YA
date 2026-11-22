package com.canchaya.service;

import com.canchaya.domain.Field;
import com.canchaya.domain.Reservation;
import com.canchaya.domain.Schedule;
import com.canchaya.domain.User;
import com.canchaya.domain.Zone;
import com.canchaya.dto.FieldDtos.CreateFieldRequest;
import com.canchaya.dto.FieldDtos.FieldResponse;
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
import java.util.List;
import java.util.Optional;

/** GRASP High Cohesion: everything about publishing and browsing fields, nothing else. */
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

    @Transactional(readOnly = true)
    public List<FieldResponse> listActive(Zone zone) {
        List<Field> found = zone == null
                ? fields.findByActiveTrueOrderByNameAsc()
                : fields.findByActiveTrueAndZoneOrderByNameAsc(zone);
        return found.stream().map(FieldResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<FieldResponse> listByOwner(Long ownerId) {
        return fields.findByOwnerIdOrderByNameAsc(ownerId).stream().map(FieldResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public FieldResponse get(Long fieldId) {
        return FieldResponse.from(load(fieldId));
    }

    @Transactional
    public FieldResponse create(Long ownerId, CreateFieldRequest request) {
        User owner = users.findById(ownerId).orElseThrow(() -> new NotFoundException("User " + ownerId + " not found"));
        Field field = new Field(request.name().trim(), request.address().trim(), request.latitude(),
                request.longitude(), request.pricePerHour(), owner, request.zone());
        return FieldResponse.from(fields.save(field));
    }

    @Transactional
    public FieldResponse setSchedule(Long ownerId, Long fieldId, ScheduleRequest request) {
        Field field = load(fieldId);
        if (!field.isOwnedBy(ownerId)) {
            throw new ForbiddenException("You are not the owner of this field");
        }
        // Creator: the Field builds its own Schedule.
        field.setSchedule(request.dayOfWeek(), request.openingHour(), request.closingHour());
        return FieldResponse.from(fields.saveAndFlush(field));
    }

    /** One slot per opening hour of that day, telling whether it can still be booked. */
    @Transactional(readOnly = true)
    public List<SlotResponse> availability(Long fieldId, LocalDate date) {
        Field field = load(fieldId);
        Optional<Schedule> schedule = field.scheduleFor(date.getDayOfWeek());
        if (schedule.isEmpty()) {
            return List.of();
        }
        List<Reservation> booked = reservations.findByFieldIdAndDate(fieldId, date);
        List<SlotResponse> slots = new ArrayList<>();
        for (int hour = schedule.get().getOpeningHour(); hour < schedule.get().getClosingHour(); hour++) {
            final int h = hour;
            // Expert: each Reservation answers whether it occupies that hour.
            boolean taken = booked.stream().anyMatch(r -> r.occupies(h));
            slots.add(new SlotResponse(hour, !taken));
        }
        return slots;
    }

    private Field load(Long fieldId) {
        return fields.findById(fieldId).orElseThrow(() -> new NotFoundException("Field " + fieldId + " not found"));
    }
}
