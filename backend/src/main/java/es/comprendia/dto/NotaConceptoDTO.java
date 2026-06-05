package es.comprendia.dto;

import java.time.LocalDateTime;

public record NotaConceptoDTO(
    String nombreConcepto,
    String nota,
    LocalDateTime fechaActualizacion
) {}
