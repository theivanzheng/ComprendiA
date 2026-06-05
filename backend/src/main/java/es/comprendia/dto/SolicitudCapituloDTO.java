package es.comprendia.dto;

public record SolicitudCapituloDTO(
    String titulo,
    String descripcion,
    Double tiempoInicio,
    Double tiempoFin
) {}
