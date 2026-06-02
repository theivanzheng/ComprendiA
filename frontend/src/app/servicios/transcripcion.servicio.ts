import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SolicitudYoutube } from '../modelos/solicitud-youtube';
import { RespuestaTranscripcion } from '../modelos/respuesta-transcripcion';
import { VideoResumen } from '../modelos/video-resumen';
import { FragmentoVideo } from '../modelos/fragmento-video';
import { ResultadoBusqueda } from '../modelos/resultado-busqueda';

@Injectable({ providedIn: 'root' })
export class TranscripcionServicio {

  private readonly urlBase = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  procesarYoutube(solicitud: SolicitudYoutube): Observable<RespuestaTranscripcion> {
    return this.http.post<RespuestaTranscripcion>(
      `${this.urlBase}/transcripciones/youtube`,
      solicitud
    );
  }

  obtenerHistorial(): Observable<VideoResumen[]> {
    return this.http.get<VideoResumen[]>(`${this.urlBase}/transcripciones`);
  }

  obtenerFragmentos(id: number): Observable<FragmentoVideo[]> {
    return this.http.get<FragmentoVideo[]>(`${this.urlBase}/transcripciones/${id}/fragmentos`);
  }

  buscar(id: number, pregunta: string): Observable<ResultadoBusqueda[]> {
    const params = { pregunta };
    return this.http.get<ResultadoBusqueda[]>(`${this.urlBase}/transcripciones/${id}/buscar`, { params });
  }
}
