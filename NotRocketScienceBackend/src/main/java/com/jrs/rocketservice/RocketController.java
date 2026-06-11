package com.jrs.rocketservice;

import com.jrs.rocketservice.RocketStatus;
import com.jrs.rocketservice.RocketResponse;
import com.jrs.rocketservice.RocketSummaryResponse;
import com.jrs.rocketservice.RocketService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rockets")
@CrossOrigin(origins = "*")
public class RocketController {

    private final RocketService rocketService;

    public RocketController(RocketService rocketService) {
        this.rocketService = rocketService;
    }

    @GetMapping
    public ResponseEntity<Page<RocketSummaryResponse>> getAllRockets(
            @RequestParam(required = false) RocketStatus status,
            @RequestParam(required = false) Long agencyId,
            Pageable pageable) {
        return ResponseEntity.ok(rocketService.getAllRockets(status, agencyId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RocketResponse> getRocketById(@PathVariable Long id) {
        return ResponseEntity.ok(rocketService.getRocketById(id));
    }
}
