import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SolicitudYoutube } from '../modelos/solicitud-youtube';
import { RespuestaTranscripcion } from '../modelos/respuesta-transcripcion';

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
}
