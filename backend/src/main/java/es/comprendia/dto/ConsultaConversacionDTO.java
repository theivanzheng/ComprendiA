package es.comprendia.dto;

import java.util.List;

/**
 * Cuerpo de una consulta conversacional al asistente del vídeo.
 *
 * - pregunta: el mensaje actual del alumno.
 * - historial: últimos 5-10 turnos previos (memoria corta del frontend, no persistida).
 * - entidadReciente: entidad/tema detectado en mensajes anteriores (p. ej. "iPhone 17 Pro Max").
 *   Sirve para resolver referencias implícitas ("el móvil", "ese reloj") y enriquecer la
 *   búsqueda semántica cuando la pregunta es ambigua.
 * - modelo: proveedor de IA elegido en el selector del frontend ("openai", "gemini").
 *   Si es null/vacío se usa el modelo por defecto del backend.
 */
public record ConsultaConversacionDTO(
    String pregunta,
    List<MensajeChatDTO> historial,
    String entidadReciente,
    String modelo
) {}
