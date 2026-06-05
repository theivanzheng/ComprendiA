package es.comprendia.dto;

public record ConceptoClaseAparicionDTO(
    Long idClase,
    String tituloClase,
    String definicion,
    Double tiempoInicio,
    Double tiempoFin,
    Boolean creadoManual,
    Boolean generadoPorIa
) {}
