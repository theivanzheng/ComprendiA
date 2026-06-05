export interface ConceptoClaveVideo {
  id?: number;
  nombre: string;
  definicion: string;
  tiempoInicio: number;
  tiempoFin?: number | null;
  orden: number;
  creadoManual?: boolean;
  generadoPorIa?: boolean;
}
