package es.comprendia.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AsignaturaDetalleDTO(
    Long id,
    String nombre,
    String descripcion,
    String profesor,
    long numeroClases,
    long conceptosTotales,
    double horasProcesadas,
    LocalDateTime fechaActualizacion,
    List<VideoResumenDTO> clases
) {}
