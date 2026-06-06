import { ChangeDetectorRef, Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { TranscripcionServicio, VideoMetadata, SolicitudAsignatura } from './servicios/transcripcion.servicio';
import { RespuestaTranscripcion } from './modelos/respuesta-transcripcion';
import { VideoResumen } from './modelos/video-resumen';
import { FragmentoVideo } from './modelos/fragmento-video';
import { ResultadoBusqueda } from './modelos/resultado-busqueda';
import { EstadoTrabajo, FaseTrabajo } from './modelos/estado-trabajo';
import { RespuestaRag } from './modelos/respuesta-rag';
import { MensajeChat } from './modelos/mensaje-chat';
import { Asignatura } from './modelos/asignatura';
import { AsignaturaDetalle } from './modelos/asignatura-detalle';
import { ResultadoBusquedaAsignatura } from './modelos/resultado-busqueda-asignatura';
import { ConceptoCurso } from './modelos/concepto-curso';
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

type VistaApp = 'home' | 'clase' | 'cursos' | 'cursos-detalle' | 'historial';
type SelectorMetadato = 'asignatura' | 'profesor' | null;

// Describe desde dónde se abrió la clase, para que el botón atrás vuelva al sitio correcto
type OrigenClase =
  | { tipo: 'home' }
  | { tipo: 'historial' }
  | { tipo: 'asignatura'; idAsignatura: number };

interface CapituloClase {
  id?: number | null;
  titulo: string;
  descripcion: string;
  tiempo: number;
  tiempoFin: number;
  origen: string;
}

interface ConceptoClave {
  id?: number | null;
  nombre: string;
  definicion: string;
  tiempo: number;
}

// Resultados de búsqueda semántica agrupados por clase/vídeo
interface GrupoBusquedaClase {
  idClase: number;
  tituloClase: string;
  youtubeId: string;
  coincidencias: number;
  mejorSimilitud: number;
  duracion: string | null;
  fecha: string | null;
  fragmentos: ResultadoBusquedaAsignatura[];
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

  // ── Chat conversacional (memoria corta SOLO en frontend, no se persiste) ─────
  // Historial visible de la conversación. Se reinicia al salir de la clase.
  protected mensajesChat: MensajeChat[] = [];
  // Cuántos turnos previos se envían al backend como memoria corta.
  private readonly MAX_TURNOS_MEMORIA = 8;
  // Memoria ligera para resolver referencias implícitas ("el móvil", "ese reloj", "y después?").
  protected contextoConversacion: {
    ultimaEntidad: string | null;
    ultimoTema: string | null;
    ultimosMensajes: string[];
    ultimosConceptos: string[];
    ultimosTimestamps: number[];
  } = {
    ultimaEntidad: null,
    ultimoTema: null,
    ultimosMensajes: [],
    ultimosConceptos: [],
    ultimosTimestamps: []
  };

  @ViewChild('hiloChat') private hiloChatRef?: ElementRef<HTMLDivElement>;

  protected faseActual: FaseTrabajo | null = null;

  protected menuTarjetaAbiertoId: number | null = null;
  protected confirmandoEliminarId: number | null = null;
  protected eliminandoId: number | null = null;

  // ── Asignaturas ────────────────────────────────────────────────────────────
  protected asignaturas: Asignatura[] = [];
  protected cargandoAsignaturas = false;
  protected asignaturaDetalle: AsignaturaDetalle | null = null;
  protected cargandoDetalleAsignatura = false;
  protected errorAsignatura = '';

  // Modal nueva asignatura
  protected modalNuevaAsignatura = false;
  protected nuevaAsignaturaNombre = '';
  protected nuevaAsignaturaDescripcion = '';
  protected nuevaAsignaturaProfesor = '';
  protected guardandoAsignatura = false;
  protected errorGuardarAsignatura = '';

  // Asignatura sobre la que actúan los modales (sirve tanto para la página de detalle
  // como para las tarjetas del grid de Mis Cursos).
  protected asignaturaObjetivo: Asignatura | null = null;

  // Modal editar asignatura
  protected modalEditarAsignatura = false;
  protected editarAsignaturaNombre = '';
  protected editarAsignaturaDescripcion = '';
  protected editarAsignaturaProfesor = '';
  protected guardandoEditarAsignatura = false;
  protected errorEditarAsignatura = '';

  // Modal eliminar asignatura
  protected modalEliminarAsignatura = false;
  protected eliminarAsignaturaConfirmacion = '';
  protected eliminandoAsignatura = false;
  protected errorEliminarAsignatura = '';

  // Menú contextual de tres puntos en las tarjetas del grid de Mis Cursos
  protected menuAsignaturaCardId: number | null = null;

  // Búsqueda dentro de asignatura
  protected preguntaAsignatura = '';
  protected resultadosBusquedaAsignatura: ResultadoBusquedaAsignatura[] = [];
  protected cargandoBusquedaAsignatura = false;
  protected errorBusquedaAsignatura = '';
  // Acordeón: idClase del grupo de resultados expandido (null = todos contraídos)
  protected grupoBusquedaExpandido: number | null = null;

  // ── Conceptos del curso (sección de detalle de asignatura) ───────────────
  protected conceptosCurso: ConceptoCurso[] = [];
  protected cargandoConceptosCurso = false;
  protected filtroConceptosCurso = '';
  // Ver más / ver menos: por defecto contraído a dos filas (no se persiste)
  protected conceptosCursoExpandido = false;
  protected conceptosCursoDesborda = false;

  // Modal de estudio de un concepto + nota personal global
  protected modalConceptoCurso = false;
  protected conceptoCursoActivo: ConceptoCurso | null = null;
  protected notaConcepto = '';
  protected cargandoNotaConcepto = false;
  protected guardandoNotaConcepto = false;
  protected notaConceptoGuardada = false;
  protected errorNotaConcepto = '';

  // Edición / eliminación del concepto (dentro del modal)
  protected editandoConcepto = false;
  protected editarConceptoNombre = '';
  protected editarConceptoDefinicion = '';
  protected guardandoEdicionConcepto = false;
  protected errorEdicionConcepto = '';
  protected confirmandoEliminarConcepto = false;
  protected eliminandoConcepto = false;

  // Menú contextual de asignatura (tres puntos)
  protected menuAsignaturaAbierto = false;

  // Origen de navegación de la clase abierta: decide a dónde vuelve el botón atrás
  protected origenClase: OrigenClase | null = null;

  // Caché en memoria de asignaturas (no persistente — se pierde al recargar la página)
  private cacheListaAsignaturas: Asignatura[] | null = null;
  private cacheDetalleAsignatura = new Map<number, AsignaturaDetalle>();

  // Timers para disimular la carga del detalle de asignatura (igual que en clase)
  protected mostrandoSkeletonAsignatura = false;
  protected mostrandoMensajeAsignatura = false;
  private temporizadorSkeletonAsignatura: ReturnType<typeof setTimeout> | null = null;
  private temporizadorMensajeAsignatura: ReturnType<typeof setTimeout> | null = null;

  protected editandoTitulo = false;
  protected tituloEdicion = '';
  protected guardandoTitulo = false;
  protected tiempoProcesando = 0;
  protected mostrarAviso60s = false;
  protected mostrarAviso180s = false;
  protected trabajoActivoId: string | null = null;
  protected asignaturaClase = 'Sin asignatura';
  // La asignatura actual es solo una sugerencia automática (metadato visual).
  protected asignaturaSugerida = false;
  protected profesorClase = 'Profesor pendiente';
  protected fechaClase = this.obtenerFechaActual();
  protected tiempoInicioReproductor = 0;
  // URL del iframe de YouTube ya sanitizada. Es un CAMPO (no getter) para no regenerar
  // un objeto nuevo en cada ciclo de detección de cambios, lo que recargaba el iframe.
  protected urlEmbedClase: SafeResourceUrl | null = null;
  // Controla el montaje del iframe. Se pone false→true para forzar un remount LIMPIO
  // (Angular gestiona el nodo) en vez de @for, que chocaba con la IFrame API (NG0956).
  protected mostrarIframe = false;
  // Id del elemento iframe para que la YouTube IFrame API pueda engancharse a él
  protected readonly idIframeClase = 'comprendia-class-player';
  // Referencia al reproductor de la IFrame API (para leer el tiempo y hacer seek)
  private youtubePlayer: any = null;
  private playerListo = false;
  // Segundo en el que abrir la clase (p.ej. al venir de "Ir al momento" de un concepto)
  private tiempoInicialPendiente: number | null = null;
  protected selectorMetadatoAbierto: SelectorMetadato = null;
  protected busquedaAsignatura = '';
  protected busquedaProfesor = '';
  protected busquedaConceptos = '';
  protected contenidoTratadoAbierto = true;

  // ── Modales de capítulo/concepto manual ──────────────────────────────────
  protected modalCapitulo = false;
  protected capituloEditandoId: number | null = null;
  // Los tiempos se editan en formato mm:ss (texto); se convierten a segundos al enviar
  protected formCapitulo = { titulo: '', descripcion: '', tiempoInicio: '', tiempoFin: '' };
  protected guardandoCapitulo = false;
  protected errorCapitulo = '';

  protected modalConcepto = false;
  protected conceptoEditandoId: number | null = null;
  protected formConcepto = { nombre: '', definicion: '', tiempoInicio: '', tiempoFin: '' };
  protected guardandoConcepto = false;
  protected errorConcepto = '';

  // Menú discreto abierto en un capítulo/concepto concreto (ej. 'cap-12', 'con-5')
  protected menuItemAbierto: string | null = null;

  // Segundo detectado del reproductor al abrir un modal (para el aviso "Timestamp detectado")
  protected tiempoDetectado = 0;

  // Nombres para el selector (derivados de asignaturasObjetos + fallback inicial)
  protected asignaturasDisponibles: string[] = ['Sin asignatura'];
  // Objetos reales del backend — fuente de verdad para IDs
  protected asignaturasObjetos: Asignatura[] = [];
  protected cargandoAsignaturasSelector = false;

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
  private socketProgreso: WebSocket | null = null;
  private socketChat: WebSocket | null = null;
  private intervaloTiempo: ReturnType<typeof setInterval> | null = null;
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
    'DESCARGANDO', 'TRANSCRIBIENDO', 'GUARDANDO', 'EMBEDDINGS', 'ANALIZANDO'
  ];

  // Timeline horizontal de progreso real del análisis (porcentaje según fase del backend)
  readonly FASES_TIMELINE: { fase: FaseTrabajo; label: string; texto: string; pct: number }[] = [
    { fase: 'DESCARGANDO',    label: 'Descarga',      texto: 'Descargando audio del vídeo…',        pct: 15 },
    { fase: 'TRANSCRIBIENDO', label: 'Transcripción', texto: 'Transcribiendo con Whisper…',          pct: 40 },
    { fase: 'GUARDANDO',      label: 'Guardado',      texto: 'Guardando la transcripción…',          pct: 60 },
    { fase: 'EMBEDDINGS',     label: 'Embeddings',    texto: 'Generando vectores semánticos…',       pct: 80 },
    { fase: 'ANALIZANDO',     label: 'Análisis',      texto: 'Generando capítulos y conceptos…',     pct: 95 },
    { fase: 'COMPLETADO',     label: 'Completado',    texto: 'Análisis completado',                  pct: 100 },
  ];

  constructor(
    private transcripcionServicio: TranscripcionServicio,
    private cd: ChangeDetectorRef,
    private sanitizer: DomSanitizer
  ) {}

  ngOnInit(): void {
    this.aplicarRutaActual();
    this.cargarHistorial();
    // Precarga asignaturas para el selector de clase (sin mostrar spinner de la página de cursos)
    this.cargarAsignaturasEnSilencio();
  }

  ngOnDestroy(): void {
    this.detenerPolling();
    this.detenerTemporizador();
    this.limpiarTemporizadoresCargaClase();
    this.limpiarReproductor();
  }

  @HostListener('document:click')
  cerrarSelectorAlClickExterior(): void {
    if (this.selectorMetadatoAbierto) {
      this.cerrarSelectorMetadato();
    }
    if (this.menuTarjetaAbiertoId !== null) {
      this.menuTarjetaAbiertoId = null;
      this.confirmandoEliminarId = null;
      this.cd.detectChanges();
    }
    if (this.menuAsignaturaAbierto) {
      this.menuAsignaturaAbierto = false;
      this.cd.detectChanges();
    }
    if (this.menuAsignaturaCardId !== null) {
      this.menuAsignaturaCardId = null;
      this.cd.detectChanges();
    }
    if (this.menuItemAbierto !== null) {
      this.menuItemAbierto = null;
      this.cd.detectChanges();
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
    this.reiniciarChat();
    this.tiempoInicioReproductor = 0;
    this.faseActual = null;
    // Montar YA el iframe real del vídeo nuevo (id extraído de la URL) para que sea
    // reproducible durante el procesamiento. Nunca hereda el iframe/thumbnail anterior.
    this.tiempoInicialPendiente = null;
    this.actualizarUrlEmbed(false);
    this.iniciarTemporizador();

    this.transcripcionServicio.iniciarProcesamiento({ urlVideo: this.urlVideo }).subscribe({
      next: ({ idTrabajo }) => {
        this.trabajoActivoId = idTrabajo;
        this.iniciarSeguimientoTrabajo(idTrabajo);
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

  // (Re)construye la URL del iframe y recrea el elemento. Se usa al cargar una clase
  // (vídeo nuevo) y como fallback de salto si la IFrame API aún no está lista.
  // Tras recrear el iframe, engancha la YouTube IFrame API para poder leer el tiempo.
  private actualizarUrlEmbed(autoplay: boolean): void {
    const youtubeId = this.youtubeIdClase;

    if (!youtubeId) {
      this.urlEmbedClase = null;
      return;
    }

    const inicio = Math.max(0, Math.floor(this.tiempoInicioReproductor));
    const parametros = new URLSearchParams({
      start: String(inicio),
      enablejsapi: '1',
      rel: '0',
      modestbranding: '1',
      origin: window.location.origin
    });
    if (autoplay) {
      parametros.set('autoplay', '1');
    }

    const urlPlana = `https://www.youtube.com/embed/${youtubeId}?${parametros.toString()}`;
    this.urlEmbedClase = this.sanitizer.bypassSecurityTrustResourceUrl(urlPlana);
    // Remount limpio del iframe gestionado por Angular (no @for) + reenganche del player.
    this.remontarIframe();
  }

  // Fuerza la recreación del <iframe> de forma estable: lo oculta, suelta el player
  // anterior (sin destroy(), que quitaría el nodo a Angular) y lo vuelve a montar.
  private remontarIframe(): void {
    this.mostrarIframe = false;
    this.youtubePlayer = null;
    this.playerListo = false;
    this.cd.detectChanges();
    setTimeout(() => {
      this.mostrarIframe = true;
      this.cd.detectChanges();
      // Una vez el iframe nuevo está en el DOM, engancha la IFrame API
      setTimeout(() => this.inicializarPlayer(), 300);
    }, 0);
  }

  get youtubeIdClase(): string | null {
    return this.videoSeleccionado?.youtubeId
      ?? this.transcripcion?.idVideo
      ?? this.extraerYoutubeId(this.urlVideo);
  }

  get thumbnailClase(): string {
    // hqdefault siempre existe (maxresdefault da 404 en algunos vídeos)
    return this.obtenerThumbnailYoutube(this.youtubeIdClase, 'hqdefault');
  }

  // URL pública para abrir el vídeo en YouTube (fallback si el embed está bloqueado)
  get urlYoutubeClase(): string | null {
    const id = this.youtubeIdClase;
    if (!id) return null;
    const inicio = Math.max(0, Math.floor(this.tiempoInicioReproductor));
    return inicio > 0
      ? `https://www.youtube.com/watch?v=${id}&t=${inicio}s`
      : `https://www.youtube.com/watch?v=${id}`;
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

  // ── Timeline de progreso del análisis ────────────────────────────────────

  get indiceFaseActual(): number {
    if (!this.faseActual) return -1;
    return this.FASES_TIMELINE.findIndex(f => f.fase === this.faseActual);
  }

  // Porcentaje real basado en la fase del backend (5% mientras "prepara")
  get progresoAnalisis(): number {
    const actual = this.FASES_TIMELINE.find(f => f.fase === this.faseActual);
    return actual ? actual.pct : 5;
  }

  // Texto descriptivo de la fase actual (o el error si lo hay)
  get faseActualTexto(): string {
    if (this.error) return this.error;
    const actual = this.FASES_TIMELINE.find(f => f.fase === this.faseActual);
    return actual ? actual.texto : 'Preparando análisis…';
  }

  fasePasada(indice: number): boolean {
    return this.indiceFaseActual > indice;
  }

  faseEsActual(indice: number): boolean {
    return this.indiceFaseActual === indice;
  }

  // Solo capítulos REALES persistidos del vídeo actual. Nunca datos falsos: si no hay
  // análisis todavía (vídeo en procesamiento), devuelve [] y la UI muestra skeleton.
  get capitulosClase(): CapituloClase[] {
    if (this.capitulos.length === 0) return [];
    return this.capitulos
      .map(capitulo => ({
        id: capitulo.id ?? null,
        titulo: capitulo.titulo,
        descripcion: capitulo.descripcion,
        tiempo: capitulo.tiempoInicio,
        tiempoFin: capitulo.tiempoFin,
        origen: capitulo.origen
      }))
      // Orden defensivo por tiempo ascendente (el backend ya ordena, esto blinda la UI)
      .sort((a, b) => (a.tiempo ?? 0) - (b.tiempo ?? 0));
  }

  // Solo conceptos REALES persistidos del vídeo actual.
  get conceptosClave(): ConceptoClave[] {
    if (this.conceptos.length === 0) return [];
    return this.conceptos
      .map(concepto => ({
        id: concepto.id ?? null,
        nombre: concepto.nombre,
        definicion: concepto.definicion,
        tiempo: concepto.tiempoInicio
      }))
      // Orden defensivo por tiempo ascendente
      .sort((a, b) => (a.tiempo ?? 0) - (b.tiempo ?? 0));
  }

  // True cuando ya existe análisis real (capítulos o conceptos del vídeo actual)
  get analisisDisponible(): boolean {
    return this.capitulos.length > 0 || this.conceptos.length > 0;
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
    // Prioridad: el resumen generado por IA y guardado en backend (Video.resumen).
    const guardado = this.videoSeleccionado?.resumen;
    if (guardado && guardado.trim()) {
      return guardado.trim();
    }

    // Respaldo (vídeos antiguos sin resumen de IA): síntesis local a partir de capítulos/conceptos.
    const fragmentos = this.fragmentosClase;
    const capitulos = this.capitulosClase;
    const conceptos = this.conceptosClave;

    // Sin datos reales todavía: vacío (la UI muestra skeleton), nunca texto provisional falso
    if (fragmentos.length === 0 && capitulos.length === 0 && conceptos.length === 0) {
      return '';
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
    this.asignaturaSugerida = video.asignaturaSugerida ?? false;
    this.profesorClase = video.profesor || 'Profesor pendiente';
    this.actualizarOpcionesMetadataDesdeVideos([video]);
    this.fechaClase = video.fechaClase || this.normalizarFecha(video.fechaCreacion);
    this.fragmentos = [];
    this.capitulos = [];
    this.conceptos = [];
    this.resultadosBusqueda = [];
    this.reiniciarChat();
    this.editandoTitulo = false;
    this.cargandoFragmentos = true;
    if (actualizarUrl) {
      this.actualizarRuta('#/clase/' + video.id);
    }
    // Cargar el reproductor. Si se venía con un segundo pendiente ("Ir al momento"),
    // posicionar el vídeo ahí; si no, desde el inicio.
    this.tiempoInicioReproductor = this.tiempoInicialPendiente ?? 0;
    this.tiempoInicialPendiente = null;
    this.actualizarUrlEmbed(false);

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

  enviarMensajeChat(): void {
    if (!this.pregunta.trim() || !this.videoSeleccionado || this.cargandoBusqueda) return;
    const preguntaActual = this.pregunta.trim();
    const idVideo = this.videoSeleccionado.id;

    // Se añade el mensaje del usuario al hilo visible (no desaparece nada de lo anterior).
    this.mensajesChat.push({ rol: 'user', contenido: preguntaActual, timestamp: Date.now() });
    this.contextoConversacion.ultimosMensajes.push(preguntaActual);

    // Memoria corta: se envían los últimos N turnos previos (sin contar el mensaje recién añadido).
    const historial = this.mensajesChat
      .slice(0, -1)
      .slice(-this.MAX_TURNOS_MEMORIA)
      .map((m) => ({ rol: m.rol, contenido: m.contenido }));
    const entidad = this.contextoConversacion.ultimaEntidad;

    this.pregunta = '';
    this.cargandoBusqueda = true;
    this.errorBusqueda = '';
    this.desplazarChatAlFinal();

    // Intentar por WebSocket (respuesta en streaming). Si falla, se cae al HTTP.
    let url: string;
    try {
      url = this.transcripcionServicio.urlWebSocketChat(idVideo);
    } catch {
      this.enviarMensajeChatHttp(idVideo, preguntaActual, historial, entidad);
      return;
    }
    let socket: WebSocket;
    try {
      socket = new WebSocket(url);
    } catch {
      this.enviarMensajeChatHttp(idVideo, preguntaActual, historial, entidad);
      return;
    }
    this.cerrarSocketChat();
    this.socketChat = socket;

    let mensajeAsistente: MensajeChat | null = null;
    let recibioToken = false;
    let terminado = false;

    socket.onopen = () => {
      socket.send(JSON.stringify({ pregunta: preguntaActual, historial, entidadReciente: entidad }));
    };

    socket.onmessage = (evento) => {
      let datos: { tipo: string; contenido?: string; fuentes?: ResultadoBusqueda[]; mensaje?: string };
      try {
        datos = JSON.parse(evento.data);
      } catch {
        return;
      }
      if (datos.tipo === 'token') {
        if (!mensajeAsistente) {
          // Primera "palabra": se crea la burbuja del asistente que se irá rellenando.
          mensajeAsistente = { rol: 'assistant', contenido: '', timestamp: Date.now() };
          this.mensajesChat.push(mensajeAsistente);
        }
        recibioToken = true;
        mensajeAsistente.contenido += datos.contenido ?? '';
        this.cd.detectChanges();
        this.desplazarChatAlFinal();
      } else if (datos.tipo === 'fin') {
        terminado = true;
        if (mensajeAsistente) {
          mensajeAsistente.fuentes = datos.fuentes ?? [];
          this.actualizarContextoConversacion(preguntaActual,
            { respuesta: mensajeAsistente.contenido, fuentes: mensajeAsistente.fuentes ?? [] });
        }
        this.cargandoBusqueda = false;
        this.cd.detectChanges();
        this.desplazarChatAlFinal();
        this.cerrarSocketChat();
      } else if (datos.tipo === 'error') {
        terminado = true;
        this.errorBusqueda = datos.mensaje ?? 'Error al generar la respuesta';
        this.cargandoBusqueda = false;
        this.cd.detectChanges();
        this.cerrarSocketChat();
      }
    };

    const alFallar = () => {
      if (terminado) return;
      this.cerrarSocketChat();
      if (recibioToken) {
        // Se cortó a mitad: damos por terminado con lo que llegó.
        this.cargandoBusqueda = false;
        this.cd.detectChanges();
      } else {
        // No llegó nada: probamos por HTTP.
        this.enviarMensajeChatHttp(idVideo, preguntaActual, historial, entidad);
      }
    };
    socket.onerror = alFallar;
    socket.onclose = alFallar;
  }

  // Alternativa por HTTP (sin streaming) si el WebSocket no está disponible.
  private enviarMensajeChatHttp(
    idVideo: number,
    preguntaActual: string,
    historial: { rol: string; contenido: string }[],
    entidad: string | null
  ): void {
    this.transcripcionServicio.conversar(idVideo, preguntaActual, historial, entidad).subscribe({
      next: (respuesta) => {
        this.mensajesChat.push({
          rol: 'assistant',
          contenido: respuesta.respuesta,
          fuentes: respuesta.fuentes,
          timestamp: Date.now()
        });
        this.actualizarContextoConversacion(preguntaActual, respuesta);
        this.cargandoBusqueda = false;
        this.cd.detectChanges();
        this.desplazarChatAlFinal();
      },
      error: (err) => {
        this.errorBusqueda = err.error?.error ?? 'Error al generar la respuesta';
        this.cargandoBusqueda = false;
        this.cd.detectChanges();
        this.desplazarChatAlFinal();
      }
    });
  }

  private cerrarSocketChat(): void {
    if (this.socketChat) {
      const socket = this.socketChat;
      this.socketChat = null;
      socket.onopen = null;
      socket.onmessage = null;
      socket.onerror = null;
      socket.onclose = null;
      try { socket.close(); } catch { /* ya cerrado */ }
    }
  }

  /** Reinicia toda la conversación (historial visible + memoria corta). Al salir de la clase. */
  private reiniciarChat(): void {
    this.cerrarSocketChat();
    this.mensajesChat = [];
    this.contextoConversacion = {
      ultimaEntidad: null,
      ultimoTema: null,
      ultimosMensajes: [],
      ultimosConceptos: [],
      ultimosTimestamps: []
    };
    this.respuestaRag = null;
    this.pregunta = '';
    this.preguntaEnviada = '';
    this.errorBusqueda = '';
    this.fuentesExpandidas = false;
  }

  /** Actualiza la memoria ligera tras cada respuesta: entidad, tema, conceptos y timestamps recientes. */
  private actualizarContextoConversacion(pregunta: string, respuesta: RespuestaRag): void {
    // La entidad se busca primero en la respuesta del asistente (suele nombrar el referente),
    // y si no aparece, en la pregunta. Si no se detecta nada nuevo, se conserva la anterior
    // (así "el móvil" sigue apuntando a "iPhone 17 Pro Max").
    const entidad = this.extraerEntidad(respuesta.respuesta) ?? this.extraerEntidad(pregunta);
    if (entidad) {
      this.contextoConversacion.ultimaEntidad = entidad;
      this.contextoConversacion.ultimoTema = entidad;
      this.contextoConversacion.ultimosConceptos.push(entidad);
      this.contextoConversacion.ultimosConceptos =
        this.contextoConversacion.ultimosConceptos.slice(-5);
    }

    this.contextoConversacion.ultimosMensajes.push(respuesta.respuesta);
    this.contextoConversacion.ultimosMensajes =
      this.contextoConversacion.ultimosMensajes.slice(-10);

    if (respuesta.fuentes?.length) {
      for (const f of respuesta.fuentes) {
        this.contextoConversacion.ultimosTimestamps.push(f.tiempoInicio);
      }
      this.contextoConversacion.ultimosTimestamps =
        this.contextoConversacion.ultimosTimestamps.slice(-10);
    }
  }

  /**
   * Heurística ligera para detectar la entidad/tema principal de un texto: la secuencia más
   * larga de palabras "relevantes" (con mayúscula inicial o interna como iPhone, o cifras de
   * modelo) que contenga al menos una con mayúscula. Captura "iPhone 17 Pro Max", "Apple Watch".
   */
  private extraerEntidad(texto: string | null | undefined): string | null {
    if (!texto) return null;
    // Los signos de fin de frase (. ! ? ; :) cortan: una entidad no cruza un punto.
    const conCortes = texto.replace(/[.!?¡¿;:\n]+/g, ' | ').replace(/[,()"]/g, ' ');
    const palabras = conCortes.split(/\s+/).filter((p) => p.length > 0);
    const tieneMayuscula = (p: string) => /[A-ZÁÉÍÓÚÑ]/.test(p);
    const esModelo = (p: string) => /^[0-9]+[a-zA-Z]*$/.test(p) || /^[a-z]*[A-Z]/.test(p);
    const esRelevante = (p: string) => tieneMayuscula(p) || esModelo(p);
    // Palabras que, aun con mayúscula, NO son entidades (conectores, artículos, afirmaciones…).
    const parada = new Set(['El', 'La', 'Los', 'Las', 'Un', 'Una', 'En', 'De', 'Del', 'Y', 'O', 'U',
      'Su', 'Sus', 'Si', 'Sí', 'No', 'Este', 'Esta', 'Esto', 'Ese', 'Esa', 'Eso', 'Por', 'Para',
      'Con', 'Que', 'Qué', 'Cuando', 'Cuándo', 'Donde', 'Dónde', 'Como', 'Cómo', 'A', 'Al', 'Se',
      'Lo', 'Le', 'Me', 'Te', 'Pero', 'Más', 'Muy', 'Hay', 'Es', 'Son']);

    let mejor: string[] = [];
    let actual: string[] = [];
    const cerrar = () => {
      if (actual.some(tieneMayuscula) && actual.length > mejor.length) {
        mejor = actual;
      }
      actual = [];
    };
    for (const p of palabras) {
      if (p === '|' || parada.has(p) || !esRelevante(p)) {
        cerrar();
      } else {
        actual.push(p);
      }
    }
    cerrar();
    return mejor.length > 0 ? mejor.join(' ') : null;
  }

  /** Desplaza el hilo del chat al último mensaje (auto-scroll al fondo). */
  private desplazarChatAlFinal(): void {
    setTimeout(() => {
      const el = this.hiloChatRef?.nativeElement;
      if (el) {
        el.scrollTop = el.scrollHeight;
      }
    }, 0);
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

  // Genera y descarga la transcripción del vídeo como .txt (cada línea con su minuto).
  descargarTranscripcion(): void {
    if (!this.fragmentos.length) {
      return;
    }
    const titulo = this.videoSeleccionado?.titulo ?? 'Transcripción';
    const enlace = this.youtubeIdClase
      ? `https://www.youtube.com/watch?v=${this.youtubeIdClase}\n`
      : '';
    const cabecera = `Transcripción — ${titulo}\n${enlace}\n`;

    const cuerpo = [...this.fragmentos]
      .sort((a, b) => a.tiempoInicio - b.tiempoInicio)
      .map((f) => `[${this.formatearTiempo(f.tiempoInicio)}] ${(f.texto ?? '').trim()}`)
      .join('\n');

    const blob = new Blob([cabecera + cuerpo + '\n'], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `transcripcion-${this.youtubeIdClase ?? this.videoSeleccionado?.id ?? 'video'}.txt`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
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

  // Convierte un texto mm:ss (o hh:mm:ss, o ss) a segundos. Devuelve null si es inválido.
  // Acepta: "4:26", "04:26", "0:30", "1:02:15", "30".
  private parsearTiempoMMSS(texto: string): number | null {
    const limpio = (texto ?? '').trim();
    if (!limpio) return null;
    if (!/^\d{1,2}(:\d{1,2}){0,2}$/.test(limpio)) return null;

    const partes = limpio.split(':').map(p => parseInt(p, 10));
    if (partes.some(n => Number.isNaN(n))) return null;

    // Cuando hay separadores, los segundos (y minutos si hay horas) deben ser 0-59
    if (partes.length >= 2 && partes[partes.length - 1] > 59) return null;
    if (partes.length === 3 && partes[1] > 59) return null;

    return partes.reduce((acumulado, parte) => acumulado * 60 + parte, 0);
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

  seleccionarAsignatura(nombreAsignatura: string): void {
    this.asignaturaClase = nombreAsignatura;
    // Elección manual: deja de mostrarse como sugerida (el backend la marca como MANUAL).
    this.asignaturaSugerida = false;
    this.cerrarSelectorMetadato();

    // Buscar el objeto real para enviar idAsignatura si existe
    const objeto = this.asignaturasObjetos.find(
      a => a.nombre.toLowerCase() === nombreAsignatura.toLowerCase()
    );

    if (objeto) {
      // Asignatura existente en backend → enviar idAsignatura
      this.persistirMetadata({ idAsignatura: objeto.id });
    } else if (nombreAsignatura === 'Sin asignatura') {
      // Quitar asignación
      this.persistirMetadata({ asignatura: 'Sin asignatura', idAsignatura: null });
    } else {
      // Nombre libre que aún no está en backend → enviar como string
      this.persistirMetadata({ asignatura: nombreAsignatura });
    }
  }

  seleccionarProfesor(profesor: string): void {
    this.profesorClase = profesor;
    this.cerrarSelectorMetadato();
    this.guardarMetadataClase();
  }

  crearAsignaturaDesdeBusqueda(): void {
    const nombre = this.normalizarOpcion(this.busquedaAsignatura);
    if (!nombre) return;

    // Si ya existe en los objetos del backend, simplemente seleccionarla
    const existente = this.asignaturasObjetos.find(
      a => a.nombre.toLowerCase() === nombre.toLowerCase()
    );
    if (existente) {
      this.seleccionarAsignatura(existente.nombre);
      return;
    }

    // Crear en backend, luego vincular al vídeo
    const solicitud: SolicitudAsignatura = { nombre };
    this.transcripcionServicio.crearAsignatura(solicitud).subscribe({
      next: (nueva) => {
        // Añadir al listado local sin recargar todo
        this.asignaturasObjetos = [...this.asignaturasObjetos, nueva];
        this.sincronizarNombresSelector();
        this.asignaturas = [...this.asignaturas, nueva]; // actualizar vista Mis Cursos
        // Vincular el vídeo actual a la nueva asignatura
        this.asignaturaClase = nueva.nombre;
        this.cerrarSelectorMetadato();
        this.persistirMetadata({ idAsignatura: nueva.id });
        this.cd.detectChanges();
      },
      error: () => {
        // Fallback: guardar como string si el backend falla
        this.asignaturaClase = nombre;
        this.cerrarSelectorMetadato();
        this.persistirMetadata({ asignatura: nombre });
        this.cd.detectChanges();
      }
    });
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

  // ── Capítulos manuales ───────────────────────────────────────────────────

  abrirAnadirCapitulo(evento?: Event): void {
    evento?.preventDefault();
    evento?.stopPropagation();
    if (!this.videoSeleccionado) return;
    this.capituloEditandoId = null;
    this.errorCapitulo = '';
    // Timestamp real del reproductor (segundo actual de reproducción)
    this.tiempoDetectado = this.obtenerTiempoActualVideo();
    this.formCapitulo = {
      titulo: '',
      descripcion: '',
      tiempoInicio: this.formatearDuracion(this.tiempoDetectado),
      tiempoFin: ''
    };
    this.modalCapitulo = true;
  }

  abrirEditarCapitulo(capitulo: CapituloClase, evento: Event): void {
    evento.stopPropagation();
    if (capitulo.id == null) return;
    this.menuItemAbierto = null;
    this.capituloEditandoId = capitulo.id;
    this.errorCapitulo = '';
    this.formCapitulo = {
      titulo: capitulo.titulo,
      descripcion: capitulo.descripcion,
      tiempoInicio: this.formatearDuracion(Math.max(0, Math.floor(capitulo.tiempo))),
      tiempoFin: capitulo.tiempoFin ? this.formatearDuracion(Math.floor(capitulo.tiempoFin)) : ''
    };
    this.modalCapitulo = true;
  }

  cerrarModalCapitulo(): void {
    this.modalCapitulo = false;
    this.guardandoCapitulo = false;
    this.errorCapitulo = '';
  }

  guardarCapitulo(): void {
    if (!this.videoSeleccionado) return;
    if (!this.formCapitulo.titulo.trim()) {
      this.errorCapitulo = 'El título es obligatorio.';
      return;
    }
    const tiempoInicio = this.parsearTiempoMMSS(this.formCapitulo.tiempoInicio);
    if (tiempoInicio == null) {
      this.errorCapitulo = 'El tiempo de inicio no es válido. Usa el formato mm:ss (por ejemplo 4:26).';
      return;
    }
    let tiempoFin: number | undefined;
    if (this.formCapitulo.tiempoFin.trim()) {
      const fin = this.parsearTiempoMMSS(this.formCapitulo.tiempoFin);
      if (fin == null) {
        this.errorCapitulo = 'El tiempo de fin no es válido. Usa el formato mm:ss o déjalo vacío.';
        return;
      }
      tiempoFin = fin;
    }

    this.guardandoCapitulo = true;
    this.errorCapitulo = '';

    const solicitud = {
      titulo: this.formCapitulo.titulo.trim(),
      descripcion: this.formCapitulo.descripcion.trim() || undefined,
      tiempoInicio,
      tiempoFin
    };
    const idVideo = this.videoSeleccionado.id;

    const peticion = this.capituloEditandoId != null
      ? this.transcripcionServicio.actualizarCapitulo(this.capituloEditandoId, solicitud)
      : this.transcripcionServicio.crearCapitulo(idVideo, solicitud);

    peticion.subscribe({
      next: () => {
        this.cerrarModalCapitulo();
        this.refrescarCapitulos(idVideo);
      },
      error: (err) => {
        this.errorCapitulo = err.error?.error ?? 'No se pudo guardar el capítulo.';
        this.guardandoCapitulo = false;
        this.cd.markForCheck();
      }
    });
  }

  eliminarCapitulo(capitulo: CapituloClase, evento: Event): void {
    evento.stopPropagation();
    if (capitulo.id == null || !this.videoSeleccionado) return;
    this.menuItemAbierto = null;
    const idVideo = this.videoSeleccionado.id;
    this.transcripcionServicio.eliminarCapitulo(capitulo.id).subscribe({
      next: () => this.refrescarCapitulos(idVideo),
      error: () => this.cd.markForCheck()
    });
  }

  // ── Conceptos manuales ───────────────────────────────────────────────────

  abrirAnadirConcepto(evento?: Event): void {
    evento?.preventDefault();
    evento?.stopPropagation();
    if (!this.videoSeleccionado) return;
    this.conceptoEditandoId = null;
    this.errorConcepto = '';
    this.tiempoDetectado = this.obtenerTiempoActualVideo();
    this.formConcepto = {
      nombre: '',
      definicion: '',
      tiempoInicio: this.formatearDuracion(this.tiempoDetectado),
      tiempoFin: ''
    };
    this.modalConcepto = true;
  }

  abrirEditarConcepto(concepto: ConceptoClave, evento: Event): void {
    evento.stopPropagation();
    if (concepto.id == null) return;
    this.menuItemAbierto = null;
    this.conceptoEditandoId = concepto.id;
    this.errorConcepto = '';
    this.formConcepto = {
      nombre: concepto.nombre,
      definicion: concepto.definicion,
      tiempoInicio: this.formatearDuracion(Math.max(0, Math.floor(concepto.tiempo))),
      tiempoFin: ''
    };
    this.modalConcepto = true;
  }

  cerrarModalConcepto(): void {
    this.modalConcepto = false;
    this.guardandoConcepto = false;
    this.errorConcepto = '';
  }

  guardarConcepto(): void {
    if (!this.videoSeleccionado) return;
    if (!this.formConcepto.nombre.trim()) {
      this.errorConcepto = 'El nombre es obligatorio.';
      return;
    }
    const tiempoInicio = this.parsearTiempoMMSS(this.formConcepto.tiempoInicio);
    if (tiempoInicio == null) {
      this.errorConcepto = 'El tiempo de inicio no es válido. Usa el formato mm:ss (por ejemplo 4:26).';
      return;
    }
    let tiempoFin: number | undefined;
    if (this.formConcepto.tiempoFin.trim()) {
      const fin = this.parsearTiempoMMSS(this.formConcepto.tiempoFin);
      if (fin == null) {
        this.errorConcepto = 'El tiempo de fin no es válido. Usa el formato mm:ss o déjalo vacío.';
        return;
      }
      tiempoFin = fin;
    }

    this.guardandoConcepto = true;
    this.errorConcepto = '';

    const solicitud = {
      nombre: this.formConcepto.nombre.trim(),
      definicion: this.formConcepto.definicion.trim() || undefined,
      tiempoInicio,
      tiempoFin
    };
    const idVideo = this.videoSeleccionado.id;

    const peticion = this.conceptoEditandoId != null
      ? this.transcripcionServicio.actualizarConcepto(this.conceptoEditandoId, solicitud)
      : this.transcripcionServicio.crearConcepto(idVideo, solicitud);

    peticion.subscribe({
      next: () => {
        this.cerrarModalConcepto();
        this.refrescarConceptos(idVideo);
      },
      error: (err) => {
        this.errorConcepto = err.error?.error ?? 'No se pudo guardar el concepto.';
        this.guardandoConcepto = false;
        this.cd.markForCheck();
      }
    });
  }

  eliminarConcepto(concepto: ConceptoClave, evento: Event): void {
    evento.stopPropagation();
    if (concepto.id == null || !this.videoSeleccionado) return;
    this.menuItemAbierto = null;
    const idVideo = this.videoSeleccionado.id;
    this.transcripcionServicio.eliminarConcepto(concepto.id).subscribe({
      next: () => this.refrescarConceptos(idVideo),
      error: () => this.cd.markForCheck()
    });
  }

  // ── Menú discreto por ítem ───────────────────────────────────────────────

  alternarMenuItem(clave: string, evento: Event): void {
    evento.stopPropagation();
    this.menuItemAbierto = this.menuItemAbierto === clave ? null : clave;
  }

  // ── Refresco tras crear/editar/borrar (sin recargar la página) ───────────

  private refrescarCapitulos(idVideo: number): void {
    this.transcripcionServicio.obtenerCapitulos(idVideo).subscribe({
      next: (capitulos) => {
        if (this.videoSeleccionado?.id !== idVideo) return;
        this.capitulos = capitulos;
        this.guardarDetallesEnCache(idVideo, { capitulos });
        this.cd.detectChanges();
      },
      error: () => this.cd.markForCheck()
    });
  }

  private refrescarConceptos(idVideo: number): void {
    this.transcripcionServicio.obtenerConceptos(idVideo).subscribe({
      next: (conceptos) => {
        if (this.videoSeleccionado?.id !== idVideo) return;
        this.conceptos = conceptos;
        this.guardarDetallesEnCache(idVideo, { conceptos });
        this.cd.detectChanges();
      },
      error: () => this.cd.markForCheck()
    });
  }

  formatearSimilitud(similitud: number): string {
    return `${Math.round(similitud * 100)}%`;
  }

  obtenerThumbnailYoutube(youtubeId: string | null | undefined, calidad = 'maxresdefault'): string {
    // Sin id no se inventa un vídeo por defecto (antes caía en uno de prueba antiguo)
    const id = youtubeId?.trim() || this.youtubeIdClase;
    if (!id) return '';
    return `https://img.youtube.com/vi/${id}/${calidad}.jpg`;
  }

  usarThumbnailFallback(evento: Event, youtubeId: string | null | undefined): void {
    const imagen = evento.target as HTMLImageElement;
    if (imagen.dataset['fallbackAplicado'] === 'true') return;
    imagen.dataset['fallbackAplicado'] = 'true';
    imagen.src = this.obtenerThumbnailYoutube(youtubeId, 'hqdefault');
  }

  // ── Métodos de asignaturas ─────────────────────────────────────────────────

  cargarAsignaturas(): void {
    // Si hay caché, mostrarla al instante y refrescar en segundo plano (sin loader)
    if (this.cacheListaAsignaturas) {
      this.asignaturas = [...this.cacheListaAsignaturas];
      this.asignaturasObjetos = [...this.cacheListaAsignaturas];
      this.sincronizarNombresSelector();
      this.cargandoAsignaturas = false;
      this.cd.markForCheck();
      this.refrescarAsignaturas();
      return;
    }

    // Sin caché: mostrar loader mientras llega la primera respuesta
    this.cargandoAsignaturas = true;
    this.refrescarAsignaturas();
  }

  // Petición real a backend; actualiza caché y UI. No fuerza loader (lo gestiona quien llama).
  private refrescarAsignaturas(): void {
    this.transcripcionServicio.obtenerAsignaturas().subscribe({
      next: (lista) => {
        this.cacheListaAsignaturas = [...lista];
        this.asignaturas = [...lista];
        this.asignaturasObjetos = [...lista];
        this.sincronizarNombresSelector();
        this.cargandoAsignaturas = false;
        this.cd.markForCheck();
      },
      error: () => {
        this.cargandoAsignaturas = false;
        this.cd.markForCheck();
      }
    });
  }

  // Carga silenciosa al iniciar: solo rellena el selector de clase, no toca cargandoAsignaturas
  private cargarAsignaturasEnSilencio(): void {
    this.transcripcionServicio.obtenerAsignaturas().subscribe({
      next: (lista) => {
        this.cacheListaAsignaturas = [...lista];
        this.asignaturasObjetos = [...lista];
        // Solo actualizar this.asignaturas si la página de cursos aún no la ha cargado
        if (this.asignaturas.length === 0) {
          this.asignaturas = [...lista];
        }
        this.sincronizarNombresSelector();
        this.cd.markForCheck();
      },
      error: () => { /* silencioso */ }
    });
  }

  private sincronizarNombresSelector(): void {
    const nombresBackend = this.asignaturasObjetos.map(a => a.nombre);
    // Mantener 'Sin asignatura' siempre al principio
    this.asignaturasDisponibles = ['Sin asignatura', ...nombresBackend];
  }

  cargarDetalleAsignatura(id: number): void {
    this.vistaActual = 'cursos-detalle';
    this.preguntaAsignatura = '';
    this.resultadosBusquedaAsignatura = [];
    this.errorBusquedaAsignatura = '';
    this.errorAsignatura = '';
    this.menuAsignaturaAbierto = false;
    // Reiniciar la sección de conceptos del curso
    this.filtroConceptosCurso = '';
    this.conceptosCursoExpandido = false;
    this.conceptosCursoDesborda = false;
    this.modalConceptoCurso = false;
    this.conceptoCursoActivo = null;
    this.actualizarRuta('#/cursos/' + id);
    // Carga independiente: si falla, no bloquea el resto del detalle
    this.cargarConceptosCurso(id);

    const detalleCacheado = this.cacheDetalleAsignatura.get(id);

    if (detalleCacheado) {
      // Hay caché: mostrar al instante sin loader y refrescar en segundo plano
      this.asignaturaDetalle = { ...detalleCacheado, clases: [...detalleCacheado.clases] };
      this.cargandoDetalleAsignatura = false;
      this.limpiarTemporizadoresAsignatura();
      this.cd.markForCheck();
      this.refrescarDetalleAsignatura(id);
      return;
    }

    // Sin caché: vaciar y mostrar skeleton a los 150 ms (loading inteligente)
    this.asignaturaDetalle = null;
    this.cargandoDetalleAsignatura = true;
    this.programarLoadingAsignatura();
    this.refrescarDetalleAsignatura(id);
  }

  // Petición real al backend; actualiza caché y UI. No fuerza loader.
  private refrescarDetalleAsignatura(id: number): void {
    this.transcripcionServicio.obtenerDetalleAsignatura(id).subscribe({
      next: (detalle) => {
        // Ignorar respuesta si el usuario ya navegó a otra asignatura
        if (this.vistaActual !== 'cursos-detalle') return;
        this.limpiarTemporizadoresAsignatura();
        const copia = { ...detalle, clases: [...detalle.clases] };
        this.cacheDetalleAsignatura.set(id, copia);
        this.asignaturaDetalle = copia;
        this.cargandoDetalleAsignatura = false;
        this.cd.markForCheck();
      },
      error: () => {
        this.limpiarTemporizadoresAsignatura();
        // Solo mostrar error si no había nada cacheado que mostrar
        if (!this.asignaturaDetalle) {
          this.errorAsignatura = 'No se pudo cargar la asignatura.';
        }
        this.cargandoDetalleAsignatura = false;
        this.cd.markForCheck();
      }
    });
  }

  abrirModalNuevaAsignatura(): void {
    this.nuevaAsignaturaNombre = '';
    this.nuevaAsignaturaDescripcion = '';
    this.nuevaAsignaturaProfesor = '';
    this.errorGuardarAsignatura = '';
    this.modalNuevaAsignatura = true;
  }

  cerrarModalNuevaAsignatura(): void {
    this.modalNuevaAsignatura = false;
    this.guardandoAsignatura = false;
    this.errorGuardarAsignatura = '';
  }

  guardarNuevaAsignatura(): void {
    if (!this.nuevaAsignaturaNombre.trim()) {
      this.errorGuardarAsignatura = 'El nombre es obligatorio.';
      return;
    }
    this.guardandoAsignatura = true;
    this.errorGuardarAsignatura = '';

    const solicitud: SolicitudAsignatura = {
      nombre: this.nuevaAsignaturaNombre.trim(),
      descripcion: this.nuevaAsignaturaDescripcion.trim() || undefined,
      profesor: this.nuevaAsignaturaProfesor.trim() || undefined
    };

    this.transcripcionServicio.crearAsignatura(solicitud).subscribe({
      next: (nueva) => {
        this.asignaturas = [nueva, ...this.asignaturas];
        this.asignaturasObjetos = [nueva, ...this.asignaturasObjetos];
        this.cacheListaAsignaturas = [...this.asignaturas]; // mantener caché coherente
        this.sincronizarNombresSelector();
        this.guardandoAsignatura = false;
        this.cerrarModalNuevaAsignatura();
        this.cd.markForCheck();
      },
      error: (err) => {
        this.errorGuardarAsignatura = err.error?.error ?? 'Error al crear la asignatura.';
        this.guardandoAsignatura = false;
        this.cd.markForCheck();
      }
    });
  }

  // ── Editar asignatura ────────────────────────────────────────────────────

  abrirModalEditarAsignatura(origen?: Asignatura): void {
    // Si llega una asignatura del grid se usa esa; si no, la de la página de detalle.
    const objetivo: Asignatura | null = origen
      ?? (this.asignaturaDetalle
        ? {
            id: this.asignaturaDetalle.id,
            nombre: this.asignaturaDetalle.nombre,
            descripcion: this.asignaturaDetalle.descripcion ?? null,
            profesor: this.asignaturaDetalle.profesor ?? null,
            numeroClases: 0,
            fechaActualizacion: null
          }
        : null);
    if (!objetivo) return;
    this.asignaturaObjetivo = objetivo;
    this.editarAsignaturaNombre = objetivo.nombre ?? '';
    this.editarAsignaturaDescripcion = objetivo.descripcion ?? '';
    this.editarAsignaturaProfesor = (objetivo.profesor && objetivo.profesor !== 'Profesor pendiente')
      ? objetivo.profesor : '';
    this.errorEditarAsignatura = '';
    this.modalEditarAsignatura = true;
  }

  cerrarModalEditarAsignatura(): void {
    this.modalEditarAsignatura = false;
    this.guardandoEditarAsignatura = false;
    this.errorEditarAsignatura = '';
    this.asignaturaObjetivo = null;
  }

  guardarEditarAsignatura(): void {
    if (!this.asignaturaObjetivo) return;
    if (!this.editarAsignaturaNombre.trim()) {
      this.errorEditarAsignatura = 'El nombre es obligatorio.';
      return;
    }
    this.guardandoEditarAsignatura = true;
    this.errorEditarAsignatura = '';

    const id = this.asignaturaObjetivo.id;
    const solicitud: SolicitudAsignatura = {
      nombre: this.editarAsignaturaNombre.trim(),
      descripcion: this.editarAsignaturaDescripcion.trim(),
      profesor: this.editarAsignaturaProfesor.trim()
    };

    this.transcripcionServicio.actualizarAsignatura(id, solicitud).subscribe({
      next: (actualizada) => {
        // Actualizar la vista sin recargar
        if (this.asignaturaDetalle?.id === id) {
          this.asignaturaDetalle = {
            ...this.asignaturaDetalle,
            nombre: actualizada.nombre,
            descripcion: actualizada.descripcion,
            profesor: actualizada.profesor
          };
          this.cacheDetalleAsignatura.set(id, this.asignaturaDetalle);
        }
        // Reflejar también en la lista de Mis Cursos y su caché
        this.asignaturas = this.asignaturas.map(a => a.id === id
          ? { ...a, nombre: actualizada.nombre, descripcion: actualizada.descripcion, profesor: actualizada.profesor }
          : a);
        if (this.cacheListaAsignaturas) {
          this.cacheListaAsignaturas = this.cacheListaAsignaturas.map(a => a.id === id
            ? { ...a, nombre: actualizada.nombre, descripcion: actualizada.descripcion, profesor: actualizada.profesor }
            : a);
        }
        this.cerrarModalEditarAsignatura();
        this.cd.markForCheck();
      },
      error: (err) => {
        this.errorEditarAsignatura = err.error?.error ?? 'No se pudo guardar la asignatura.';
        this.guardandoEditarAsignatura = false;
        this.cd.markForCheck();
      }
    });
  }

  abrirModalEliminarAsignatura(origen?: Asignatura): void {
    const objetivo: Asignatura | null = origen
      ?? (this.asignaturaDetalle
        ? {
            id: this.asignaturaDetalle.id,
            nombre: this.asignaturaDetalle.nombre,
            descripcion: this.asignaturaDetalle.descripcion ?? null,
            profesor: this.asignaturaDetalle.profesor ?? null,
            numeroClases: 0,
            fechaActualizacion: null
          }
        : null);
    if (!objetivo) return;
    this.asignaturaObjetivo = objetivo;
    this.eliminarAsignaturaConfirmacion = '';
    this.errorEliminarAsignatura = '';
    this.modalEliminarAsignatura = true;
  }

  cerrarModalEliminarAsignatura(): void {
    this.modalEliminarAsignatura = false;
    this.eliminandoAsignatura = false;
    this.errorEliminarAsignatura = '';
    this.eliminarAsignaturaConfirmacion = '';
    this.asignaturaObjetivo = null;
  }

  confirmarEliminarAsignatura(): void {
    if (!this.asignaturaObjetivo) return;
    if (this.eliminarAsignaturaConfirmacion !== this.asignaturaObjetivo.nombre) {
      this.errorEliminarAsignatura = 'El nombre no coincide exactamente.';
      return;
    }
    this.eliminandoAsignatura = true;
    this.errorEliminarAsignatura = '';

    const idEliminada = this.asignaturaObjetivo.id;
    // Si estábamos viendo el detalle de esta asignatura, tras borrar hay que volver al grid.
    const veniaDeDetalle = this.vistaActual === 'cursos-detalle' && this.asignaturaDetalle?.id === idEliminada;

    this.transcripcionServicio.eliminarAsignatura(
      idEliminada,
      this.eliminarAsignaturaConfirmacion
    ).subscribe({
      next: () => {
        this.asignaturas = this.asignaturas.filter(a => a.id !== idEliminada);
        this.asignaturasObjetos = this.asignaturasObjetos.filter(a => a.id !== idEliminada);
        // Invalidar cachés de la asignatura borrada
        this.cacheListaAsignaturas = [...this.asignaturas];
        this.cacheDetalleAsignatura.delete(idEliminada);
        this.sincronizarNombresSelector();
        this.cerrarModalEliminarAsignatura();
        if (veniaDeDetalle) {
          // Desde la página de detalle: volver al grid de Mis Cursos.
          this.asignaturaDetalle = null;
          this.irACursos();
        }
        // Desde el grid: la card desaparece sola al filtrar this.asignaturas (sin recargar).
        this.cd.markForCheck();
      },
      error: (err) => {
        this.errorEliminarAsignatura = err.error?.error ?? 'Error al eliminar la asignatura.';
        this.eliminandoAsignatura = false;
        this.cd.markForCheck();
      }
    });
  }

  /** Abre/cierra el menú de tres puntos de una tarjeta del grid de Mis Cursos. */
  alternarMenuAsignaturaCard(id: number, evento: Event): void {
    evento.stopPropagation();
    this.menuAsignaturaCardId = this.menuAsignaturaCardId === id ? null : id;
  }

  /** Acción "Editar" desde el menú de una tarjeta del grid. */
  editarAsignaturaDesdeCard(asignatura: Asignatura, evento: Event): void {
    evento.stopPropagation();
    this.menuAsignaturaCardId = null;
    this.abrirModalEditarAsignatura(asignatura);
  }

  /** Acción "Eliminar" desde el menú de una tarjeta del grid. */
  eliminarAsignaturaDesdeCard(asignatura: Asignatura, evento: Event): void {
    evento.stopPropagation();
    this.menuAsignaturaCardId = null;
    this.abrirModalEliminarAsignatura(asignatura);
  }

  buscarEnAsignatura(): void {
    if (!this.preguntaAsignatura.trim() || !this.asignaturaDetalle) return;
    this.cargandoBusquedaAsignatura = true;
    this.errorBusquedaAsignatura = '';
    this.resultadosBusquedaAsignatura = [];
    this.grupoBusquedaExpandido = null;

    this.transcripcionServicio.buscarEnAsignatura(
      this.asignaturaDetalle.id,
      this.preguntaAsignatura.trim()
    ).subscribe({
      next: (resultados) => {
        this.resultadosBusquedaAsignatura = resultados;
        this.cargandoBusquedaAsignatura = false;
        this.cd.detectChanges();
      },
      error: (err) => {
        this.errorBusquedaAsignatura = err.error?.error ?? 'Error en la búsqueda.';
        this.cargandoBusquedaAsignatura = false;
        this.cd.detectChanges();
      }
    });
  }

  // Agrupa los resultados planos de búsqueda por clase/vídeo (una card por clase)
  get resultadosBusquedaAgrupados(): GrupoBusquedaClase[] {
    const mapa = new Map<number, GrupoBusquedaClase>();

    for (const r of this.resultadosBusquedaAsignatura) {
      let grupo = mapa.get(r.idClase);
      if (!grupo) {
        const clase = this.asignaturaDetalle?.clases.find(c => c.id === r.idClase);
        grupo = {
          idClase: r.idClase,
          tituloClase: r.tituloClase,
          youtubeId: r.youtubeId,
          coincidencias: 0,
          mejorSimilitud: 0,
          duracion: clase ? this.duracionEstimadaVideo(clase) : null,
          fecha: clase?.fechaCreacion ? this.formatearFechaClase(clase.fechaCreacion) : null,
          fragmentos: []
        };
        mapa.set(r.idClase, grupo);
      }
      grupo.fragmentos.push(r);
      grupo.coincidencias++;
      if (r.similitud > grupo.mejorSimilitud) grupo.mejorSimilitud = r.similitud;
    }

    const grupos = [...mapa.values()];
    // Fragmentos de cada clase en orden cronológico; clases por mejor similitud desc
    grupos.forEach(g => g.fragmentos.sort((a, b) => a.tiempoInicio - b.tiempoInicio));
    grupos.sort((a, b) => b.mejorSimilitud - a.mejorSimilitud);
    return grupos;
  }

  alternarGrupoBusqueda(idClase: number): void {
    this.grupoBusquedaExpandido = this.grupoBusquedaExpandido === idClase ? null : idClase;
  }

  // ── Conceptos del curso ──────────────────────────────────────────────────

  private cargarConceptosCurso(id: number): void {
    this.cargandoConceptosCurso = true;
    this.conceptosCurso = [];
    this.transcripcionServicio.obtenerConceptosCurso(id).subscribe({
      next: (conceptos) => {
        if (this.asignaturaDetalle?.id !== id && this.vistaActual === 'cursos-detalle') {
          // La respuesta llegó pero ya se está viendo otra asignatura
        }
        this.conceptosCurso = conceptos;
        this.cargandoConceptosCurso = false;
        this.cd.markForCheck();
        this.medirDesbordamientoConceptos();
      },
      error: () => {
        // No bloquear el resto del detalle si esta sección falla
        this.conceptosCurso = [];
        this.cargandoConceptosCurso = false;
        this.cd.markForCheck();
      }
    });
  }

  get conceptosCursoFiltrados(): ConceptoCurso[] {
    const termino = this.filtroConceptosCurso.trim().toLowerCase();
    if (!termino) return this.conceptosCurso;
    return this.conceptosCurso.filter(c => c.nombre.toLowerCase().includes(termino));
  }

  // Alterna ver más / ver menos de los chips de conceptos
  alternarConceptosCurso(): void {
    this.conceptosCursoExpandido = !this.conceptosCursoExpandido;
  }

  // Se llama al filtrar para recalcular si los chips desbordan dos filas
  alFiltrarConceptosCurso(): void {
    this.medirDesbordamientoConceptos();
  }

  // Mide si el contenedor de chips (en estado contraído) desborda las dos filas
  @HostListener('window:resize')
  medirDesbordamientoConceptos(): void {
    // Esperar al render del DOM antes de medir
    setTimeout(() => {
      const contenedor = document.querySelector('.conceptos-curso-chips') as HTMLElement | null;
      if (!contenedor) {
        this.conceptosCursoDesborda = false;
        return;
      }
      // En estado contraído el contenedor recorta con max-height; si el contenido real
      // es mayor que el visible, hay desbordamiento → mostrar "Ver más".
      const desborda = contenedor.scrollHeight > contenedor.clientHeight + 4;
      if (desborda !== this.conceptosCursoDesborda) {
        this.conceptosCursoDesborda = desborda;
        this.cd.markForCheck();
      }
    }, 60);
  }

  conceptoCursoEsManual(concepto: ConceptoCurso): boolean {
    return concepto.clases.some(c => c.creadoManual === true);
  }

  conceptoCursoEsIa(concepto: ConceptoCurso): boolean {
    return concepto.clases.some(c => c.generadoPorIa === true);
  }

  // Primera definición no vacía de las apariciones del concepto activo
  get definicionConceptoActivo(): string {
    if (!this.conceptoCursoActivo) return '';
    const def = this.conceptoCursoActivo.clases.find(c => c.definicion?.trim())?.definicion;
    return def?.trim() ?? '';
  }

  // ── Modal de estudio del concepto + nota global ──────────────────────────

  abrirConceptoCurso(concepto: ConceptoCurso): void {
    if (!this.asignaturaDetalle) return;
    this.conceptoCursoActivo = concepto;
    this.notaConcepto = '';
    this.errorNotaConcepto = '';
    this.notaConceptoGuardada = false;
    this.resetearEdicionConcepto();
    this.modalConceptoCurso = true;
    this.cargarNotaConcepto();
  }

  cerrarModalConceptoCurso(): void {
    this.modalConceptoCurso = false;
    this.conceptoCursoActivo = null;
    this.guardandoNotaConcepto = false;
    this.cargandoNotaConcepto = false;
    this.errorNotaConcepto = '';
    this.notaConceptoGuardada = false;
    this.resetearEdicionConcepto();
  }

  private resetearEdicionConcepto(): void {
    this.editandoConcepto = false;
    this.editarConceptoNombre = '';
    this.editarConceptoDefinicion = '';
    this.guardandoEdicionConcepto = false;
    this.errorEdicionConcepto = '';
    this.confirmandoEliminarConcepto = false;
    this.eliminandoConcepto = false;
  }

  private cargarNotaConcepto(): void {
    if (!this.asignaturaDetalle || !this.conceptoCursoActivo) return;
    const idAsignatura = this.asignaturaDetalle.id;
    const nombre = this.conceptoCursoActivo.nombre;
    this.cargandoNotaConcepto = true;
    this.transcripcionServicio.obtenerNotaConcepto(idAsignatura, nombre).subscribe({
      next: (resultado) => {
        // Ignorar si el usuario ya cambió de concepto
        if (this.conceptoCursoActivo?.nombre !== nombre) return;
        this.notaConcepto = resultado.nota ?? '';
        this.cargandoNotaConcepto = false;
        this.cd.markForCheck();
      },
      error: () => {
        this.notaConcepto = '';
        this.cargandoNotaConcepto = false;
        this.cd.markForCheck();
      }
    });
  }

  guardarNotaConcepto(): void {
    if (!this.asignaturaDetalle || !this.conceptoCursoActivo) return;
    const idAsignatura = this.asignaturaDetalle.id;
    const nombre = this.conceptoCursoActivo.nombre;
    this.guardandoNotaConcepto = true;
    this.errorNotaConcepto = '';
    this.notaConceptoGuardada = false;

    this.transcripcionServicio.guardarNotaConcepto(idAsignatura, nombre, this.notaConcepto).subscribe({
      next: () => {
        this.guardandoNotaConcepto = false;
        this.notaConceptoGuardada = true;
        this.cd.markForCheck();
        // Ocultar el aviso "guardada" tras un instante
        setTimeout(() => { this.notaConceptoGuardada = false; this.cd.markForCheck(); }, 2000);
      },
      error: (err) => {
        this.errorNotaConcepto = err.error?.error ?? 'No se pudo guardar la nota.';
        this.guardandoNotaConcepto = false;
        this.cd.markForCheck();
      }
    });
  }

  // ── Editar / eliminar concepto (afecta a toda la asignatura) ─────────────

  iniciarEdicionConcepto(): void {
    if (!this.conceptoCursoActivo) return;
    this.editandoConcepto = true;
    this.errorEdicionConcepto = '';
    this.confirmandoEliminarConcepto = false;
    this.editarConceptoNombre = this.conceptoCursoActivo.nombre;
    this.editarConceptoDefinicion = this.definicionConceptoActivo;
  }

  cancelarEdicionConcepto(): void {
    this.editandoConcepto = false;
    this.errorEdicionConcepto = '';
  }

  guardarEdicionConcepto(): void {
    if (!this.asignaturaDetalle || !this.conceptoCursoActivo) return;
    if (!this.editarConceptoNombre.trim()) {
      this.errorEdicionConcepto = 'El nombre es obligatorio.';
      return;
    }
    const idAsignatura = this.asignaturaDetalle.id;
    const nombreOriginal = this.conceptoCursoActivo.nombre;
    const nuevoNombre = this.editarConceptoNombre.trim();
    const definicion = this.editarConceptoDefinicion.trim();
    this.guardandoEdicionConcepto = true;
    this.errorEdicionConcepto = '';

    this.transcripcionServicio.editarConceptoCurso(idAsignatura, nombreOriginal, nuevoNombre, definicion).subscribe({
      next: () => {
        this.guardandoEdicionConcepto = false;
        this.editandoConcepto = false;
        // Refrescar chips y reapuntar el concepto activo al nombre nuevo
        this.cargarConceptosCursoYReapuntar(idAsignatura, nuevoNombre);
      },
      error: (err) => {
        this.errorEdicionConcepto = err.error?.error ?? 'No se pudo editar el concepto.';
        this.guardandoEdicionConcepto = false;
        this.cd.markForCheck();
      }
    });
  }

  iniciarEliminarConcepto(): void {
    this.confirmandoEliminarConcepto = true;
    this.editandoConcepto = false;
  }

  cancelarEliminarConcepto(): void {
    this.confirmandoEliminarConcepto = false;
  }

  confirmarEliminarConcepto(): void {
    if (!this.asignaturaDetalle || !this.conceptoCursoActivo) return;
    const idAsignatura = this.asignaturaDetalle.id;
    const nombre = this.conceptoCursoActivo.nombre;
    this.eliminandoConcepto = true;

    this.transcripcionServicio.eliminarConceptoCurso(idAsignatura, nombre).subscribe({
      next: () => {
        this.eliminandoConcepto = false;
        this.cerrarModalConceptoCurso();
        // Recargar la lista y, si es la asignatura abierta, sus clases (las apariciones cambiaron)
        this.cargarConceptosCurso(idAsignatura);
        this.refrescarDetalleAsignatura(idAsignatura);
      },
      error: () => {
        this.eliminandoConcepto = false;
        this.confirmandoEliminarConcepto = false;
        this.cd.markForCheck();
      }
    });
  }

  // Tras editar, recarga los conceptos y vuelve a apuntar el concepto activo al nuevo nombre
  private cargarConceptosCursoYReapuntar(idAsignatura: number, nuevoNombre: string): void {
    this.transcripcionServicio.obtenerConceptosCurso(idAsignatura).subscribe({
      next: (conceptos) => {
        this.conceptosCurso = conceptos;
        const reapuntado = conceptos.find(c => c.nombre.toLowerCase() === nuevoNombre.toLowerCase());
        if (reapuntado && this.modalConceptoCurso) {
          this.conceptoCursoActivo = reapuntado;
        } else {
          // Si no se encuentra (no debería pasar), cerrar el modal
          this.cerrarModalConceptoCurso();
        }
        this.cd.markForCheck();
        this.medirDesbordamientoConceptos();
        // Las clases de la asignatura también reflejan el cambio de nombre
        this.refrescarDetalleAsignatura(idAsignatura);
      },
      error: () => this.cd.markForCheck()
    });
  }

  // Cierra el modal de concepto con la tecla ESC
  @HostListener('document:keydown.escape')
  cerrarConEscape(): void {
    if (this.modalConceptoCurso) {
      this.cerrarModalConceptoCurso();
    }
  }

  // Abre la clase donde aparece el concepto y la posiciona en el momento exacto
  irAlMomentoConcepto(aparicion: { idClase: number; tiempoInicio: number }): void {
    const origen = this.asignaturaDetalle?.id;
    // Cerrar el modal antes de navegar a la clase
    this.modalConceptoCurso = false;
    this.conceptoCursoActivo = null;
    this.abrirClase(aparicion.idClase, undefined, origen, Math.max(0, Math.floor(aparicion.tiempoInicio)));
  }

  get profesorEfectivoAsignatura(): string {
    if (!this.asignaturaDetalle) return '';
    // Si la asignatura tiene profesor definido, usarlo
    if (this.asignaturaDetalle.profesor?.trim()) return this.asignaturaDetalle.profesor;
    // Sino derivar de las clases
    const profesores = [...new Set(
      this.asignaturaDetalle.clases
        .map(c => c.profesor?.trim())
        .filter((p): p is string => !!p && p !== 'Profesor pendiente')
    )];
    if (profesores.length === 0) return '';
    if (profesores.length === 1) return profesores[0];
    return 'Varios profesores';
  }

  alternarMenuAsignatura(evento: Event): void {
    evento.stopPropagation();
    this.menuAsignaturaAbierto = !this.menuAsignaturaAbierto;
  }

  formatearFechaClase(fechaCreacion: string | null): string {
    if (!fechaCreacion) return '';
    const d = new Date(fechaCreacion);
    return d.toLocaleDateString('es-ES', { day: '2-digit', month: '2-digit', year: 'numeric' });
  }

  formatearFechaRelativa(fecha: string | null): string {
    if (!fecha) return 'Sin actualizar';
    const diff = Date.now() - new Date(fecha).getTime();
    const dias = Math.floor(diff / 86400000);
    if (dias === 0) return 'Hoy';
    if (dias === 1) return 'Ayer';
    if (dias < 7) return `Hace ${dias} días`;
    if (dias < 30) return `Hace ${Math.floor(dias / 7)} semanas`;
    return `Hace ${Math.floor(dias / 30)} meses`;
  }

  // ── Métodos de tarjetas de clases recientes ───────────────────────────────

  alternarMenuTarjeta(idVideo: number, evento: Event): void {
    evento.stopPropagation();
    this.menuTarjetaAbiertoId = this.menuTarjetaAbiertoId === idVideo ? null : idVideo;
    this.confirmandoEliminarId = null;
  }

  iniciarConfirmacionEliminar(idVideo: number, evento: Event): void {
    evento.stopPropagation();
    this.confirmandoEliminarId = idVideo;
  }

  cancelarEliminar(evento: Event): void {
    evento.stopPropagation();
    this.confirmandoEliminarId = null;
    this.menuTarjetaAbiertoId = null;
  }

  eliminarClase(video: VideoResumen, evento: Event): void {
    evento.stopPropagation();
    this.eliminandoId = video.id;
    this.menuTarjetaAbiertoId = null;
    this.confirmandoEliminarId = null;

    this.transcripcionServicio.eliminarVideo(video.id).subscribe({
      next: () => {
        this.historial = this.historial.filter(v => v.id !== video.id);
        this.cacheClases.delete(video.id);
        this.eliminandoId = null;
        this.cd.detectChanges();
      },
      error: () => {
        this.eliminandoId = null;
        this.cd.detectChanges();
      }
    });
  }

  irAHome(): void {
    this.cancelarCargaClasePendiente();
    this.origenClase = null;
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
    this.origenClase = null;
    this.vistaActual = 'cursos';
    this.actualizarRuta('#/cursos');
    this.cargarAsignaturas();
  }

  irAHistorial(): void {
    this.cancelarCargaClasePendiente();
    this.origenClase = null;
    this.vistaActual = 'historial';
    this.actualizarRuta('#/historial');
  }

  abrirClase(idVideo: number, preview?: VideoResumen, origenAsignatura?: number, tiempoInicial?: number): void {
    if (!Number.isFinite(idVideo)) return;
    // Capturar el origen ANTES de cambiar de vista (this.vistaActual aún es la de partida)
    this.origenClase = this.calcularOrigenClase(origenAsignatura);
    // Si se pide abrir en un segundo concreto (p.ej. "Ir al momento" de un concepto)
    this.tiempoInicialPendiente = (tiempoInicial != null && tiempoInicial > 0)
      ? Math.floor(tiempoInicial) : null;
    this.actualizarRuta('#/clase/' + idVideo);
    this.cargarClaseDesdeRuta(idVideo, true, preview);
  }

  private calcularOrigenClase(origenAsignatura?: number): OrigenClase {
    if (origenAsignatura != null) {
      return { tipo: 'asignatura', idAsignatura: origenAsignatura };
    }
    if (this.vistaActual === 'historial') {
      return { tipo: 'historial' };
    }
    if ((this.vistaActual === 'cursos-detalle' || this.vistaActual === 'cursos') && this.asignaturaDetalle) {
      return { tipo: 'asignatura', idAsignatura: this.asignaturaDetalle.id };
    }
    // Inicio o entrada directa por URL → volver a inicio
    return { tipo: 'home' };
  }

  get mostrarVolverClase(): boolean {
    return this.origenClase !== null;
  }

  volverAlOrigen(): void {
    const origen = this.origenClase;
    if (!origen) {
      this.irAHome();
      return;
    }
    switch (origen.tipo) {
      case 'historial':
        this.irAHistorial();
        break;
      case 'asignatura':
        this.cargarDetalleAsignatura(origen.idAsignatura);
        break;
      case 'home':
      default:
        this.irAHome();
        break;
    }
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
      this.enviarMensajeChat();
    }
  }

  preguntarConcepto(concepto: ConceptoClave): void {
    this.pregunta = `Explicame el concepto "${concepto.nombre}" en esta clase`;
    if (this.videoSeleccionado) {
      this.enviarMensajeChat();
    }
  }

  saltarATiempo(segundos: number): void {
    const objetivo = Math.max(0, Math.floor(segundos));
    this.tiempoInicioReproductor = objetivo;

    // Si la IFrame API está lista, usar seekTo (no recrea el iframe → más fluido y
    // mantiene viva la referencia del player para leer el tiempo).
    if (this.playerListo && this.youtubePlayer?.seekTo) {
      try {
        this.youtubePlayer.seekTo(objetivo, true);
        this.youtubePlayer.playVideo?.();
        this.cd.markForCheck();
        return;
      } catch {
        // Si falla, caer al método de recarga de iframe
      }
    }

    // Fallback: reconstruir la URL con start={segundos}&autoplay para recargar el iframe.
    this.actualizarUrlEmbed(true);
    this.cd.markForCheck();
  }

  // Salta al inicio de un capítulo (atajo semántico solicitado)
  irACapitulo(capitulo: CapituloClase): void {
    this.saltarATiempo(capitulo.tiempo);
  }

  // Devuelve el segundo actual de reproducción. Si el player no está listo,
  // usa el último segundo al que se saltó como aproximación, o 0.
  obtenerTiempoActualVideo(): number {
    if (this.playerListo && this.youtubePlayer?.getCurrentTime) {
      try {
        const t = this.youtubePlayer.getCurrentTime();
        if (Number.isFinite(t) && t >= 0) return Math.floor(t);
      } catch {
        // ignorar y usar fallback
      }
    }
    return Math.max(0, Math.floor(this.tiempoInicioReproductor)) || 0;
  }

  // Sigue el progreso del trabajo en tiempo real por WebSocket. Si la conexión falla o se
  // cierra antes de terminar, cae automáticamente al sondeo (polling) HTTP.
  private iniciarSeguimientoTrabajo(idTrabajo: string): void {
    let url: string;
    try {
      url = this.transcripcionServicio.urlWebSocketTrabajo(idTrabajo);
    } catch {
      this.iniciarPolling(idTrabajo);
      return;
    }

    let socket: WebSocket;
    try {
      socket = new WebSocket(url);
    } catch {
      this.iniciarPolling(idTrabajo);
      return;
    }
    this.socketProgreso = socket;

    socket.onmessage = (evento) => {
      try {
        const estado = JSON.parse(evento.data) as EstadoTrabajo;
        this.manejarEstadoTrabajo(estado);
      } catch {
        // Mensaje no interpretable: se ignora.
      }
    };

    // Si el socket falla o se cierra y el trabajo sigue activo, recurrimos al polling.
    const recurrirAFallback = () => {
      if (this.trabajoActivoId === idTrabajo && this.socketProgreso === socket) {
        this.cerrarSocketProgreso();
        this.iniciarPolling(idTrabajo);
      }
    };
    socket.onerror = recurrirAFallback;
    socket.onclose = recurrirAFallback;
  }

  // Sondeo HTTP como alternativa al WebSocket. Idempotente: no arranca dos veces.
  private iniciarPolling(idTrabajo: string): void {
    if (this.intervaloPolling !== null) {
      return;
    }
    this.intervaloPolling = setInterval(() => {
      this.transcripcionServicio.obtenerEstadoTrabajo(idTrabajo).subscribe({
        next: (estado) => this.manejarEstadoTrabajo(estado),
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

  // Procesa un estado del trabajo, venga del WebSocket o del polling.
  private manejarEstadoTrabajo(estado: EstadoTrabajo): void {
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
  }

  // Detiene cualquier seguimiento en curso: sondeo HTTP y conexión WebSocket.
  private detenerPolling(): void {
    if (this.intervaloPolling !== null) {
      clearInterval(this.intervaloPolling);
      this.intervaloPolling = null;
    }
    this.cerrarSocketProgreso();
  }

  private cerrarSocketProgreso(): void {
    if (this.socketProgreso) {
      const socket = this.socketProgreso;
      this.socketProgreso = null;
      socket.onmessage = null;
      socket.onerror = null;
      socket.onclose = null;
      try { socket.close(); } catch { /* ya cerrado */ }
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

  // Limpia el reproductor (al salir de la clase o iniciar un nuevo procesamiento)
  private limpiarReproductor(): void {
    this.urlEmbedClase = null;
    this.mostrarIframe = false;
    this.tiempoInicioReproductor = 0;
    this.destruirPlayer();
  }

  // ── YouTube IFrame API (lectura de tiempo + seek) ────────────────────────

  private inicializarPlayer(): void {
    if (!this.youtubeIdClase || !this.mostrarIframe) return;
    this.cargarApiYoutube(() => {
      const elemento = document.getElementById(this.idIframeClase);
      if (!elemento || !window.YT?.Player) return;
      // Soltar la referencia anterior sin destroy() (Angular ya gestiona el nodo)
      this.youtubePlayer = null;
      this.playerListo = false;
      try {
        this.youtubePlayer = new window.YT.Player(this.idIframeClase, {
          events: {
            onReady: () => { this.playerListo = true; },
            onStateChange: (evento: { data: number }) => {
              if (evento.data === window.YT?.PlayerState?.ENDED && this.videoSeleccionado) {
                this.marcarClaseCompletada(true);
              }
            }
          }
        });
      } catch {
        this.youtubePlayer = null;
        this.playerListo = false;
      }
    });
  }

  // Carga el script de la IFrame API una sola vez y ejecuta el callback cuando está listo.
  private cargarApiYoutube(callback: () => void): void {
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

  // Suelta la referencia al player SIN llamar a destroy(): destroy() elimina el <iframe>
  // del DOM y choca con el control de Angular (NotFoundError en insertBefore). Angular
  // retira el nodo mediante @if (mostrarIframe / urlEmbedClase).
  private destruirPlayer(): void {
    this.playerListo = false;
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
    // Resuelve el nombre de asignatura para mostrarlo localmente
    const nombreAsignatura = metadata.idAsignatura != null
      ? (this.asignaturasObjetos.find(a => a.id === metadata.idAsignatura)?.nombre ?? metadata.asignatura)
      : metadata.asignatura;

    const aplicar = (video: VideoResumen): VideoResumen => ({
      ...video,
      asignatura: nombreAsignatura ?? video.asignatura,
      idAsignatura: metadata.idAsignatura !== undefined ? metadata.idAsignatura : video.idAsignatura,
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
      this.asignaturaSugerida = videoActualizado.asignaturaSugerida ?? false;
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
    this.reiniciarChat();
    this.editandoTitulo = false;
    this.cargandoFragmentos = false;
    this.limpiarReproductor();
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

  private programarLoadingAsignatura(): void {
    this.limpiarTemporizadoresAsignatura();

    this.temporizadorSkeletonAsignatura = setTimeout(() => {
      if (!this.cargandoDetalleAsignatura) return;
      this.mostrandoSkeletonAsignatura = true;
      this.cd.detectChanges();
    }, 150);

    this.temporizadorMensajeAsignatura = setTimeout(() => {
      if (!this.cargandoDetalleAsignatura) return;
      this.mostrandoMensajeAsignatura = true;
      this.cd.detectChanges();
    }, 700);
  }

  private limpiarTemporizadoresAsignatura(): void {
    if (this.temporizadorSkeletonAsignatura !== null) {
      clearTimeout(this.temporizadorSkeletonAsignatura);
      this.temporizadorSkeletonAsignatura = null;
    }
    if (this.temporizadorMensajeAsignatura !== null) {
      clearTimeout(this.temporizadorMensajeAsignatura);
      this.temporizadorMensajeAsignatura = null;
    }
    this.mostrandoSkeletonAsignatura = false;
    this.mostrandoMensajeAsignatura = false;
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
    // Las asignaturas se gestionan desde el backend (asignaturasObjetos).
    // Solo sincronizamos profesores desde los vídeos para mantener el selector actualizado.
    const profesores = videos
      .map(video => video.profesor)
      .filter((valor): valor is string => !!valor?.trim());

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
        // Entrada directa por URL (o navegador atrás/adelante): si no hay origen previo,
        // el botón atrás vuelve a Inicio
        if (this.origenClase === null) {
          this.origenClase = { tipo: 'home' };
        }
        this.cargarClaseDesdeRuta(idVideo);
      } else {
        this.irAHome();
      }
      return;
    }

    if (vista === 'cursos' && id) {
      const idAsignatura = Number(id);
      if (Number.isFinite(idAsignatura)) {
        this.cancelarCargaClasePendiente();
        this.cargarDetalleAsignatura(idAsignatura);
        return;
      }
    }

    if (vista === 'cursos') {
      this.cancelarCargaClasePendiente();
      this.vistaActual = 'cursos';
      this.cargarAsignaturas();
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
        // Zoneless: el callback HTTP no repinta solo. Forzar CD para que el iframe
        // (urlEmbedClase) se renderice al entrar directo por URL o tras refrescar.
        this.cd.detectChanges();
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
    if (ruta.startsWith('cursos/')) return 'cursos-detalle';
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
