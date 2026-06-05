export interface CapituloVideo {
  id?: number;
  titulo: string;
  descripcion: string;
  tiempoInicio: number;
  tiempoFin: number;
  orden: number;
  origen: string;
  creadoManual?: boolean;
  generadoPorIa?: boolean;
}
