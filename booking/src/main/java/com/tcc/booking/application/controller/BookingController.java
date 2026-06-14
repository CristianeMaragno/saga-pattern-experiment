package com.tcc.booking.application.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tcc.booking.application.dto.BookingResponseDTO;
import com.tcc.booking.application.dto.CreateSolicitacaoRequestDTO;
import com.tcc.booking.application.service.BookingService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponseDTO> createBooking(@Valid @RequestBody CreateSolicitacaoRequestDTO req) {
        BookingResponseDTO resp = bookingService.createBooking(req);
        return ResponseEntity.ok(resp);
    }
}
