import { FragmentoTranscripcion } from './fragmento-transcripcion';

export interface RespuestaTranscripcion {
  idVideo: string;
  titulo: string;
  fuenteTranscripcion: string;
  fragmentos: FragmentoTranscripcion[];
}
