package es.comprendia.dto;

/**
 * Resultado de la búsqueda semántica en documentos del curso: el texto del trozo, el nombre del
 * documento de origen y la similitud con la pregunta. A diferencia de la transcripción, no tiene
 * marcas de tiempo (un documento no se "reproduce").
 */
public record FuenteDocumentoDTO(
    String texto,
    String nombreDocumento,
    double similitud
) {}
