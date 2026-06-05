package es.comprendia.entidad;

/**
 * Criterio con el que se asignó la asignatura a un vídeo.
 * - CANAL: por coincidencia de canal de YouTube.
 * - SEMANTICA: por similitud de contenido con una asignatura existente o nueva.
 * - MANUAL: el usuario la eligió/cambió a mano (ya no es sugerencia).
 * - NINGUNO: todavía sin clasificar.
 */
public enum CriterioAsignacion {
    CANAL,
    SEMANTICA,
    MANUAL,
    NINGUNO
}
