package com.tcc.booking.hold.service;

import com.tcc.booking.hold.dto.CreateHoldRequest;
import com.tcc.booking.hold.dto.HoldResponse;
import com.tcc.booking.hold.model.Hold;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class HoldService {

    private final Map<String, Hold> store = new HashMap<>();

    // Flight hold allowed range: [24h, 72h]
    private static final int FLIGHT_MIN = 24;
    private static final int FLIGHT_MAX = 72;

    // Hotel default: 48h (no explicit range provided here)
    private static final int HOTEL_DEFAULT = 48;

    public HoldResponse createFlightHold(CreateHoldRequest req) {
        int hours = req.getDurationHours() != null ? req.getDurationHours() : FLIGHT_MIN;
        if (hours < FLIGHT_MIN) hours = FLIGHT_MIN;
        if (hours > FLIGHT_MAX) hours = FLIGHT_MAX;

        return createHold("FLIGHT", req.getReference(), hours);
    }

    public HoldResponse createHotelHold(CreateHoldRequest req) {
        int hours = req.getDurationHours() != null ? req.getDurationHours() : HOTEL_DEFAULT;
        return createHold("HOTEL", req.getReference(), hours);
    }

    private HoldResponse createHold(String type, String reference, int hours) {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expires = now.plus(hours, ChronoUnit.HOURS);

        Hold h = new Hold(id, type, reference, now, expires);
        store.put(id, h);

        return new HoldResponse(id, type, reference, now, expires);
    }

    public HoldResponse getHold(String id) {
        Hold h = store.get(id);
        if (h == null) return null;
        return new HoldResponse(h.getId(), h.getType(), h.getReference(), h.getCreatedAt(), h.getExpiresAt());
    }
}
