package com.canchaya.web;

import com.canchaya.domain.Zone;
import com.canchaya.dto.FieldDtos.CreateFieldRequest;
import com.canchaya.dto.FieldDtos.FieldResponse;
import com.canchaya.dto.FieldDtos.NearbyFieldResponse;
import com.canchaya.dto.FieldDtos.ScheduleRequest;
import com.canchaya.dto.FieldDtos.SlotResponse;
import com.canchaya.security.AuthenticatedUser;
import com.canchaya.service.FieldService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** GRASP Controller for the "browse fields" and "publish field" use cases. */
@RestController
@RequestMapping("/api")
public class FieldController {

    private final FieldService fieldService;

    public FieldController(FieldService fieldService) {
        this.fieldService = fieldService;
    }

    /** GET /api/fields or GET /api/fields?zone=NORTH (NORTH, CENTER, WEST, EAST, SOUTH). */
    @GetMapping("/fields")
    public List<FieldResponse> list(@RequestParam(required = false) Zone zone) {
        return fieldService.listActive(zone);
    }

    /** GET /api/fields/nearby?lat=1.21&lng=-77.28&limit=10 : nearest active fields first. */
    @GetMapping("/fields/nearby")
    public List<NearbyFieldResponse> nearby(@RequestParam double lat, @RequestParam double lng,
                                            @RequestParam(defaultValue = "10") int limit) {
        return fieldService.nearby(lat, lng, limit);
    }

    @GetMapping("/fields/{id}")
    public FieldResponse get(@PathVariable Long id) {
        return fieldService.get(id);
    }

    @GetMapping("/fields/{id}/availability")
    public List<SlotResponse> availability(@PathVariable Long id,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return fieldService.availability(id, date);
    }

    @PostMapping("/fields")
    @ResponseStatus(HttpStatus.CREATED)
    public FieldResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                @Valid @RequestBody CreateFieldRequest request) {
        return fieldService.create(user.id(), request);
    }

    @PostMapping("/fields/{id}/schedules")
    public FieldResponse setSchedule(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                     @Valid @RequestBody ScheduleRequest request) {
        return fieldService.setSchedule(user.id(), id, request);
    }

    @GetMapping("/owner/fields")
    public List<FieldResponse> myFields(@AuthenticationPrincipal AuthenticatedUser user) {
        return fieldService.listByOwner(user.id());
    }
}
