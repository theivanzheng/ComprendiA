package es.comprendia.dto;

public record FragmentoDTO(
    String texto,
    Double tiempoInicio,
    Double tiempoFin,
    Integer orden
) {}
