package es.comprendia.dto;

import java.time.LocalDateTime;

public record AsignaturaResumenDTO(
    Long id,
    String nombre,
    String descripcion,
    String profesor,
    long numeroClases,
    LocalDateTime fechaActualizacion
) {}
