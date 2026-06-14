package com.tcc.booking.hold.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateHoldRequest {
    @NotBlank
    private String reference;

    // Optional duration in hours. If null, service uses defaults.
    private Integer durationHours;
}
