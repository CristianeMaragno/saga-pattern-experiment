package com.tcc.booking.hold.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Hold {
    private String id;
    private String type; // FLIGHT or HOTEL
    private String reference; // e.g., booking reference or user id
    private Instant createdAt;
    private Instant expiresAt;
}
