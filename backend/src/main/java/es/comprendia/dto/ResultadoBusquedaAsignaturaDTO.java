package es.comprendia.dto;

public record ResultadoBusquedaAsignaturaDTO(
    Long idClase,
    String tituloClase,
    String youtubeId,
    String fragmento,
    double tiempoInicio,
    double similitud
) {}
