package es.comprendia.dto;

public record ConceptoClaveVideoDTO(
    String nombre,
    String definicion,
    Double tiempoInicio,
    Integer orden
) {}
