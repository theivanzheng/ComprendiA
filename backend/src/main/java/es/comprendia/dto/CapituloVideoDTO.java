package es.comprendia.dto;

public record CapituloVideoDTO(
    Long id,
    String titulo,
    String descripcion,
    Double tiempoInicio,
    Double tiempoFin,
    Integer orden,
    String origen,
    Boolean creadoManual,
    Boolean generadoPorIa
) {}
