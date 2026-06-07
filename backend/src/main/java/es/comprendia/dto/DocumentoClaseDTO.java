package es.comprendia.dto;

import java.time.LocalDateTime;

/**
 * Metadatos de un documento del curso para mostrarlo en la interfaz (listado de documentos de una
 * clase). No incluye el contenido ni los embeddings.
 */
public record DocumentoClaseDTO(
    Long id,
    String nombreArchivo,
    String tipoMime,
    Integer numFragmentos,
    LocalDateTime fechaSubida
) {}
