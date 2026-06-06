package es.comprendia.dto;

/**
 * Un modelo de chat disponible para el asistente (multi-modelo).
 * - id: identificador interno ("openai", "gemini").
 * - nombre: etiqueta legible para la interfaz.
 * - disponible: true si su clave de API está configurada.
 */
public record ModeloChatDTO(
    String id,
    String nombre,
    boolean disponible
) {}
