package com.tcc.booking.hold.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HoldResponse {
    private String id;
    private String type;
    private String reference;
    private Instant createdAt;
    private Instant expiresAt;
}
