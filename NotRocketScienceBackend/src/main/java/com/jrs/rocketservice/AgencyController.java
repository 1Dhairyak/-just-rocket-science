package com.jrs.rocketservice;

import com.jrs.rocketservice.RocketStatus;
import com.jrs.rocketservice.AgencyResponse;
import com.jrs.rocketservice.AgencySummaryResponse;
import com.jrs.rocketservice.RocketSummaryResponse;
import com.jrs.rocketservice.AgencyService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agencies")
@CrossOrigin(origins = "*")
public class AgencyController {

    private final AgencyService agencyService;

    public AgencyController(AgencyService agencyService) {
        this.agencyService = agencyService;
    }

    @GetMapping
    public ResponseEntity<Page<AgencySummaryResponse>> getAllAgencies(
            @RequestParam(required = false) String country,
            Pageable pageable) {
        return ResponseEntity.ok(agencyService.getAllAgencies(country, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AgencyResponse> getAgencyById(@PathVariable Long id) {
        return ResponseEntity.ok(agencyService.getAgencyById(id));
    }

    @GetMapping("/{agencyId}/rockets")
    public ResponseEntity<Page<RocketSummaryResponse>> getAgencyRockets(
            @PathVariable Long agencyId,
            @RequestParam(required = false) RocketStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(agencyService.getAgencyRockets(agencyId, status, pageable));
    }
}
