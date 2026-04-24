import { Component } from '@angular/core';
import { TranscripcionServicio } from './servicios/transcripcion.servicio';
import { RespuestaTranscripcion } from './modelos/respuesta-transcripcion';

@Component({
  selector: 'app-root',
  imports: [],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {

  protected cargando = false;
  protected error = '';
  protected transcripcion: RespuestaTranscripcion | null = null;

  constructor(private transcripcionServicio: TranscripcionServicio) {}

  procesarVideo(url: string): void {
    if (!url.trim()) return;
    this.cargando = true;
    this.error = '';
    this.transcripcion = null;

    this.transcripcionServicio.procesarYoutube({ urlVideo: url }).subscribe({
      next: (respuesta) => {
        this.transcripcion = respuesta;
        this.cargando = false;
      },
      error: (err) => {
        this.error = err.error?.error ?? 'Error al conectar con el servidor';
        this.cargando = false;
      }
    });
  }
}
