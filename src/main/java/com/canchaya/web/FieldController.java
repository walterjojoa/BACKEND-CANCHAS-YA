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

/**
 * Endpoints de canchas.
 * GRASP Controlador para los casos de uso "consultar canchas" y "publicar cancha":
 * recibe la petición, saca los datos y delega en {@link FieldService}.
 */
@RestController
@RequestMapping("/api")
public class FieldController {

    private final FieldService fieldService;

    public FieldController(FieldService fieldService) {
        this.fieldService = fieldService;
    }

    /** GET /api/fields o GET /api/fields?zone=NORTH (NORTH, CENTER, WEST, EAST, SOUTH). Público. */
    @GetMapping("/fields")
    public List<FieldResponse> list(@RequestParam(required = false) Zone zone) {
        return fieldService.listActive(zone);
    }

    /** GET /api/fields/nearby?lat=1.21&lng=-77.28&limit=10 : canchas activas, primero las más cercanas. */
    @GetMapping("/fields/nearby")
    public List<NearbyFieldResponse> nearby(@RequestParam double lat, @RequestParam double lng,
                                            @RequestParam(defaultValue = "10") int limit) {
        return fieldService.nearby(lat, lng, limit);
    }

    /** GET /api/fields/{id} : detalle de una cancha. Público. */
    @GetMapping("/fields/{id}")
    public FieldResponse get(@PathVariable Long id) {
        return fieldService.get(id);
    }

    /** GET /api/fields/{id}/availability?date=2026-11-25 : franjas libres/ocupadas de ese día. Público. */
    @GetMapping("/fields/{id}/availability")
    public List<SlotResponse> availability(@PathVariable Long id,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return fieldService.availability(id, date);
    }

    /**
     * POST /api/fields : publica una cancha (solo OWNER, 201 Created).
     * @AuthenticationPrincipal entrega el usuario que venía dentro del token JWT.
     */
    @PostMapping("/fields")
    @ResponseStatus(HttpStatus.CREATED)
    public FieldResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                @Valid @RequestBody CreateFieldRequest request) {
        return fieldService.create(user.id(), request);
    }

    /** POST /api/fields/{id}/schedules : define el horario de un día (solo el dueño de la cancha). */
    @PostMapping("/fields/{id}/schedules")
    public FieldResponse setSchedule(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                     @Valid @RequestBody ScheduleRequest request) {
        return fieldService.setSchedule(user.id(), id, request);
    }

    /** GET /api/owner/fields : canchas del dueño que tiene la sesión iniciada. */
    @GetMapping("/owner/fields")
    public List<FieldResponse> myFields(@AuthenticationPrincipal AuthenticatedUser user) {
        return fieldService.listByOwner(user.id());
    }
}
