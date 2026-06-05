package es.comprendia.dto;

import java.time.LocalDate;

public record VideoMetadataDTO(
    String asignatura,
    String profesor,
    LocalDate fechaClase,
    Boolean completado,
    Long idAsignatura,
    Long idProfesor,
    String resumen
) {}
