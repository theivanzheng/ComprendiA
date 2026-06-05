import { ResultadoBusqueda } from './resultado-busqueda';

/**
 * Un turno del chat conversacional del vídeo.
 * Vive SOLO en el frontend (no se persiste en backend). Se pierde al salir de la clase.
 */
export interface MensajeChat {
  rol: 'user' | 'assistant';
  contenido: string;
  fuentes?: ResultadoBusqueda[];
  timestamp?: number;
}
