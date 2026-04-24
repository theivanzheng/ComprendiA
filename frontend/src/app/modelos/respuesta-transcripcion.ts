import { FragmentoTranscripcion } from './fragmento-transcripcion';

export interface RespuestaTranscripcion {
  idVideo: string;
  titulo: string;
  fragmentos: FragmentoTranscripcion[];
}
