import { FragmentoTranscripcion } from './fragmento-transcripcion';

export interface RespuestaTranscripcion {
  idVideo: string;
  idTranscripcion?: number;
  titulo: string;
  fuenteTranscripcion: string;
  fragmentos: FragmentoTranscripcion[];
}
