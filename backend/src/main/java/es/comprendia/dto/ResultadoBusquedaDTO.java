package es.comprendia.dto;

public record ResultadoBusquedaDTO(
    String texto,
    Double tiempoInicio,
    Double tiempoFin,
    Integer orden,
    double similitud
) {}
