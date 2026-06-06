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
import { Profesor } from '../modelos/profesor';
import { ConceptoCurso } from '../modelos/concepto-curso';
import { NotaConcepto } from '../modelos/nota-concepto';

export interface VideoMetadata {
  asignatura?: string;
  profesor?: string;
  fechaClase?: string;
  completado?: boolean;
  idAsignatura?: number | null;
  idProfesor?: number | null;
  resumen?: string;
}

export interface SolicitudAsignatura {
  nombre: string;
  descripcion?: string;
  profesor?: string;
  idProfesor?: number | null;
}

export interface SolicitudProfesor {
  nombre: string;
  email?: string;
}

export interface SolicitudCapitulo {
  titulo: string;
  descripcion?: string;
  tiempoInicio?: number;
  tiempoFin?: number;
}

export interface SolicitudConcepto {
  nombre: string;
  definicion?: string;
  tiempoInicio?: number;
  tiempoFin?: number;
}

export interface ModeloChat {
  id: string;
  nombre: string;
  disponible: boolean;
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

  /**
   * URL del canal WebSocket que emite el progreso del trabajo en tiempo real.
   * Se deriva de urlBase: cambia http(s) por ws(s) y apunta a /ws/trabajos/{id} (raíz, sin /api).
   */
  urlWebSocketTrabajo(idTrabajo: string): string {
    const origen = this.urlBase
      .replace(/^http/, 'ws')          // http -> ws, https -> wss
      .replace(/\/api\/?$/, '');       // quitar el sufijo /api
    return `${origen}/ws/trabajos/${idTrabajo}`;
  }

  /** URL del canal WebSocket del chat en streaming: /ws/chat/{idVideo}. */
  urlWebSocketChat(idVideo: number): string {
    const origen = this.urlBase
      .replace(/^http/, 'ws')
      .replace(/\/api\/?$/, '');
    return `${origen}/ws/chat/${idVideo}`;
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

  obtenerConceptosCurso(id: number): Observable<ConceptoCurso[]> {
    return this.http.get<ConceptoCurso[]>(`${this.urlBase}/asignaturas/${id}/conceptos`);
  }

  obtenerNotaConcepto(idAsignatura: number, nombreConcepto: string): Observable<NotaConcepto> {
    return this.http.get<NotaConcepto>(
      `${this.urlBase}/asignaturas/${idAsignatura}/conceptos/${encodeURIComponent(nombreConcepto)}/nota`
    );
  }

  guardarNotaConcepto(idAsignatura: number, nombreConcepto: string, nota: string): Observable<NotaConcepto> {
    return this.http.patch<NotaConcepto>(
      `${this.urlBase}/asignaturas/${idAsignatura}/conceptos/${encodeURIComponent(nombreConcepto)}/nota`,
      { nota }
    );
  }

  editarConceptoCurso(idAsignatura: number, nombreConcepto: string, nuevoNombre: string, definicion: string): Observable<void> {
    return this.http.patch<void>(
      `${this.urlBase}/asignaturas/${idAsignatura}/conceptos/${encodeURIComponent(nombreConcepto)}`,
      { nuevoNombre, definicion }
    );
  }

  eliminarConceptoCurso(idAsignatura: number, nombreConcepto: string): Observable<void> {
    return this.http.delete<void>(
      `${this.urlBase}/asignaturas/${idAsignatura}/conceptos/${encodeURIComponent(nombreConcepto)}`
    );
  }

  // ── Profesores ───────────────────────────────────────────────────────────

  obtenerProfesores(): Observable<Profesor[]> {
    return this.http.get<Profesor[]>(`${this.urlBase}/profesores`);
  }

  crearProfesor(solicitud: SolicitudProfesor): Observable<Profesor> {
    return this.http.post<Profesor>(`${this.urlBase}/profesores`, solicitud);
  }

  // ── Capítulos (CRUD manual) ──────────────────────────────────────────────

  crearCapitulo(idVideo: number, solicitud: SolicitudCapitulo): Observable<CapituloVideo> {
    return this.http.post<CapituloVideo>(`${this.urlBase}/transcripciones/${idVideo}/capitulos`, solicitud);
  }

  actualizarCapitulo(idCapitulo: number, solicitud: SolicitudCapitulo): Observable<CapituloVideo> {
    return this.http.patch<CapituloVideo>(`${this.urlBase}/capitulos/${idCapitulo}`, solicitud);
  }

  eliminarCapitulo(idCapitulo: number): Observable<void> {
    return this.http.delete<void>(`${this.urlBase}/capitulos/${idCapitulo}`);
  }

  // ── Conceptos (CRUD manual) ──────────────────────────────────────────────

  crearConcepto(idVideo: number, solicitud: SolicitudConcepto): Observable<ConceptoClaveVideo> {
    return this.http.post<ConceptoClaveVideo>(`${this.urlBase}/transcripciones/${idVideo}/conceptos`, solicitud);
  }

  actualizarConcepto(idConcepto: number, solicitud: SolicitudConcepto): Observable<ConceptoClaveVideo> {
    return this.http.patch<ConceptoClaveVideo>(`${this.urlBase}/conceptos/${idConcepto}`, solicitud);
  }

  eliminarConcepto(idConcepto: number): Observable<void> {
    return this.http.delete<void>(`${this.urlBase}/conceptos/${idConcepto}`);
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

  conversar(
    id: number,
    pregunta: string,
    historial: { rol: string; contenido: string }[],
    entidadReciente: string | null,
    modelo: string | null
  ): Observable<RespuestaRag> {
    return this.http.post<RespuestaRag>(
      `${this.urlBase}/transcripciones/${id}/conversar`,
      { pregunta, historial, entidadReciente, modelo }
    );
  }

  /** Modelos de chat disponibles para el selector (multi-modelo). */
  obtenerModelos(): Observable<ModeloChat[]> {
    return this.http.get<ModeloChat[]>(`${this.urlBase}/modelos`);
  }
}
