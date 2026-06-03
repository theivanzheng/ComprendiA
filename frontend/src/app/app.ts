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
import { CapituloVideo } from './modelos/capitulo-video';
import { ConceptoClaveVideo } from './modelos/concepto-clave-video';

interface PasoProcesamiento {
  fase: FaseTrabajo;
  label: string;
  descripcion: string;
}

type VistaApp = 'home' | 'clase' | 'cursos' | 'historial';

interface CapituloClase {
  titulo: string;
  descripcion: string;
  tiempo: number;
  tiempoFin: number;
  origen: string;
}

interface ConceptoClave {
  nombre: string;
  definicion: string;
  tiempo: number;
}

interface CursoVista {
  nombre: string;
  descripcion: string;
  clases: number;
  acento: string;
}

interface ClaseRelacionada {
  titulo: string;
  descripcion: string;
  fecha: string;
}

@Component({
  selector: 'app-root',
  imports: [FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit, OnDestroy {
  protected vistaActual: VistaApp = 'home';
  protected chatColapsado = false;

  protected urlVideo = '';
  protected cargando = false;
  protected error = '';
  protected transcripcion: RespuestaTranscripcion | null = null;
  protected mensajeCancelacion = '';

  protected historial: VideoResumen[] = [];
  protected cargandoHistorial = false;

  protected videoSeleccionado: VideoResumen | null = null;
  protected fragmentos: FragmentoVideo[] = [];
  protected cargandoFragmentos = false;
  protected capitulos: CapituloVideo[] = [];
  protected conceptos: ConceptoClaveVideo[] = [];

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
  protected trabajoActivoId: string | null = null;
  protected asignaturaClase = 'Sin asignatura';
  protected fechaClase = this.obtenerFechaActual();
  protected tiempoInicioReproductor = 0;

  protected readonly asignaturasDisponibles = [
    'Sin asignatura',
    'Inteligencia Artificial',
    'Bases de Datos',
    'Algoritmos',
    'Programacion Web'
  ];

  protected readonly sugerenciasChat = [
    'Resume la clase',
    'Explicame este concepto',
    'Dime los puntos importantes',
    'Genera preguntas de repaso'
  ];

  protected readonly clasesRelacionadas: ClaseRelacionada[] = [
    {
      titulo: 'Preparacion de conceptos previos',
      descripcion: 'Clase relacionada de la misma asignatura con contexto anterior.',
      fecha: '08/05/2026'
    },
    {
      titulo: 'Ejercicios aplicados',
      descripcion: 'Sesion cercana con ejemplos practicos y resolucion guiada.',
      fecha: '12/05/2026'
    },
    {
      titulo: 'Repaso y dudas',
      descripcion: 'Clase de cierre con preguntas frecuentes del bloque.',
      fecha: '15/05/2026'
    }
  ];

  private intervaloPolling: ReturnType<typeof setInterval> | null = null;
  private intervaloTiempo: ReturnType<typeof setInterval> | null = null;

  readonly PASOS: PasoProcesamiento[] = [
    { fase: 'DESCARGANDO',   label: 'Descargando audio',         descripcion: 'yt-dlp descarga el audio del video de YouTube...' },
    { fase: 'TRANSCRIBIENDO', label: 'Transcribiendo con Whisper', descripcion: 'Enviando audio a la API de OpenAI Whisper...' },
    { fase: 'GUARDANDO',      label: 'Guardando transcripcion',    descripcion: 'Persistiendo los fragmentos en PostgreSQL...' },
    { fase: 'EMBEDDINGS',     label: 'Generando embeddings',       descripcion: 'Calculando vectores semanticos con OpenAI...' },
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
    this.detenerPolling();
    this.detenerTemporizador();
    this.vistaActual = 'clase';
    this.chatColapsado = false;
    this.cargando = true;
    this.error = '';
    this.mensajeCancelacion = '';
    this.transcripcion = null;
    this.videoSeleccionado = null;
    this.fragmentos = [];
    this.capitulos = [];
    this.conceptos = [];
    this.resultadosBusqueda = [];
    this.respuestaRag = null;
    this.pregunta = '';
    this.errorBusqueda = '';
    this.tiempoInicioReproductor = 0;
    this.faseActual = null;
    this.iniciarTemporizador();

    this.transcripcionServicio.iniciarProcesamiento({ urlVideo: this.urlVideo }).subscribe({
      next: ({ idTrabajo }) => {
        this.trabajoActivoId = idTrabajo;
        this.iniciarPolling(idTrabajo);
      },
      error: (err) => {
        this.error = err.error?.error ?? 'Error al conectar con el servidor';
        this.cargando = false;
        this.detenerTemporizador();
      }
    });
  }

  cargarHistorial(alCompletar?: () => void): void {
    this.cargandoHistorial = true;
    this.transcripcionServicio.obtenerHistorial().subscribe({
      next: (videos) => {
        this.historial = videos;
        this.cargandoHistorial = false;
        alCompletar?.();
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

  get urlEmbedClase(): SafeResourceUrl | null {
    const youtubeId = this.videoSeleccionado?.youtubeId
      ?? this.transcripcion?.idVideo
      ?? this.extraerYoutubeId(this.urlVideo);

    if (!youtubeId) return null;
    const inicio = Math.max(0, Math.floor(this.tiempoInicioReproductor));
    const query = inicio > 0 ? `?start=${inicio}&autoplay=1` : '';
    return this.sanitizer.bypassSecurityTrustResourceUrl(
      `https://www.youtube.com/embed/${youtubeId}${query}`
    );
  }

  get tituloClase(): string {
    return this.videoSeleccionado?.titulo
      ?? this.transcripcion?.titulo
      ?? 'Clase en procesamiento';
  }

  get fuenteClase(): string {
    return this.videoSeleccionado?.fuenteTranscripcion
      ?? this.transcripcion?.fuenteTranscripcion
      ?? 'Pendiente';
  }

  get fragmentosClase(): Array<FragmentoVideo | { texto: string; tiempoInicio: number; tiempoFin: number }> {
    if (this.fragmentos.length > 0) return this.fragmentos;
    return this.transcripcion?.fragmentos ?? [];
  }

  get faseActualLabel(): string {
    if (!this.faseActual) return 'Preparando analisis';
    return this.PASOS.find(paso => paso.fase === this.faseActual)?.label ?? this.faseActual;
  }

  get capitulosClase(): CapituloClase[] {
    const fragmentos = this.fragmentosClase;
    if (this.capitulos.length > 0) {
      return this.capitulos.map(capitulo => ({
        titulo: capitulo.titulo,
        descripcion: capitulo.descripcion,
        tiempo: capitulo.tiempoInicio,
        tiempoFin: capitulo.tiempoFin,
        origen: capitulo.origen
      }));
    }

    if (fragmentos.length === 0) {
      return [
        {
          titulo: 'Inicio de la clase',
          descripcion: 'Primer bloque detectado cuando el analisis este disponible.',
          tiempo: 0,
          tiempoFin: 0,
          origen: 'Manual'
        },
        {
          titulo: 'Bloque principal',
          descripcion: 'Desarrollo central de la sesion.',
          tiempo: 600,
          tiempoFin: 1200,
          origen: 'IA'
        },
        {
          titulo: 'Cierre y conclusiones',
          descripcion: 'Resultado final de la clase.',
          tiempo: 1800,
          tiempoFin: 2100,
          origen: 'IA'
        }
      ];
    }

    const automaticos = fragmentos.slice(0, 5).map((fragmento, index) => ({
      titulo: this.crearTituloCorto(fragmento.texto, index + 1),
      descripcion: this.recortarTexto(fragmento.texto, 140),
      tiempo: fragmento.tiempoInicio,
      tiempoFin: fragmento.tiempoFin,
      origen: 'AUTO'
    }));

    return [
      {
        titulo: 'Inicio marcado',
        descripcion: 'Punto de entrada manual al video.',
        tiempo: 0,
        tiempoFin: 0,
        origen: 'Manual'
      },
      ...automaticos
    ];
  }

  get conceptosClave(): ConceptoClave[] {
    if (this.conceptos.length > 0) {
      return this.conceptos.map(concepto => ({
        nombre: concepto.nombre,
        definicion: concepto.definicion,
        tiempo: concepto.tiempoInicio
      }));
    }

    const fragmentos = this.fragmentosClase;
    if (fragmentos.length === 0) {
      return [
        {
          nombre: 'Concepto principal',
          definicion: 'Idea central detectada cuando finalice el analisis.',
          tiempo: 0
        },
        {
          nombre: 'Resultado de la clase',
          definicion: 'Conclusion o aprendizaje final que se extraera del video.',
          tiempo: 0
        }
      ];
    }

    return fragmentos.slice(0, 6).map((fragmento, index) => ({
      nombre: this.crearTituloCorto(fragmento.texto, index + 1),
      definicion: this.recortarTexto(fragmento.texto, 135),
      tiempo: fragmento.tiempoInicio
    }));
  }

  get resumenClase(): string {
    const fragmentos = this.fragmentosClase;
    if (fragmentos.length === 0) {
      return 'El resumen se generara cuando termine el analisis. Mostrara como arranca la clase, que trabajo se desarrolla y con que resultado termina.';
    }

    const inicio = this.recortarTexto(fragmentos[0].texto, 130);
    const cierre = this.recortarTexto(fragmentos[fragmentos.length - 1].texto, 130);
    return `La clase empieza trabajando sobre "${inicio}". Durante la sesion se conectan los bloques principales del video y termina con "${cierre}".`;
  }

  get cursosVista(): CursoVista[] {
    const total = this.historial.length;
    return [
      {
        nombre: 'Inteligencia Artificial',
        descripcion: 'Clases sobre modelos, busqueda semantica y razonamiento con IA.',
        clases: total > 0 ? Math.max(1, Math.ceil(total / 2)) : 0,
        acento: '#4f7cff'
      },
      {
        nombre: 'Bases de Datos',
        descripcion: 'Sesiones sobre modelado, consultas y estructuras relacionales.',
        clases: total > 1 ? Math.max(1, Math.floor(total / 3)) : 0,
        acento: '#12b886'
      },
      {
        nombre: 'Algoritmos',
        descripcion: 'Analisis de complejidad, estrategias de solucion y practica.',
        clases: total > 2 ? Math.max(1, total - 2) : 0,
        acento: '#f59f00'
      }
    ];
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
    this.vistaActual = 'clase';
    this.chatColapsado = false;
    this.videoSeleccionado = video;
    this.fechaClase = this.normalizarFecha(video.fechaCreacion);
    this.fragmentos = [];
    this.capitulos = [];
    this.conceptos = [];
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

    this.transcripcionServicio.obtenerCapitulos(video.id).subscribe({
      next: (capitulos) => {
        this.capitulos = capitulos;
        this.cd.detectChanges();
      },
      error: () => {
        this.capitulos = [];
        this.cd.detectChanges();
      }
    });

    this.transcripcionServicio.obtenerConceptos(video.id).subscribe({
      next: (conceptos) => {
        this.conceptos = conceptos;
        this.cd.detectChanges();
      },
      error: () => {
        this.conceptos = [];
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

  irAHome(): void {
    this.vistaActual = 'home';
  }

  irAWorkspace(): void {
    this.vistaActual = 'clase';
  }

  irAClase(): void {
    this.vistaActual = 'clase';
  }

  irACursos(): void {
    this.vistaActual = 'cursos';
  }

  irAHistorial(): void {
    this.vistaActual = 'historial';
  }

  abrirClaseDesdeHome(video: VideoResumen): void {
    this.seleccionarVideo(video);
  }

  alternarChat(): void {
    this.chatColapsado = !this.chatColapsado;
  }

  cancelarAnalisis(): void {
    if (!this.cargando) return;
    const idTrabajo = this.trabajoActivoId;

    if (!idTrabajo) {
      this.aplicarCancelacionLocal();
      return;
    }

    this.transcripcionServicio.cancelarProcesamiento(idTrabajo).subscribe({
      next: () => {
        this.aplicarCancelacionLocal();
      },
      error: (err) => {
        this.error = err.error?.error ?? 'No se pudo cancelar el analisis';
        this.cd.detectChanges();
      }
    });
  }

  aplicarSugerenciaChat(sugerencia: string): void {
    this.pregunta = sugerencia;
    if (this.videoSeleccionado) {
      this.buscarEnVideo();
    }
  }

  preguntarConcepto(concepto: ConceptoClave): void {
    this.pregunta = `Explicame el concepto "${concepto.nombre}" en esta clase`;
    if (this.videoSeleccionado) {
      this.buscarEnVideo();
    }
  }

  saltarATiempo(segundos: number): void {
    this.tiempoInicioReproductor = segundos;
    this.cd.detectChanges();
  }

  private iniciarPolling(idTrabajo: string): void {
    this.intervaloPolling = setInterval(() => {
      this.transcripcionServicio.obtenerEstadoTrabajo(idTrabajo).subscribe({
        next: (estado) => {
          this.faseActual = estado.fase;
          if (estado.fase === 'COMPLETADO') {
            this.detenerPolling();
            this.detenerTemporizador();
            this.trabajoActivoId = null;
            this.transcripcion = estado.resultado!;
            this.cargando = false;
            this.cargarHistorial(() => {
              const idTranscripcion = estado.resultado?.idTranscripcion;
              const videoRecienProcesado = idTranscripcion
                ? this.historial.find(video => video.id === idTranscripcion)
                : this.historial.find(video => video.youtubeId === estado.resultado!.idVideo);
              if (videoRecienProcesado) {
                this.seleccionarVideo(videoRecienProcesado);
              }
            });
          } else if (estado.fase === 'ERROR') {
            this.detenerPolling();
            this.detenerTemporizador();
            this.trabajoActivoId = null;
            this.error = estado.error ?? 'Error desconocido en el procesamiento';
            this.cargando = false;
          } else if (estado.fase === 'CANCELADO') {
            this.detenerPolling();
            this.detenerTemporizador();
            this.trabajoActivoId = null;
            this.faseActual = null;
            this.cargando = false;
            this.mensajeCancelacion = estado.error ?? 'Analisis cancelado';
          }
          this.cd.detectChanges();
        },
        error: () => {
          this.detenerPolling();
          this.detenerTemporizador();
          this.trabajoActivoId = null;
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

  private aplicarCancelacionLocal(): void {
    this.detenerPolling();
    this.detenerTemporizador();
    this.cargando = false;
    this.faseActual = null;
    this.trabajoActivoId = null;
    this.mensajeCancelacion = 'Analisis cancelado. No se guardara la clase si el backend no habia completado la persistencia.';
    this.cd.detectChanges();
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

  private obtenerFechaActual(): string {
    return new Date().toISOString().slice(0, 10);
  }

  private normalizarFecha(fecha: string): string {
    if (!fecha) return this.obtenerFechaActual();
    return fecha.slice(0, 10);
  }

  private crearTituloCorto(texto: string, posicion: number): string {
    const palabras = texto
      .replace(/[^\p{L}\p{N}\s]/gu, '')
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 5)
      .join(' ');

    return palabras || `Capitulo ${posicion}`;
  }

  private recortarTexto(texto: string, longitud: number): string {
    if (texto.length <= longitud) return texto;
    return `${texto.slice(0, longitud).trim()}...`;
  }

  private extraerYoutubeId(url: string): string | null {
    const limpia = url.trim();
    if (!limpia) return null;

    const patrones = [
      /[?&]v=([^&]+)/,
      /youtu\.be\/([^?&/]+)/,
      /youtube\.com\/shorts\/([^?&/]+)/
    ];

    for (const patron of patrones) {
      const match = limpia.match(patron);
      if (match?.[1]) return match[1];
    }

    return null;
  }
}
