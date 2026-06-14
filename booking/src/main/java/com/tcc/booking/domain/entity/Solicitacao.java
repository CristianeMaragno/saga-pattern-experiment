package com.tcc.booking.domain.entity;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Solicitacao {
    private Long id;
    private Long usuarioId;
    private String destino;
    private LocalDate dataIda;
    private LocalDate dataVolta;
    private String motivo;
}
