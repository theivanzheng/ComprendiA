package es.comprendia.servicio;

import es.comprendia.dto.EstadoTrabajoDTO.Fase;
import es.comprendia.dto.FragmentoTranscripcionDTO;
import es.comprendia.dto.RespuestaTranscripcionDTO;
import es.comprendia.entidad.FragmentoTranscripcion;
import es.comprendia.excepcion.ExcepcionTranscripcionYoutube;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@ApplicationScoped
public class TranscripcionYoutubeServicio {

    private static final Logger LOG = Logger.getLogger(TranscripcionYoutubeServicio.class);
    private static final Set<String> DOMINIOS_YOUTUBE = Set.of(
        "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be"
    );

    @Inject
    YoutubeTranscripcionServicio youtubeTranscripcionServicio;

    @Inject
    PipelineWhisperServicio pipelineWhisperServicio;

    @Inject
    TranscripcionPersistenciaServicio transcripcionPersistenciaServicio;

    @Inject
    EmbeddingFragmentoServicio embeddingFragmentoServicio;

    @Inject
    AnalisisClaseServicio analisisClaseServicio;

    @Inject
    ClasificacionAsignaturaServicio clasificacionAsignaturaServicio;

    @Inject
    ReagrupadorFragmentosServicio reagrupadorFragmentos;

    @Inject
    @ConfigProperty(name = "comprendia.transcripcion.modo", defaultValue = "simulada")
    String modoTranscripcion;

    // Backward-compat: lo siguen usando los tests y cualquier llamada legacy
    public RespuestaTranscripcionDTO procesarUrlYoutube(String urlVideo) {
        validarUrlYoutube(urlVideo);
        return procesarUrlYoutube(urlVideo, fase -> {});
    }

    public RespuestaTranscripcionDTO procesarUrlYoutube(String urlVideo, Consumer<Fase> actualizarFase) {
        return procesarUrlYoutube(urlVideo, actualizarFase, () -> false);
    }

    public RespuestaTranscripcionDTO procesarUrlYoutube(
        String urlVideo,
        Consumer<Fase> actualizarFase,
        BooleanSupplier cancelado) {
        LOG.infof("[Tiempo] ===== Proceso total iniciado =====");
        long inicioTotal = System.currentTimeMillis();
        Long idVideoGuardado = null;

        try {
            verificarCancelacion(cancelado);

            // Fase 1: validación y extracción de ID
            long inicioFase = System.currentTimeMillis();
            validarUrlYoutube(urlVideo);
            String idVideo = extraerIdVideo(urlVideo);
            LOG.infof("[Tiempo] Validación completada en %d ms — idVideo: %s",
                System.currentTimeMillis() - inicioFase, idVideo);

            verificarCancelacion(cancelado);

            // Fase 2: transcripción (el pipeline Whisper emite DESCARGANDO y TRANSCRIBIENDO)
            inicioFase = System.currentTimeMillis();
            RespuestaTranscripcionDTO respuesta = switch (modoTranscripcion) {
                case "scraping" -> {
                    actualizarFase.accept(Fase.TRANSCRIBIENDO);
                    yield procesarConScraping(idVideo);
                }
                case "whisper" -> pipelineWhisperServicio.transcribirDesdeAudio(idVideo, actualizarFase);
                default -> {
                    actualizarFase.accept(Fase.TRANSCRIBIENDO);
                    yield construirTranscripcionSimulada(idVideo);
                }
            };
            LOG.infof("[Tiempo] Transcripción completada en %d ms — %d fragmentos",
                System.currentTimeMillis() - inicioFase, respuesta.getFragmentos().size());
            // [Diagnóstico] Cobertura temporal de la transcripción
            registrarDiagnosticoTranscripcion(respuesta);

            // Reagrupar fragmentos pequeños en ventanas mayores (mejora la búsqueda semántica).
            respuesta.setFragmentos(reagrupadorFragmentos.reagrupar(respuesta.getFragmentos()));

            verificarCancelacion(cancelado);

            // Fase 3: persistencia
            LOG.info("[Estado] Guardando en base de datos");
            actualizarFase.accept(Fase.GUARDANDO);
            inicioFase = System.currentTimeMillis();
            List<FragmentoTranscripcion> fragmentosGuardados = transcripcionPersistenciaServicio.guardarTranscripcion(respuesta);
            idVideoGuardado = fragmentosGuardados.isEmpty() ? null : fragmentosGuardados.get(0).video.id;
            LOG.infof("[Tiempo] Persistencia completada en %d ms — %d fragmentos guardados",
                System.currentTimeMillis() - inicioFase, fragmentosGuardados.size());

            verificarCancelacion(cancelado);

            // Fase 4: embeddings
            LOG.infof("[Estado] Generando embeddings para %d fragmentos", fragmentosGuardados.size());
            actualizarFase.accept(Fase.EMBEDDINGS);
            inicioFase = System.currentTimeMillis();
            embeddingFragmentoServicio.generarYGuardar(fragmentosGuardados, cancelado);
            LOG.infof("[Tiempo] Embeddings completados en %d ms",
                System.currentTimeMillis() - inicioFase);

            verificarCancelacion(cancelado);

            LOG.info("[Estado] Generando capitulos y conceptos clave");
            actualizarFase.accept(Fase.ANALIZANDO);
            inicioFase = System.currentTimeMillis();
            analisisClaseServicio.generarYGuardar(idVideoGuardado, fragmentosGuardados);
            LOG.infof("[Tiempo] Analisis de clase completado en %d ms",
                System.currentTimeMillis() - inicioFase);

            // Fase 6: autoasignación sugerida de asignatura (canal / semántica / nueva).
            // No debe romper el procesamiento: si falla, se registra y se continúa.
            if (idVideoGuardado != null) {
                try {
                    inicioFase = System.currentTimeMillis();
                    clasificacionAsignaturaServicio.clasificarYAsignar(idVideoGuardado, idVideo);
                    LOG.infof("[Tiempo] Autoasignación de asignatura completada en %d ms",
                        System.currentTimeMillis() - inicioFase);
                } catch (Exception e) {
                    LOG.warnf("[Clasificacion] La autoasignación falló para vídeo id=%s: %s",
                        idVideoGuardado, e.getMessage());
                }
            }

            LOG.infof("[Tiempo] ===== Proceso total completado en %d ms =====",
                System.currentTimeMillis() - inicioTotal);
            return respuesta;
        } catch (CancellationException e) {
            if (idVideoGuardado != null) {
                transcripcionPersistenciaServicio.eliminarVideoCompleto(idVideoGuardado);
            }
            throw e;
        } catch (RuntimeException e) {
            if (Thread.currentThread().isInterrupted() || cancelado.getAsBoolean()) {
                if (idVideoGuardado != null) {
                    transcripcionPersistenciaServicio.eliminarVideoCompleto(idVideoGuardado);
                }
                throw new CancellationException("Trabajo cancelado por el usuario");
            }
            throw e;
        }
    }

    private void verificarCancelacion(BooleanSupplier cancelado) {
        if (Thread.currentThread().isInterrupted() || cancelado.getAsBoolean()) {
            throw new CancellationException("Trabajo cancelado por el usuario");
        }
    }

    // [Diagnóstico] Registra la cobertura temporal de la transcripción recién obtenida
    private void registrarDiagnosticoTranscripcion(RespuestaTranscripcionDTO respuesta) {
        var fragmentos = respuesta.getFragmentos();
        if (fragmentos == null || fragmentos.isEmpty()) {
            LOG.warn("[Diagnostico] Transcripción sin fragmentos");
            return;
        }
        double primerInicio = fragmentos.stream()
            .mapToDouble(f -> f.getTiempoInicio())
            .min().orElse(0.0);
        double ultimoFin = fragmentos.stream()
            .mapToDouble(f -> f.getTiempoFin())
            .max().orElse(0.0);
        LOG.infof("[Diagnostico] Transcripción: %d fragmentos, primer inicio=%.0fs, ultimo fin=%.0fs (fuente=%s)",
            fragmentos.size(), primerInicio, ultimoFin, respuesta.getFuenteTranscripcion());
    }

    private RespuestaTranscripcionDTO procesarConScraping(String idVideo) {
        try {
            return youtubeTranscripcionServicio.obtenerTranscripcion(idVideo);
        } catch (ExcepcionTranscripcionYoutube e) {
            // Sin subtítulos disponibles: fallback a simulada
            return construirTranscripcionSimulada(idVideo);
        }
    }

    public void validarUrlYoutube(String urlVideo) {
        if (urlVideo == null || urlVideo.isBlank()) {
            throw new IllegalArgumentException("La URL del vídeo no puede estar vacía");
        }
        String url = urlVideo.strip();
        // Añadir protocolo si falta (ej: "www.youtube.com/...")
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        try {
            String host = new URI(url).getHost();
            if (host == null || !DOMINIOS_YOUTUBE.contains(host)) {
                throw new IllegalArgumentException(
                    "La URL no corresponde a un vídeo de YouTube válido");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("La URL no tiene un formato válido");
        }
    }

    private String extraerIdVideo(String urlVideo) {
        String url = urlVideo.strip();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        if (url.contains("youtube.com/watch?v=")) {
            int inicio = url.indexOf("v=") + 2;
            int fin = url.indexOf('&', inicio);
            return fin == -1 ? url.substring(inicio) : url.substring(inicio, fin);
        }
        if (url.contains("youtu.be/")) {
            int inicio = url.indexOf("youtu.be/") + 9;
            int fin = url.indexOf('?', inicio);
            return fin == -1 ? url.substring(inicio) : url.substring(inicio, fin);
        }
        if (url.contains("youtube.com/shorts/")) {
            int inicio = url.indexOf("youtube.com/shorts/") + 19;
            int fin = url.indexOf('?', inicio);
            return fin == -1 ? url.substring(inicio) : url.substring(inicio, fin);
        }
        throw new IllegalArgumentException("Formato de URL de YouTube no reconocido");
    }

    private RespuestaTranscripcionDTO construirTranscripcionSimulada(String idVideo) {
        List<FragmentoTranscripcionDTO> fragmentos = List.of(
            new FragmentoTranscripcionDTO("Bienvenido a esta clase simulada.", 0.0, 10.0),
            new FragmentoTranscripcionDTO("En esta parte se introduce el concepto principal.", 10.0, 25.0),
            new FragmentoTranscripcionDTO("Ahora vemos un ejemplo práctico del tema.", 25.0, 45.0),
            new FragmentoTranscripcionDTO("Resumen y conclusiones de la clase.", 45.0, 60.0)
        );
        return new RespuestaTranscripcionDTO(idVideo, "Clase simulada de ComprendiA", fragmentos, "SIMULADA");
    }
}
