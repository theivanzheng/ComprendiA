import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { TranscripcionServicio } from './servicios/transcripcion.servicio';
import { RespuestaTranscripcion } from './modelos/respuesta-transcripcion';
import { VideoResumen } from './modelos/video-resumen';
import { FragmentoVideo } from './modelos/fragmento-video';
import { ResultadoBusqueda } from './modelos/resultado-busqueda';
import { FaseTrabajo } from './modelos/estado-trabajo';
import { RespuestaRag } from './modelos/respuesta-rag';

interface PasoProcesamiento {
  fase: FaseTrabajo;
  label: string;
  descripcion: string;
}

@Component({
  selector: 'app-root',
  imports: [FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit, OnDestroy {

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
  protected respuestaRag: RespuestaRag | null = null;
  protected fuentesExpandidas = false;

  protected faseActual: FaseTrabajo | null = null;

  protected editandoTitulo = false;
  protected tituloEdicion = '';
  protected guardandoTitulo = false;
  protected tiempoProcesando = 0;
  protected mostrarAviso60s = false;
  protected mostrarAviso180s = false;

  private intervaloPolling: ReturnType<typeof setInterval> | null = null;
  private intervaloTiempo: ReturnType<typeof setInterval> | null = null;

  readonly PASOS: PasoProcesamiento[] = [
    { fase: 'DESCARGANDO',   label: 'Descargando audio',         descripcion: 'yt-dlp descarga el audio del vídeo de YouTube…' },
    { fase: 'TRANSCRIBIENDO', label: 'Transcribiendo con Whisper', descripcion: 'Enviando audio a la API de OpenAI Whisper…' },
    { fase: 'GUARDANDO',      label: 'Guardando transcripción',    descripcion: 'Persistiendo los fragmentos en PostgreSQL…' },
    { fase: 'EMBEDDINGS',     label: 'Generando embeddings',       descripcion: 'Calculando vectores semánticos con OpenAI…' },
  ];

  private readonly ORDEN_FASES: FaseTrabajo[] = [
    'DESCARGANDO', 'TRANSCRIBIENDO', 'GUARDANDO', 'EMBEDDINGS'
  ];

  constructor(
    private transcripcionServicio: TranscripcionServicio,
    private cd: ChangeDetectorRef,
    private sanitizer: DomSanitizer
  ) {}

  ngOnInit(): void {
    this.cargarHistorial();
  }

  ngOnDestroy(): void {
    this.detenerPolling();
    this.detenerTemporizador();
  }

  procesarVideo(): void {
    if (!this.urlVideo.trim()) return;
    this.cargando = true;
    this.error = '';
    this.transcripcion = null;
    this.faseActual = null;
    this.iniciarTemporizador();

    this.transcripcionServicio.iniciarProcesamiento({ urlVideo: this.urlVideo }).subscribe({
      next: ({ idTrabajo }) => {
        this.iniciarPolling(idTrabajo);
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
        this.cd.detectChanges();
      },
      error: () => {
        this.cargandoHistorial = false;
        this.cd.detectChanges();
      }
    });
  }

  get urlEmbed(): SafeResourceUrl | null {
    if (!this.videoSeleccionado?.youtubeId) return null;
    return this.sanitizer.bypassSecurityTrustResourceUrl(
      `https://www.youtube.com/embed/${this.videoSeleccionado.youtubeId}`
    );
  }

  iniciarEdicionTitulo(): void {
    if (!this.videoSeleccionado) return;
    this.tituloEdicion = this.videoSeleccionado.titulo;
    this.editandoTitulo = true;
    this.cd.detectChanges();
  }

  guardarTitulo(): void {
    if (!this.videoSeleccionado || !this.tituloEdicion.trim()) return;
    this.guardandoTitulo = true;
    this.transcripcionServicio.actualizarTitulo(this.videoSeleccionado.id, this.tituloEdicion.trim()).subscribe({
      next: () => {
        this.videoSeleccionado!.titulo = this.tituloEdicion.trim();
        const enHistorial = this.historial.find(v => v.id === this.videoSeleccionado!.id);
        if (enHistorial) enHistorial.titulo = this.tituloEdicion.trim();
        this.editandoTitulo = false;
        this.guardandoTitulo = false;
        this.cd.detectChanges();
      },
      error: () => {
        this.guardandoTitulo = false;
        this.cd.detectChanges();
      }
    });
  }

  cancelarEdicionTitulo(): void {
    this.editandoTitulo = false;
    this.cd.detectChanges();
  }

  seleccionarVideo(video: VideoResumen): void {
    this.videoSeleccionado = video;
    this.fragmentos = [];
    this.resultadosBusqueda = [];
    this.respuestaRag = null;
    this.pregunta = '';
    this.errorBusqueda = '';
    this.editandoTitulo = false;
    this.fuentesExpandidas = false;
    this.cargandoFragmentos = true;

    this.transcripcionServicio.obtenerFragmentos(video.id).subscribe({
      next: (fragmentos) => {
        this.fragmentos = fragmentos;
        this.cargandoFragmentos = false;
        this.cd.detectChanges();
      },
      error: () => {
        this.cargandoFragmentos = false;
        this.cd.detectChanges();
      }
    });
  }

  buscarEnVideo(): void {
    if (!this.pregunta.trim() || !this.videoSeleccionado) return;
    this.cargandoBusqueda = true;
    this.errorBusqueda = '';
    this.respuestaRag = null;
    this.fuentesExpandidas = false;

    this.transcripcionServicio.responder(this.videoSeleccionado.id, this.pregunta).subscribe({
      next: (respuesta) => {
        this.respuestaRag = respuesta;
        this.cargandoBusqueda = false;
        this.cd.detectChanges();
      },
      error: (err) => {
        this.errorBusqueda = err.error?.error ?? 'Error al generar la respuesta';
        this.cargandoBusqueda = false;
        this.cd.detectChanges();
      }
    });
  }

  esFaseCompletada(fase: FaseTrabajo): boolean {
    if (!this.faseActual) return false;
    return this.ORDEN_FASES.indexOf(this.faseActual) > this.ORDEN_FASES.indexOf(fase);
  }

  esFasePendiente(fase: FaseTrabajo): boolean {
    if (!this.faseActual) return true;
    return this.ORDEN_FASES.indexOf(fase) > this.ORDEN_FASES.indexOf(this.faseActual);
  }

  formatearTiempo(segundos: number): string {
    const m = Math.floor(segundos / 60);
    const s = Math.floor(segundos % 60);
    return `${m}:${s.toString().padStart(2, '0')}`;
  }

  formatearSimilitud(similitud: number): string {
    return `${Math.round(similitud * 100)}%`;
  }

  private iniciarPolling(idTrabajo: string): void {
    this.intervaloPolling = setInterval(() => {
      this.transcripcionServicio.obtenerEstadoTrabajo(idTrabajo).subscribe({
        next: (estado) => {
          this.faseActual = estado.fase;
          if (estado.fase === 'COMPLETADO') {
            this.detenerPolling();
            this.detenerTemporizador();
            this.transcripcion = estado.resultado!;
            this.cargando = false;
            this.cargarHistorial();
          } else if (estado.fase === 'ERROR') {
            this.detenerPolling();
            this.detenerTemporizador();
            this.error = estado.error ?? 'Error desconocido en el procesamiento';
            this.cargando = false;
          }
          this.cd.detectChanges();
        },
        error: () => {
          this.detenerPolling();
          this.detenerTemporizador();
          this.error = 'Error al consultar el estado del procesamiento';
          this.cargando = false;
          this.cd.detectChanges();
        }
      });
    }, 2500);
  }

  private detenerPolling(): void {
    if (this.intervaloPolling !== null) {
      clearInterval(this.intervaloPolling);
      this.intervaloPolling = null;
    }
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
