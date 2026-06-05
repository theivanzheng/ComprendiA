export interface ConceptoClaseAparicion {
  idClase: number;
  tituloClase: string;
  definicion: string | null;
  tiempoInicio: number;
  tiempoFin: number | null;
  creadoManual: boolean | null;
  generadoPorIa: boolean | null;
}

export interface ConceptoCurso {
  nombre: string;
  totalApariciones: number;
  clases: ConceptoClaseAparicion[];
}
