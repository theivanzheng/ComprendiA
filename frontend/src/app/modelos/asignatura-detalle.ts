import { VideoResumen } from './video-resumen';

export interface AsignaturaDetalle {
  id: number;
  nombre: string;
  descripcion: string | null;
  profesor: string | null;
  numeroClases: number;
  conceptosTotales: number;
  horasProcesadas: number;
  fechaActualizacion: string | null;
  clases: VideoResumen[];
}
