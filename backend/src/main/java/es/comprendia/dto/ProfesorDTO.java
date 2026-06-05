package es.comprendia.dto;

import java.time.LocalDateTime;

public record ProfesorDTO(
    Long id,
    String nombre,
    String email,
    long numeroAsignaturas,
    LocalDateTime fechaCreacion,
    LocalDateTime fechaActualizacion
) {}
