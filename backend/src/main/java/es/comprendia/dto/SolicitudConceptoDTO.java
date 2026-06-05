package es.comprendia.dto;

public record SolicitudConceptoDTO(
    String nombre,
    String definicion,
    Double tiempoInicio,
    Double tiempoFin
) {}
