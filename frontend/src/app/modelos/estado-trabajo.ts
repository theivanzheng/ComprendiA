import { RespuestaTranscripcion } from './respuesta-transcripcion';

export type FaseTrabajo =
  | 'DESCARGANDO'
  | 'TRANSCRIBIENDO'
  | 'GUARDANDO'
  | 'EMBEDDINGS'
  | 'COMPLETADO'
  | 'CANCELADO'
  | 'ERROR';

export interface EstadoTrabajo {
  id: string;
  fase: FaseTrabajo;
  resultado?: RespuestaTranscripcion;
  error?: string;
}
