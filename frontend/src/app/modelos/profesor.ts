export interface Profesor {
  id: number;
  nombre: string;
  email: string | null;
  numeroAsignaturas: number;
  fechaCreacion: string | null;
  fechaActualizacion: string | null;
}
