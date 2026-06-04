import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SolicitudYoutube } from '../modelos/solicitud-youtube';
import { RespuestaTranscripcion } from '../modelos/respuesta-transcripcion';
import { VideoResumen } from '../modelos/video-resumen';
import { FragmentoVideo } from '../modelos/fragmento-video';
import { ResultadoBusqueda } from '../modelos/resultado-busqueda';
import { EstadoTrabajo } from '../modelos/estado-trabajo';
import { RespuestaRag } from '../modelos/respuesta-rag';
import { CapituloVideo } from '../modelos/capitulo-video';
import { ConceptoClaveVideo } from '../modelos/concepto-clave-video';
import { Asignatura } from '../modelos/asignatura';
import { AsignaturaDetalle } from '../modelos/asignatura-detalle';
import { ResultadoBusquedaAsignatura } from '../modelos/resultado-busqueda-asignatura';

export interface VideoMetadata {
  asignatura?: string;
  profesor?: string;
  fechaClase?: string;
  completado?: boolean;
  idAsignatura?: number | null;
}

export interface SolicitudAsignatura {
  nombre: string;
  descripcion?: string;
  profesor?: string;
}

@Injectable({ providedIn: 'root' })
export class TranscripcionServicio {

  private readonly urlBase = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  iniciarProcesamiento(solicitud: SolicitudYoutube): Observable<{ idTrabajo: string }> {
    return this.http.post<{ idTrabajo: string }>(
      `${this.urlBase}/transcripciones/youtube`,
      solicitud
    );
  }

  obtenerEstadoTrabajo(idTrabajo: string): Observable<EstadoTrabajo> {
    return this.http.get<EstadoTrabajo>(
      `${this.urlBase}/transcripciones/youtube/${idTrabajo}`
    );
  }

  cancelarProcesamiento(idTrabajo: string): Observable<{ idTrabajo: string; fase: string }> {
    return this.http.post<{ idTrabajo: string; fase: string }>(
      `${this.urlBase}/transcripciones/youtube/${idTrabajo}/cancelar`,
      {}
    );
  }

  actualizarTitulo(id: number, titulo: string): Observable<void> {
    return this.http.patch<void>(
      `${this.urlBase}/transcripciones/${id}/titulo`,
      { titulo }
    );
  }

  actualizarMetadata(id: number, metadata: VideoMetadata): Observable<VideoResumen> {
    return this.http.patch<VideoResumen>(
      `${this.urlBase}/transcripciones/${id}/metadata`,
      metadata
    );
  }

  obtenerHistorial(): Observable<VideoResumen[]> {
    return this.http.get<VideoResumen[]>(`${this.urlBase}/transcripciones`);
  }

  obtenerVideo(id: number): Observable<VideoResumen> {
    return this.http.get<VideoResumen>(`${this.urlBase}/transcripciones/${id}`);
  }

  obtenerFragmentos(id: number): Observable<FragmentoVideo[]> {
    return this.http.get<FragmentoVideo[]>(`${this.urlBase}/transcripciones/${id}/fragmentos`);
  }

  obtenerCapitulos(id: number): Observable<CapituloVideo[]> {
    return this.http.get<CapituloVideo[]>(`${this.urlBase}/transcripciones/${id}/capitulos`);
  }

  obtenerConceptos(id: number): Observable<ConceptoClaveVideo[]> {
    return this.http.get<ConceptoClaveVideo[]>(`${this.urlBase}/transcripciones/${id}/conceptos`);
  }

  eliminarVideo(id: number): Observable<void> {
    return this.http.delete<void>(`${this.urlBase}/transcripciones/${id}`);
  }

  // ── Asignaturas ────────────────────────────────────────────────────────────

  obtenerAsignaturas(): Observable<Asignatura[]> {
    return this.http.get<Asignatura[]>(`${this.urlBase}/asignaturas`);
  }

  crearAsignatura(solicitud: SolicitudAsignatura): Observable<Asignatura> {
    return this.http.post<Asignatura>(`${this.urlBase}/asignaturas`, solicitud);
  }

  obtenerDetalleAsignatura(id: number): Observable<AsignaturaDetalle> {
    return this.http.get<AsignaturaDetalle>(`${this.urlBase}/asignaturas/${id}`);
  }

  actualizarAsignatura(id: number, solicitud: SolicitudAsignatura): Observable<Asignatura> {
    return this.http.patch<Asignatura>(`${this.urlBase}/asignaturas/${id}`, solicitud);
  }

  eliminarAsignatura(id: number, confirmacionNombre: string): Observable<void> {
    return this.http.delete<void>(`${this.urlBase}/asignaturas/${id}`, {
      body: { confirmacionNombre }
    });
  }

  buscarEnAsignatura(id: number, pregunta: string): Observable<ResultadoBusquedaAsignatura[]> {
    return this.http.get<ResultadoBusquedaAsignatura[]>(
      `${this.urlBase}/asignaturas/${id}/buscar`,
      { params: { pregunta } }
    );
  }

  buscar(id: number, pregunta: string): Observable<ResultadoBusqueda[]> {
    return this.http.get<ResultadoBusqueda[]>(
      `${this.urlBase}/transcripciones/${id}/buscar`,
      { params: { pregunta } }
    );
  }

  responder(id: number, pregunta: string): Observable<RespuestaRag> {
    return this.http.get<RespuestaRag>(
      `${this.urlBase}/transcripciones/${id}/responder`,
      { params: { pregunta } }
    );
  }
}
