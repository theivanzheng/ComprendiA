export interface VideoResumen {
  id: number;
  youtubeId: string;
  titulo: string;
  fechaCreacion: string;
  fuenteTranscripcion: string;
  numeroFragmentos: number;
  asignatura: string;
  profesor: string;
  fechaClase: string | null;
  completado: boolean;
  idAsignatura: number | null;
  idProfesor: number | null;
  resumen: string | null;
}
