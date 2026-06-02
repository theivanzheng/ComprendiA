import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranscripcionServicio } from './servicios/transcripcion.servicio';
import { RespuestaTranscripcion } from './modelos/respuesta-transcripcion';
import { VideoResumen } from './modelos/video-resumen';
import { FragmentoVideo } from './modelos/fragmento-video';
import { ResultadoBusqueda } from './modelos/resultado-busqueda';

@Component({
  selector: 'app-root',
  imports: [FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {

  protected urlVideo = '';
  protected cargando = false;
  protected error = '';
  protected transcripcion: RespuestaTranscripcion | null = null;

  protected historial: VideoResumen[] = [];
  protected cargandoHistorial = false;

  protected videoSeleccionado: VideoResumen | null = null;
  protected fragmentos: FragmentoVideo[] = [];
  protected cargandoFragmentos = false;

  protected pregunta = '';
  protected resultadosBusqueda: ResultadoBusqueda[] = [];
  protected cargandoBusqueda = false;
  protected errorBusqueda = '';

  protected tiempoProcesando = 0;
  protected mostrarAviso60s = false;
  protected mostrarAviso180s = false;
  private intervaloTiempo: ReturnType<typeof setInterval> | null = null;

  constructor(private transcripcionServicio: TranscripcionServicio) {}

  ngOnInit(): void {
    this.cargarHistorial();
  }

  procesarVideo(): void {
    if (!this.urlVideo.trim()) return;
    this.cargando = true;
    this.error = '';
    this.transcripcion = null;
    this.iniciarTemporizador();

    this.transcripcionServicio.procesarYoutube({ urlVideo: this.urlVideo }).subscribe({
      next: (respuesta) => {
        this.transcripcion = respuesta;
        this.cargando = false;
        this.detenerTemporizador();
        this.cargarHistorial();
      },
      error: (err) => {
        this.error = err.error?.error ?? 'Error al conectar con el servidor';
        this.cargando = false;
        this.detenerTemporizador();
      }
    });
  }

  cargarHistorial(): void {
    this.cargandoHistorial = true;
    this.transcripcionServicio.obtenerHistorial().subscribe({
      next: (videos) => {
        this.historial = videos;
        this.cargandoHistorial = false;
      },
      error: () => {
        this.cargandoHistorial = false;
      }
    });
  }

  seleccionarVideo(video: VideoResumen): void {
    this.videoSeleccionado = video;
    this.fragmentos = [];
    this.resultadosBusqueda = [];
    this.pregunta = '';
    this.errorBusqueda = '';
    this.cargandoFragmentos = true;

    this.transcripcionServicio.obtenerFragmentos(video.id).subscribe({
      next: (fragmentos) => {
        this.fragmentos = fragmentos;
        this.cargandoFragmentos = false;
      },
      error: () => {
        this.cargandoFragmentos = false;
      }
    });
  }

  buscarEnVideo(): void {
    if (!this.pregunta.trim() || !this.videoSeleccionado) return;
    this.cargandoBusqueda = true;
    this.errorBusqueda = '';
    this.resultadosBusqueda = [];

    this.transcripcionServicio.buscar(this.videoSeleccionado.id, this.pregunta).subscribe({
      next: (resultados) => {
        this.resultadosBusqueda = resultados;
        this.cargandoBusqueda = false;
      },
      error: (err) => {
        this.errorBusqueda = err.error?.error ?? 'Error en la búsqueda';
        this.cargandoBusqueda = false;
      }
    });
  }

  formatearTiempo(segundos: number): string {
    const m = Math.floor(segundos / 60);
    const s = Math.floor(segundos % 60);
    return `${m}:${s.toString().padStart(2, '0')}`;
  }

  formatearSimilitud(similitud: number): string {
    return `${Math.round(similitud * 100)}%`;
  }

  private iniciarTemporizador(): void {
    this.tiempoProcesando = 0;
    this.mostrarAviso60s = false;
    this.mostrarAviso180s = false;
    this.intervaloTiempo = setInterval(() => {
      this.tiempoProcesando++;
      if (this.tiempoProcesando === 60) this.mostrarAviso60s = true;
      if (this.tiempoProcesando === 180) this.mostrarAviso180s = true;
    }, 1000);
  }

  private detenerTemporizador(): void {
    if (this.intervaloTiempo !== null) {
      clearInterval(this.intervaloTiempo);
      this.intervaloTiempo = null;
    }
    this.tiempoProcesando = 0;
    this.mostrarAviso60s = false;
    this.mostrarAviso180s = false;
  }
}
