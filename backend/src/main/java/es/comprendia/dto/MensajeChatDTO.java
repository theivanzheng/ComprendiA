package es.comprendia.dto;

/**
 * Un turno del historial de chat enviado por el frontend.
 * El historial NO se persiste: vive solo en el cliente y se manda en cada consulta
 * para dar contexto conversacional al modelo (memoria corta).
 *
 * rol: "user" o "assistant".
 */
public record MensajeChatDTO(
    String rol,
    String contenido
) {}
