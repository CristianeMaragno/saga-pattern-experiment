package com.tcc.booking.hold.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tcc.booking.hold.dto.CreateHoldRequest;
import com.tcc.booking.hold.dto.HoldResponse;
import com.tcc.booking.hold.service.HoldService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/holds")
public class HoldController {

    private final HoldService service;

    public HoldController(HoldService service) {
        this.service = service;
    }

    @PostMapping("/flight")
    public ResponseEntity<HoldResponse> createFlightHold(@Valid @RequestBody CreateHoldRequest req) {
        HoldResponse resp = service.createFlightHold(req);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/hotel")
    public ResponseEntity<HoldResponse> createHotelHold(@Valid @RequestBody CreateHoldRequest req) {
        HoldResponse resp = service.createHotelHold(req);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}")
    public ResponseEntity<HoldResponse> getHold(@PathVariable String id) {
        HoldResponse resp = service.getHold(id);
        if (resp == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(resp);
    }
}
