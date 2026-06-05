package es.comprendia.dto;

import java.util.List;

public record ConceptoCursoDTO(
    String nombre,
    long totalApariciones,
    List<ConceptoClaseAparicionDTO> clases
) {}
