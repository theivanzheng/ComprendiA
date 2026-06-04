package es.comprendia.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record VideoResumenDTO(
    Long id,
    String youtubeId,
    String titulo,
    LocalDateTime fechaCreacion,
    String fuenteTranscripcion,
    long numeroFragmentos,
    String asignatura,
    String profesor,
    LocalDate fechaClase,
    boolean completado,
    Long idAsignatura
) {}
