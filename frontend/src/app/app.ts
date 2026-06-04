import { ChangeDetectorRef, Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { TranscripcionServicio, VideoMetadata } from './servicios/transcripcion.servicio';
import { RespuestaTranscripcion } from './modelos/respuesta-transcripcion';
import { VideoResumen } from './modelos/video-resumen';
import { FragmentoVideo } from './modelos/fragmento-video';
import { ResultadoBusqueda } from './modelos/resultado-busqueda';
import { FaseTrabajo } from './modelos/estado-trabajo';
import { RespuestaRag } from './modelos/respuesta-rag';
import { CapituloVideo } from './modelos/capitulo-video';
import { ConceptoClaveVideo } from './modelos/concepto-clave-video';

declare global {
  interface Window {
    YT?: any;
    onYouTubeIframeAPIReady?: () => void;
  }
}

interface PasoProcesamiento {
  fase: FaseTrabajo;
  label: string;
  descripcion: string;
}

type VistaApp = 'home' | 'clase' | 'cursos' | 'historial';
type SelectorMetadato = 'asignatura' | 'profesor' | null;

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
  youtubeId: string;
  duracion: string;
  asignatura: string;
}

interface ClaseEnCache {
  video: VideoResumen;
  fragmentos?: FragmentoVideo[];
  capitulos?: CapituloVideo[];
  conceptos?: ConceptoClaveVideo[];
}

@Component({
  selector: 'app-root',
  imports: [FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit, OnDestroy {
  protected vistaActual: VistaApp = this.obtenerVistaInicial();
  protected chatColapsado = false;
  protected cargandoClase = this.esRutaClaseActual();
  protected mostrandoSkeletonClase = false;
  protected mostrandoMensajeCargaClase = false;
  protected errorCargaClase = '';

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
  protected preguntaEnviada = '';

  protected faseActual: FaseTrabajo | null = null;

  protected editandoTitulo = false;
  protected tituloEdicion = '';
  protected guardandoTitulo = false;
  protected tiempoProcesando = 0;
  protected mostrarAviso60s = false;
  protected mostrarAviso180s = false;
  protected trabajoActivoId: string | null = null;
  protected asignaturaClase = 'Sin asignatura';
  protected profesorClase = 'Profesor pendiente';
  protected fechaClase = this.obtenerFechaActual();
  protected tiempoInicioReproductor = 0;
  protected selectorMetadatoAbierto: SelectorMetadato = null;
  protected busquedaAsignatura = '';
  protected busquedaProfesor = '';
  protected busquedaConceptos = '';
  protected contenidoTratadoAbierto = true;
  protected readonly idIframeClase = 'comprendia-class-player';

  protected asignaturasDisponibles = [
    'Sin asignatura',
    'Inteligencia Artificial',
    'Bases de Datos',
    'Algoritmos',
    'Programacion Web'
  ];

  protected profesoresDisponibles = [
    'Profesor pendiente',
    'Manuel Martin',
    'Laura Sanchez',
    'IA Clara'
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
      fecha: '08/05/2026',
      youtubeId: 'Xphb-tzJj24',
      duracion: '12:45',
      asignatura: 'Matematicas'
    },
    {
      titulo: 'Ejercicios aplicados',
      descripcion: 'Sesion cercana con ejemplos practicos y resolucion guiada.',
      fecha: '12/05/2026',
      youtubeId: 'dQw4w9WgXcQ',
      duracion: '18:20',
      asignatura: 'Practica'
    },
    {
      titulo: 'Repaso y dudas',
      descripcion: 'Clase de cierre con preguntas frecuentes del bloque.',
      fecha: '15/05/2026',
      youtubeId: 'tu_j-G6v7nY',
      duracion: '09:35',
      asignatura: 'Repaso'
    }
  ];

  private intervaloPolling: ReturnType<typeof setInterval> | null = null;
  private intervaloTiempo: ReturnType<typeof setInterval> | null = null;
  private youtubePlayer: any = null;
  private idClaseCargando: number | null = null;
  private temporizadorSkeletonClase: ReturnType<typeof setTimeout> | null = null;
  private temporizadorMensajeClase: ReturnType<typeof setTimeout> | null = null;
  private cacheClases = new Map<number, ClaseEnCache>();

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
    this.aplicarRutaActual();
    this.cargarHistorial();
  }

  ngOnDestroy(): void {
    this.detenerPolling();
    this.detenerTemporizador();
    this.limpiarTemporizadoresCargaClase();
    this.destruirYoutubePlayer();
  }

  @HostListener('document:click')
  cerrarSelectorAlClickExterior(): void {
    if (this.selectorMetadatoAbierto) {
      this.cerrarSelectorMetadato();
    }
  }

  @HostListener('window:popstate')
  manejarCambioRuta(): void {
    this.aplicarRutaActual();
  }

  @HostListener('window:hashchange')
  manejarCambioHash(): void {
    this.aplicarRutaActual();
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
    this.preguntaEnviada = '';
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
        videos.forEach(video => this.guardarVideoEnCache(video));
        this.actualizarOpcionesMetadataDesdeVideos(videos);
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
    const youtubeId = this.youtubeIdClase;

    if (!youtubeId) return null;
    const inicio = Math.max(0, Math.floor(this.tiempoInicioReproductor));
    const parametros = new URLSearchParams({
      enablejsapi: '1',
      origin: window.location.origin
    });
    if (inicio > 0) {
      parametros.set('start', String(inicio));
      parametros.set('autoplay', '1');
    }
    return this.sanitizer.bypassSecurityTrustResourceUrl(
      `https://www.youtube.com/embed/${youtubeId}?${parametros.toString()}`
    );
  }

  get youtubeIdClase(): string | null {
    return this.videoSeleccionado?.youtubeId
      ?? this.transcripcion?.idVideo
      ?? this.extraerYoutubeId(this.urlVideo);
  }

  get thumbnailClase(): string {
    return this.obtenerThumbnailYoutube(this.youtubeIdClase);
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

  get claseActualCompletada(): boolean {
    return this.estaClaseCompletada(this.videoSeleccionado);
  }

  get duracionClase(): string {
    const tiemposFin = [
      ...this.fragmentos.map(fragmento => fragmento.tiempoFin),
      ...(this.transcripcion?.fragmentos ?? []).map(fragmento => fragmento.tiempoFin),
      ...this.capitulos.map(capitulo => capitulo.tiempoFin ?? capitulo.tiempoInicio)
    ].filter((tiempo): tiempo is number => Number.isFinite(tiempo));

    if (tiemposFin.length === 0) return this.cargando ? 'Procesando' : 'Pendiente';
    return this.formatearDuracion(Math.max(...tiemposFin));
  }

  get asignaturasFiltradas(): string[] {
    return this.filtrarOpciones(this.asignaturasDisponibles, this.busquedaAsignatura);
  }

  get profesoresFiltrados(): string[] {
    return this.filtrarOpciones(this.profesoresDisponibles, this.busquedaProfesor);
  }

  get puedeCrearAsignatura(): boolean {
    return this.puedeCrearOpcion(this.asignaturasDisponibles, this.busquedaAsignatura);
  }

  get puedeCrearProfesor(): boolean {
    return this.puedeCrearOpcion(this.profesoresDisponibles, this.busquedaProfesor);
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

  get conceptosClaveFiltrados(): ConceptoClave[] {
    const termino = this.busquedaConceptos.trim().toLowerCase();
    if (!termino) return this.conceptosClave;
    return this.conceptosClave.filter(concepto =>
      concepto.nombre.toLowerCase().includes(termino)
      || concepto.definicion.toLowerCase().includes(termino)
    );
  }

  get resumenClase(): string {
    const fragmentos = this.fragmentosClase;
    const capitulos = this.capitulosClase.filter(capitulo => capitulo.titulo && capitulo.titulo !== 'Inicio marcado');
    const conceptos = this.conceptosClave;

    if (fragmentos.length === 0 && capitulos.length === 0 && conceptos.length === 0) {
      return 'El resumen se generara cuando termine el analisis. Mostrara como arranca la clase, que trabajo se desarrolla y con que resultado termina.';
    }

    const tema = this.obtenerTemaResumen(capitulos, conceptos, fragmentos);
    const desarrollo = this.obtenerDesarrolloResumen(capitulos, conceptos, fragmentos);
    const cierre = this.obtenerCierreResumen(capitulos, fragmentos);

    return `Esta clase trata sobre ${tema}. A lo largo del video se trabaja ${desarrollo}. El recorrido termina con ${cierre}, dejando una vision global del contenido y de los puntos principales que el alumno debe retener.`;
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

  seleccionarVideo(video: VideoResumen, actualizarUrl = true, cargarDetalles = true): void {
    this.vistaActual = 'clase';
    this.cargandoClase = false;
    this.mostrandoSkeletonClase = false;
    this.mostrandoMensajeCargaClase = false;
    this.errorCargaClase = '';
    this.chatColapsado = false;
    this.videoSeleccionado = video;
    this.guardarVideoEnCache(video);
    this.asignaturaClase = video.asignatura || 'Sin asignatura';
    this.profesorClase = video.profesor || 'Profesor pendiente';
    this.actualizarOpcionesMetadataDesdeVideos([video]);
    this.fechaClase = video.fechaClase || this.normalizarFecha(video.fechaCreacion);
    this.fragmentos = [];
    this.capitulos = [];
    this.conceptos = [];
    this.resultadosBusqueda = [];
    this.respuestaRag = null;
    this.pregunta = '';
    this.preguntaEnviada = '';
    this.errorBusqueda = '';
    this.editandoTitulo = false;
    this.fuentesExpandidas = false;
    this.cargandoFragmentos = true;
    if (actualizarUrl) {
      this.actualizarRuta('#/clase/' + video.id);
    }
    this.programarSeguimientoYoutube();

    if (!cargarDetalles) {
      return;
    }

    this.cargarDetallesClase(video.id);
  }

  private cargarDetallesClase(idVideo: number): void {
    const claseCacheada = this.cacheClases.get(idVideo);
    if (claseCacheada?.fragmentos && claseCacheada.capitulos && claseCacheada.conceptos) {
      this.fragmentos = claseCacheada.fragmentos;
      this.capitulos = claseCacheada.capitulos;
      this.conceptos = claseCacheada.conceptos;
      this.cargandoFragmentos = false;
      this.cd.detectChanges();
      return;
    }

    this.cargandoFragmentos = true;

    this.transcripcionServicio.obtenerFragmentos(idVideo).subscribe({
      next: (fragmentos) => {
        if (this.videoSeleccionado?.id !== idVideo) return;
        this.fragmentos = fragmentos;
        this.guardarDetallesEnCache(idVideo, { fragmentos });
        this.cargandoFragmentos = false;
        this.cd.detectChanges();
      },
      error: () => {
        this.cargandoFragmentos = false;
        this.cd.detectChanges();
      }
    });

    this.transcripcionServicio.obtenerCapitulos(idVideo).subscribe({
      next: (capitulos) => {
        if (this.videoSeleccionado?.id !== idVideo) return;
        this.capitulos = capitulos;
        this.guardarDetallesEnCache(idVideo, { capitulos });
        this.cd.detectChanges();
      },
      error: () => {
        this.capitulos = [];
        this.cd.detectChanges();
      }
    });

    this.transcripcionServicio.obtenerConceptos(idVideo).subscribe({
      next: (conceptos) => {
        if (this.videoSeleccionado?.id !== idVideo) return;
        this.conceptos = conceptos;
        this.guardarDetallesEnCache(idVideo, { conceptos });
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
    const preguntaActual = this.pregunta.trim();
    this.cargandoBusqueda = true;
    this.errorBusqueda = '';
    this.respuestaRag = null;
    this.preguntaEnviada = preguntaActual;
    this.fuentesExpandidas = false;

    this.transcripcionServicio.responder(this.videoSeleccionado.id, preguntaActual).subscribe({
      next: (respuesta) => {
        this.respuestaRag = respuesta;
        this.pregunta = '';
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

  formatearDuracion(segundos: number): string {
    const total = Math.max(0, Math.floor(segundos));
    const horas = Math.floor(total / 3600);
    const minutos = Math.floor((total % 3600) / 60);
    const segundosRestantes = total % 60;

    if (horas > 0) {
      return `${horas}:${minutos.toString().padStart(2, '0')}:${segundosRestantes.toString().padStart(2, '0')}`;
    }
    return `${minutos}:${segundosRestantes.toString().padStart(2, '0')}`;
  }

  duracionEstimadaVideo(video: VideoResumen): string {
    if (!video.numeroFragmentos || video.numeroFragmentos <= 0) return 'Pendiente';
    return this.formatearDuracion(video.numeroFragmentos * 6);
  }

  estaClaseCompletada(video: VideoResumen | null | undefined): boolean {
    return !!video?.completado;
  }

  alternarClaseCompletada(): void {
    if (!this.videoSeleccionado) return;
    this.marcarClaseCompletada(!this.videoSeleccionado.completado);
  }

  guardarMetadataClase(): void {
    if (!this.videoSeleccionado) return;
    this.persistirMetadata({
      asignatura: this.asignaturaClase,
      profesor: this.profesorClase,
      fechaClase: this.fechaClase
    });
  }

  alternarSelectorMetadato(selector: Exclude<SelectorMetadato, null>): void {
    if (this.selectorMetadatoAbierto === selector) {
      this.cerrarSelectorMetadato();
      return;
    }

    this.selectorMetadatoAbierto = selector;
    this.busquedaAsignatura = '';
    this.busquedaProfesor = '';
  }

  cerrarSelectorMetadato(): void {
    this.selectorMetadatoAbierto = null;
    this.busquedaAsignatura = '';
    this.busquedaProfesor = '';
  }

  seleccionarAsignatura(asignatura: string): void {
    this.asignaturaClase = asignatura;
    this.cerrarSelectorMetadato();
    this.guardarMetadataClase();
  }

  seleccionarProfesor(profesor: string): void {
    this.profesorClase = profesor;
    this.cerrarSelectorMetadato();
    this.guardarMetadataClase();
  }

  crearAsignaturaDesdeBusqueda(): void {
    const asignatura = this.normalizarOpcion(this.busquedaAsignatura);
    if (!asignatura) return;
    if (!this.existeOpcion(this.asignaturasDisponibles, asignatura)) {
      this.asignaturasDisponibles = [...this.asignaturasDisponibles, asignatura];
    }
    this.seleccionarAsignatura(asignatura);
  }

  crearProfesorDesdeBusqueda(): void {
    const profesor = this.normalizarOpcion(this.busquedaProfesor);
    if (!profesor) return;
    if (!this.existeOpcion(this.profesoresDisponibles, profesor)) {
      this.profesoresDisponibles = [...this.profesoresDisponibles, profesor];
    }
    this.seleccionarProfesor(profesor);
  }

  alternarContenidoTratado(): void {
    this.contenidoTratadoAbierto = !this.contenidoTratadoAbierto;
  }

  abrirAnadirCapitulo(evento?: Event): void {
    evento?.preventDefault();
    evento?.stopPropagation();
  }

  abrirAnadirConcepto(evento?: Event): void {
    evento?.preventDefault();
    evento?.stopPropagation();
  }

  formatearSimilitud(similitud: number): string {
    return `${Math.round(similitud * 100)}%`;
  }

  obtenerThumbnailYoutube(youtubeId: string | null | undefined, calidad = 'maxresdefault'): string {
    const id = youtubeId?.trim() || this.youtubeIdClase || 'Xphb-tzJj24';
    return `https://img.youtube.com/vi/${id}/${calidad}.jpg`;
  }

  usarThumbnailFallback(evento: Event, youtubeId: string | null | undefined): void {
    const imagen = evento.target as HTMLImageElement;
    if (imagen.dataset['fallbackAplicado'] === 'true') return;
    imagen.dataset['fallbackAplicado'] = 'true';
    imagen.src = this.obtenerThumbnailYoutube(youtubeId, 'hqdefault');
  }

  irAHome(): void {
    this.cancelarCargaClasePendiente();
    this.vistaActual = 'home';
    this.actualizarRuta('#/');
  }

  irAWorkspace(): void {
    this.vistaActual = 'clase';
  }

  irAClase(): void {
    this.vistaActual = 'clase';
  }

  irACursos(): void {
    this.cancelarCargaClasePendiente();
    this.vistaActual = 'cursos';
    this.actualizarRuta('#/cursos');
  }

  irAHistorial(): void {
    this.cancelarCargaClasePendiente();
    this.vistaActual = 'historial';
    this.actualizarRuta('#/historial');
  }

  abrirClase(idVideo: number, preview?: VideoResumen): void {
    if (!Number.isFinite(idVideo)) return;
    this.actualizarRuta('#/clase/' + idVideo);
    this.cargarClaseDesdeRuta(idVideo, true, preview);
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
    this.programarSeguimientoYoutube();
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

  private obtenerTemaResumen(
    capitulos: CapituloClase[],
    conceptos: ConceptoClave[],
    fragmentos: Array<FragmentoVideo | { texto: string; tiempoInicio: number; tiempoFin: number }>
  ): string {
    const conceptosPrincipales = conceptos
      .slice(0, 3)
      .map(concepto => concepto.nombre.toLowerCase())
      .filter(Boolean);

    if (conceptosPrincipales.length > 0) {
      return conceptosPrincipales.join(', ');
    }

    const primerCapitulo = capitulos.find(capitulo => capitulo.titulo && !capitulo.titulo.toLowerCase().includes('inicio'));
    if (primerCapitulo) {
      return primerCapitulo.titulo.toLowerCase();
    }

    return fragmentos[0]?.texto
      ? this.recortarTexto(fragmentos[0].texto, 90).toLowerCase()
      : 'el contenido principal del video';
  }

  private obtenerDesarrolloResumen(
    capitulos: CapituloClase[],
    conceptos: ConceptoClave[],
    fragmentos: Array<FragmentoVideo | { texto: string; tiempoInicio: number; tiempoFin: number }>
  ): string {
    const capitulosRepresentativos = capitulos
      .filter(capitulo => capitulo.titulo && capitulo.descripcion)
      .slice(0, 4);

    if (capitulosRepresentativos.length > 0) {
      return capitulosRepresentativos
        .map(capitulo => `${capitulo.titulo.toLowerCase()} (${this.recortarTexto(capitulo.descripcion, 80).toLowerCase()})`)
        .join('; ');
    }

    if (conceptos.length > 0) {
      return conceptos
        .slice(0, 4)
        .map(concepto => `${concepto.nombre.toLowerCase()}: ${this.recortarTexto(concepto.definicion, 70).toLowerCase()}`)
        .join('; ');
    }

    return this.seleccionarFragmentosDistribuidos(fragmentos, 3)
      .map(fragmento => this.recortarTexto(fragmento.texto, 85).toLowerCase())
      .join('; ') || 'los bloques principales de la clase';
  }

  private obtenerCierreResumen(
    capitulos: CapituloClase[],
    fragmentos: Array<FragmentoVideo | { texto: string; tiempoInicio: number; tiempoFin: number }>
  ): string {
    const capituloFinal = [...capitulos].reverse()
      .find(capitulo => capitulo.descripcion || capitulo.titulo);

    if (capituloFinal) {
      return this.recortarTexto((capituloFinal.descripcion || capituloFinal.titulo).toLowerCase(), 110);
    }

    const ultimoFragmento = fragmentos.at(-1);
    if (ultimoFragmento?.texto) {
      return this.recortarTexto(ultimoFragmento.texto.toLowerCase(), 110);
    }

    return 'una conclusion general del tema tratado';
  }

  private seleccionarFragmentosDistribuidos(
    fragmentos: Array<FragmentoVideo | { texto: string; tiempoInicio: number; tiempoFin: number }>,
    cantidad: number
  ): Array<FragmentoVideo | { texto: string; tiempoInicio: number; tiempoFin: number }> {
    if (fragmentos.length <= cantidad) return fragmentos;

    const seleccionados: Array<FragmentoVideo | { texto: string; tiempoInicio: number; tiempoFin: number }> = [];
    const paso = (fragmentos.length - 1) / (cantidad - 1);
    for (let i = 0; i < cantidad; i++) {
      seleccionados.push(fragmentos[Math.round(i * paso)]);
    }
    return seleccionados;
  }

  private programarSeguimientoYoutube(): void {
    this.destruirYoutubePlayer();
    setTimeout(() => this.inicializarSeguimientoYoutube(), 250);
  }

  private inicializarSeguimientoYoutube(): void {
    if (!this.videoSeleccionado || !this.youtubeIdClase) return;

    this.cargarYouTubeIframeApi(() => {
      const iframe = document.getElementById(this.idIframeClase);
      if (!iframe || !window.YT?.Player) return;

      this.destruirYoutubePlayer();
      this.youtubePlayer = new window.YT.Player(this.idIframeClase, {
        events: {
          onStateChange: (evento: { data: number }) => {
            if (evento.data === window.YT.PlayerState.ENDED && this.videoSeleccionado) {
              this.marcarClaseCompletada(true);
            }
          }
        }
      });
    });
  }

  private cargarYouTubeIframeApi(callback: () => void): void {
    if (window.YT?.Player) {
      callback();
      return;
    }

    const callbackPrevio = window.onYouTubeIframeAPIReady;
    window.onYouTubeIframeAPIReady = () => {
      callbackPrevio?.();
      callback();
    };

    if (!document.querySelector('script[src="https://www.youtube.com/iframe_api"]')) {
      const script = document.createElement('script');
      script.src = 'https://www.youtube.com/iframe_api';
      script.async = true;
      document.head.appendChild(script);
    }
  }

  private destruirYoutubePlayer(): void {
    if (this.youtubePlayer?.destroy) {
      this.youtubePlayer.destroy();
    }
    this.youtubePlayer = null;
  }

  private marcarClaseCompletada(completado: boolean): void {
    if (!this.videoSeleccionado) return;
    this.persistirMetadata({ completado });
  }

  private persistirMetadata(metadata: VideoMetadata): void {
    if (!this.videoSeleccionado) return;

    const idVideo = this.videoSeleccionado.id;
    this.aplicarMetadataLocal(idVideo, metadata);

    this.transcripcionServicio.actualizarMetadata(idVideo, metadata).subscribe({
      next: (videoActualizado) => {
        this.sincronizarVideoLocal(videoActualizado);
        this.cd.detectChanges();
      },
      error: () => {
        this.cd.detectChanges();
      }
    });
  }

  private aplicarMetadataLocal(idVideo: number, metadata: VideoMetadata): void {
    const aplicar = (video: VideoResumen): VideoResumen => ({
      ...video,
      asignatura: metadata.asignatura ?? video.asignatura,
      profesor: metadata.profesor ?? video.profesor,
      fechaClase: metadata.fechaClase ?? video.fechaClase,
      completado: metadata.completado ?? video.completado
    });

    if (this.videoSeleccionado?.id === idVideo) {
      this.videoSeleccionado = aplicar(this.videoSeleccionado);
      this.guardarVideoEnCache(this.videoSeleccionado);
    }
    this.historial = this.historial.map(video => video.id === idVideo ? aplicar(video) : video);
  }

  private sincronizarVideoLocal(videoActualizado: VideoResumen): void {
    if (this.videoSeleccionado?.id === videoActualizado.id) {
      this.videoSeleccionado = videoActualizado;
      this.asignaturaClase = videoActualizado.asignatura || 'Sin asignatura';
      this.profesorClase = videoActualizado.profesor || 'Profesor pendiente';
      this.fechaClase = videoActualizado.fechaClase || this.normalizarFecha(videoActualizado.fechaCreacion);
    }
    this.historial = this.historial.map(video => video.id === videoActualizado.id ? videoActualizado : video);
    this.guardarVideoEnCache(videoActualizado);
    this.actualizarOpcionesMetadataDesdeVideos([videoActualizado]);
  }

  private guardarVideoEnCache(video: VideoResumen): void {
    const claseActual = this.cacheClases.get(video.id);
    this.cacheClases.set(video.id, {
      ...claseActual,
      video
    });
  }

  private guardarDetallesEnCache(idVideo: number, detalles: Partial<Pick<ClaseEnCache, 'fragmentos' | 'capitulos' | 'conceptos'>>): void {
    const claseActual = this.cacheClases.get(idVideo);
    if (!claseActual) return;
    this.cacheClases.set(idVideo, {
      ...claseActual,
      ...detalles
    });
  }

  private aplicarDetallesCacheados(idVideo: number): void {
    const claseCacheada = this.cacheClases.get(idVideo);
    if (!claseCacheada) return;

    if (claseCacheada.fragmentos) this.fragmentos = claseCacheada.fragmentos;
    if (claseCacheada.capitulos) this.capitulos = claseCacheada.capitulos;
    if (claseCacheada.conceptos) this.conceptos = claseCacheada.conceptos;

    if (claseCacheada.fragmentos && claseCacheada.capitulos && claseCacheada.conceptos) {
      this.cargandoFragmentos = false;
    }
  }

  private prepararCargaClaseVacia(): void {
    this.videoSeleccionado = null;
    this.fragmentos = [];
    this.capitulos = [];
    this.conceptos = [];
    this.resultadosBusqueda = [];
    this.respuestaRag = null;
    this.pregunta = '';
    this.preguntaEnviada = '';
    this.errorBusqueda = '';
    this.editandoTitulo = false;
    this.fuentesExpandidas = false;
    this.cargandoFragmentos = false;
    this.destruirYoutubePlayer();
  }

  private programarLoadingClase(idVideo: number): void {
    this.limpiarTemporizadoresCargaClase();

    this.temporizadorSkeletonClase = setTimeout(() => {
      if (this.idClaseCargando !== idVideo || !this.cargandoClase) return;
      this.mostrandoSkeletonClase = true;
      this.cd.detectChanges();
    }, 150);

    this.temporizadorMensajeClase = setTimeout(() => {
      if (this.idClaseCargando !== idVideo || !this.cargandoClase) return;
      this.mostrandoMensajeCargaClase = true;
      this.cd.detectChanges();
    }, 700);
  }

  private limpiarTemporizadoresCargaClase(): void {
    if (this.temporizadorSkeletonClase !== null) {
      clearTimeout(this.temporizadorSkeletonClase);
      this.temporizadorSkeletonClase = null;
    }
    if (this.temporizadorMensajeClase !== null) {
      clearTimeout(this.temporizadorMensajeClase);
      this.temporizadorMensajeClase = null;
    }
  }

  private cancelarCargaClasePendiente(): void {
    this.idClaseCargando = null;
    this.cargandoClase = false;
    this.mostrandoSkeletonClase = false;
    this.mostrandoMensajeCargaClase = false;
    this.errorCargaClase = '';
    this.limpiarTemporizadoresCargaClase();
  }

  private actualizarOpcionesMetadataDesdeVideos(videos: VideoResumen[]): void {
    const asignaturas = videos
      .map(video => video.asignatura)
      .filter((valor): valor is string => !!valor?.trim());
    const profesores = videos
      .map(video => video.profesor)
      .filter((valor): valor is string => !!valor?.trim());

    this.asignaturasDisponibles = this.fusionarOpciones(this.asignaturasDisponibles, asignaturas);
    this.profesoresDisponibles = this.fusionarOpciones(this.profesoresDisponibles, profesores);
  }

  private fusionarOpciones(opcionesActuales: string[], nuevasOpciones: string[]): string[] {
    return nuevasOpciones.reduce((opciones, opcion) => {
      const valor = this.normalizarOpcion(opcion);
      if (!valor || this.existeOpcion(opciones, valor)) return opciones;
      return [...opciones, valor];
    }, opcionesActuales);
  }

  private aplicarRutaActual(): void {
    const ruta = window.location.hash.replace(/^#\/?/, '');
    const [vista, id] = ruta.split('/');

    if (vista === 'clase' && id) {
      const idVideo = Number(id);
      if (Number.isFinite(idVideo)) {
        this.cargarClaseDesdeRuta(idVideo);
      } else {
        this.irAHome();
      }
      return;
    }

    if (vista === 'cursos') {
      this.cancelarCargaClasePendiente();
      this.vistaActual = 'cursos';
      return;
    }

    if (vista === 'historial') {
      this.cancelarCargaClasePendiente();
      this.vistaActual = 'historial';
      return;
    }

    this.cancelarCargaClasePendiente();
    this.vistaActual = 'home';
  }

  private cargarClaseDesdeRuta(idVideo: number, forzarRecarga = false, preview?: VideoResumen): void {
    if (!forzarRecarga && this.videoSeleccionado?.id === idVideo && this.vistaActual === 'clase') return;

    const claseCacheada = this.cacheClases.get(idVideo);
    const videoLocal = preview ?? claseCacheada?.video;
    const tienePreview = !!videoLocal;

    this.vistaActual = 'clase';
    this.cargandoClase = !tienePreview;
    this.mostrandoSkeletonClase = false;
    this.mostrandoMensajeCargaClase = false;
    this.errorCargaClase = '';
    this.idClaseCargando = idVideo;
    this.chatColapsado = false;

    if (videoLocal) {
      this.seleccionarVideo(videoLocal, false, false);
      this.aplicarDetallesCacheados(idVideo);
    } else {
      this.prepararCargaClaseVacia();
      this.programarLoadingClase(idVideo);
      this.cd.detectChanges();
    }

    if (claseCacheada?.video && claseCacheada.fragmentos && claseCacheada.capitulos && claseCacheada.conceptos) {
      this.idClaseCargando = null;
      return;
    }

    this.transcripcionServicio.obtenerVideo(idVideo).subscribe({
      next: (video) => {
        if (!this.esHashClase(idVideo) || this.idClaseCargando !== idVideo) return;
        this.idClaseCargando = null;
        this.limpiarTemporizadoresCargaClase();
        this.seleccionarVideo(video, false, !tienePreview);
        if (tienePreview) {
          this.aplicarDetallesCacheados(idVideo);
          this.cargarDetallesClase(idVideo);
        }
      },
      error: () => {
        if (!this.esHashClase(idVideo) || this.idClaseCargando !== idVideo) return;
        this.idClaseCargando = null;
        this.limpiarTemporizadoresCargaClase();
        this.cargandoClase = false;
        this.mostrandoSkeletonClase = false;
        this.mostrandoMensajeCargaClase = false;
        this.errorCargaClase = 'No se pudo cargar la clase solicitada.';
        this.cd.detectChanges();
      }
    });
  }

  private actualizarRuta(hash: string): void {
    if (window.location.hash === hash) return;
    history.pushState(null, '', hash);
  }

  private obtenerVistaInicial(): VistaApp {
    const ruta = window.location.hash.replace(/^#\/?/, '');
    if (ruta.startsWith('clase/')) return 'clase';
    if (ruta === 'cursos') return 'cursos';
    if (ruta === 'historial') return 'historial';
    return 'home';
  }

  private esRutaClaseActual(): boolean {
    return window.location.hash.replace(/^#\/?/, '').startsWith('clase/');
  }

  private esHashClase(idVideo: number): boolean {
    return window.location.hash.replace(/^#\/?/, '') === `clase/${idVideo}`;
  }

  private filtrarOpciones(opciones: string[], busqueda: string): string[] {
    const termino = this.normalizarOpcion(busqueda).toLowerCase();
    if (!termino) return opciones;
    return opciones.filter(opcion => opcion.toLowerCase().includes(termino));
  }

  private puedeCrearOpcion(opciones: string[], busqueda: string): boolean {
    const valor = this.normalizarOpcion(busqueda);
    return valor.length > 0 && !this.existeOpcion(opciones, valor);
  }

  private existeOpcion(opciones: string[], valor: string): boolean {
    return opciones.some(opcion => opcion.toLowerCase() === valor.toLowerCase());
  }

  private normalizarOpcion(valor: string): string {
    return valor.trim().replace(/\s+/g, ' ');
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
