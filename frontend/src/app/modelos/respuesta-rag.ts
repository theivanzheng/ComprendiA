import { ResultadoBusqueda } from './resultado-busqueda';

export interface RespuestaRag {
  respuesta: string;
  fuentes: ResultadoBusqueda[];
}
