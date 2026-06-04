export interface Asignatura {
  id: number;
  nombre: string;
  descripcion: string | null;
  profesor: string | null;
  numeroClases: number;
  fechaActualizacion: string | null;
}
