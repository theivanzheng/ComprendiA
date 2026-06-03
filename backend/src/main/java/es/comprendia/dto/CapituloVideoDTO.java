package es.comprendia.dto;

public record CapituloVideoDTO(
    String titulo,
    String descripcion,
    Double tiempoInicio,
    Double tiempoFin,
    Integer orden,
    String origen
) {}
