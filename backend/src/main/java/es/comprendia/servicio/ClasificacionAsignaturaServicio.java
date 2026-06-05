package es.comprendia.servicio;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.comprendia.dto.CapituloVideoDTO;
import es.comprendia.dto.ConceptoClaveVideoDTO;
import es.comprendia.entidad.Asignatura;
import es.comprendia.entidad.CriterioAsignacion;
import es.comprendia.entidad.Video;
import es.comprendia.repositorio.AsignaturaRepositorio;
import es.comprendia.repositorio.CapituloVideoRepositorio;
import es.comprendia.repositorio.ConceptoClaveVideoRepositorio;
import es.comprendia.repositorio.VideoRepositorio;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Sugiere y asigna automáticamente una asignatura a un vídeo recién analizado.
 *
 * Orden de decisión:
 *   1) Por canal de YouTube (id, luego nombre normalizado).
 *   2) Por similitud semántica con asignaturas existentes.
 *   3) Si no hay candidata, crea una asignatura nueva.
 *
 * En todos los casos la asignatura queda marcada como SUGERIDA (asignaturaSugerida = true):
 * es solo metadato visual; el nombre real de la asignatura NUNCA contiene "(sugerida)".
 */
@ApplicationScoped
public class ClasificacionAsignaturaServicio {

    private static final Logger LOG = Logger.getLogger(ClasificacionAsignaturaServicio.class);
    // Umbral flexible: preferimos clasificar de más y dejar que el usuario corrija.
    private static final double UMBRAL_SIMILITUD = 0.30;

    @Inject VideoRepositorio videoRepositorio;
    @Inject AsignaturaRepositorio asignaturaRepositorio;
    @Inject ConceptoClaveVideoRepositorio conceptoRepositorio;
    @Inject CapituloVideoRepositorio capituloRepositorio;
    @Inject EmbeddingServicio embeddingServicio;
    @Inject AudioExtraccionServicio audioExtraccionServicio;

    // Permite desactivar la autoasignación (p. ej. en tests, para no llamar a yt-dlp ni OpenAI).
    @ConfigProperty(name = "comprendia.clasificacion.habilitada", defaultValue = "true")
    boolean clasificacionHabilitada;

    private final ObjectMapper mapeadorJson = new ObjectMapper();

    @Transactional
    public void clasificarYAsignar(Long idVideo, String youtubeId) {
        if (!clasificacionHabilitada) {
            LOG.info("[Clasificacion] Autoasignación deshabilitada por configuración");
            return;
        }
        long inicioTotal = System.currentTimeMillis();
        Video video = videoRepositorio.findById(idVideo);
        if (video == null) {
            LOG.warnf("[Clasificacion] Vídeo id=%s no encontrado, se omite la autoasignación", idVideo);
            return;
        }

        // 1) Canal de YouTube
        long t0 = System.currentTimeMillis();
        AudioExtraccionServicio.MetadatosCanal canal = audioExtraccionServicio.obtenerMetadatosCanal(youtubeId);
        long msMetadatosCanal = System.currentTimeMillis() - t0;
        video.canalYoutubeId = canal.canalId();
        video.canalYoutubeNombre = canal.canalNombre();
        LOG.infof("[Clasificacion] Vídeo id=%s — canalId=%s, canalNombre=%s",
            idVideo, canal.canalId(), canal.canalNombre());
        LOG.infof("[Clasificacion][Tiempo] Metadatos de canal (yt-dlp): %d ms", msMetadatosCanal);

        List<Asignatura> asignaturas = asignaturaRepositorio.listAll();

        t0 = System.currentTimeMillis();
        Optional<Asignatura> porCanal = buscarPorCanal(asignaturas, canal);
        long msMatchCanal = System.currentTimeMillis() - t0;
        LOG.infof("[Clasificacion][Tiempo] Match por canal (%d asignaturas): %d ms — %s",
            asignaturas.size(), msMatchCanal, porCanal.isPresent() ? "encontrada" : "sin coincidencia");
        if (porCanal.isPresent()) {
            t0 = System.currentTimeMillis();
            asignar(video, porCanal.get(), CriterioAsignacion.CANAL);
            aprenderCanalEnAsignatura(porCanal.get(), video);
            long msAsociacion = System.currentTimeMillis() - t0;
            LOG.infof("[Clasificacion] Asignada por CANAL a '%s' (id=%s) para vídeo id=%s",
                porCanal.get().nombre, porCanal.get().id, idVideo);
            LOG.infof("[Clasificacion][Tiempo] Asociación final: %d ms", msAsociacion);
            LOG.infof("[Clasificacion][Tiempo] TOTAL (vía CANAL): %d ms", System.currentTimeMillis() - inicioTotal);
            return;
        }

        // 2) Similitud semántica
        try {
            t0 = System.currentTimeMillis();
            String textoVideo = construirTextoVideo(video, idVideo);
            List<Double> embeddingVideo = embeddingServicio.generarEmbedding(textoVideo);
            long msEmbeddingVideo = System.currentTimeMillis() - t0;
            LOG.infof("[Clasificacion][Tiempo] Embedding del vídeo (OpenAI): %d ms", msEmbeddingVideo);

            long msEmbeddingsAsig = 0L;   // carga/generación de embeddings de asignaturas
            long msSimilitud = 0L;        // cálculo de coseno
            int asignaturasComparadas = 0;
            Asignatura mejor = null;
            double mejorPuntuacion = -1.0;
            for (Asignatura a : asignaturas) {
                long tEmb = System.currentTimeMillis();
                List<Double> embeddingAsig = obtenerOcalcularEmbedding(a);
                msEmbeddingsAsig += System.currentTimeMillis() - tEmb;
                if (embeddingAsig == null) continue;
                long tSim = System.currentTimeMillis();
                double similitud = coseno(embeddingVideo, embeddingAsig);
                msSimilitud += System.currentTimeMillis() - tSim;
                asignaturasComparadas++;
                LOG.infof("[Clasificacion] Similitud vídeo id=%s vs asignatura '%s' (id=%s) = %.4f",
                    idVideo, a.nombre, a.id, similitud);
                if (similitud > mejorPuntuacion) {
                    mejorPuntuacion = similitud;
                    mejor = a;
                }
            }
            LOG.infof("[Clasificacion][Tiempo] Embeddings de asignaturas (carga/generación, %d asignaturas): %d ms",
                asignaturas.size(), msEmbeddingsAsig);
            LOG.infof("[Clasificacion][Tiempo] Cálculo de similitud (%d comparaciones): %d ms",
                asignaturasComparadas, msSimilitud);

            if (mejor != null && mejorPuntuacion >= UMBRAL_SIMILITUD) {
                t0 = System.currentTimeMillis();
                asignar(video, mejor, CriterioAsignacion.SEMANTICA);
                aprenderCanalEnAsignatura(mejor, video);
                long msAsociacion = System.currentTimeMillis() - t0;
                LOG.infof("[Clasificacion] Asignada por SEMANTICA a '%s' (id=%s, score=%.4f) para vídeo id=%s",
                    mejor.nombre, mejor.id, mejorPuntuacion, idVideo);
                LOG.infof("[Clasificacion][Tiempo] Asociación final: %d ms", msAsociacion);
                LOG.infof("[Clasificacion][Tiempo] TOTAL (vía SEMANTICA): %d ms", System.currentTimeMillis() - inicioTotal);
                return;
            }

            // 3) Crear asignatura nueva (reutilizando el embedding del vídeo)
            t0 = System.currentTimeMillis();
            crearAsignaturaNueva(video, idVideo, canal, embeddingVideo);
            LOG.infof("[Clasificacion][Tiempo] Asociación final (asignatura nueva): %d ms",
                System.currentTimeMillis() - t0);
            LOG.infof("[Clasificacion][Tiempo] TOTAL (vía NUEVA): %d ms", System.currentTimeMillis() - inicioTotal);
        } catch (Exception e) {
            // Si los embeddings fallan (p. ej. sin clave OpenAI), creamos asignatura nueva sin embedding.
            LOG.warnf("[Clasificacion] Fallo en la clasificación semántica del vídeo id=%s: %s. Se creará asignatura nueva.",
                idVideo, e.getMessage());
            crearAsignaturaNueva(video, idVideo, canal, null);
            LOG.infof("[Clasificacion][Tiempo] TOTAL (vía NUEVA tras fallo semántico): %d ms",
                System.currentTimeMillis() - inicioTotal);
        }
    }

    private Optional<Asignatura> buscarPorCanal(List<Asignatura> asignaturas, AudioExtraccionServicio.MetadatosCanal canal) {
        if (canal.canalId() != null && !canal.canalId().isBlank()) {
            Optional<Asignatura> porId = asignaturas.stream()
                .filter(a -> canal.canalId().equals(a.canalYoutubeId))
                .findFirst();
            if (porId.isPresent()) return porId;
        }
        if (canal.canalNombre() != null && !canal.canalNombre().isBlank()) {
            String objetivo = normalizar(canal.canalNombre());
            return asignaturas.stream()
                .filter(a -> a.canalYoutubeNombre != null
                    && normalizar(a.canalYoutubeNombre).equals(objetivo))
                .findFirst();
        }
        return Optional.empty();
    }

    private void crearAsignaturaNueva(Video video, Long idVideo,
                                      AudioExtraccionServicio.MetadatosCanal canal,
                                      List<Double> embeddingVideo) {
        Asignatura nueva = new Asignatura();
        nueva.nombre = nombreSugerido(video, idVideo, canal);
        nueva.descripcion = null;
        nueva.canalYoutubeId = canal.canalId();
        nueva.canalYoutubeNombre = canal.canalNombre();
        nueva.palabrasClave = construirPalabrasClave(idVideo);
        nueva.embeddingResumen = embeddingVideo != null ? serializar(embeddingVideo) : null;
        nueva.fechaCreacion = LocalDateTime.now();
        nueva.fechaActualizacion = LocalDateTime.now();
        asignaturaRepositorio.persist(nueva);

        // Si hay canal, el criterio es CANAL (futuros vídeos del mismo canal caerán aquí);
        // si no, fue una decisión por contenido (SEMANTICA).
        CriterioAsignacion criterio = (canal.canalId() != null || canal.canalNombre() != null)
            ? CriterioAsignacion.CANAL : CriterioAsignacion.SEMANTICA;
        asignar(video, nueva, criterio);
        LOG.infof("[Clasificacion] Creada asignatura NUEVA '%s' (id=%s, criterio=%s) para vídeo id=%s",
            nueva.nombre, nueva.id, criterio, idVideo);
    }

    private void asignar(Video video, Asignatura asignatura, CriterioAsignacion criterio) {
        video.asignaturaObj = asignatura;
        video.asignatura = asignatura.nombre; // nombre REAL, sin "(sugerida)"
        video.asignaturaSugerida = true;       // metadato visual
        video.criterioAsignacion = criterio;
        asignatura.fechaActualizacion = LocalDateTime.now();
        sugerirProfesorDeAsignatura(video, asignatura);
    }

    /**
     * Sugiere también el profesor de la asignatura (si lo tiene) cuando el vídeo aún no
     * tiene profesor asignado a mano. No pisa una elección manual previa.
     */
    private void sugerirProfesorDeAsignatura(Video video, Asignatura asignatura) {
        boolean videoSinProfesor = video.profesorObj == null
            && (video.profesor == null || video.profesor.isBlank()
                || video.profesor.equalsIgnoreCase("Profesor pendiente"));
        if (!videoSinProfesor) return;

        if (asignatura.profesorObj != null) {
            video.profesorObj = asignatura.profesorObj;
            video.profesor = asignatura.profesorObj.nombre;
        } else if (asignatura.profesor != null && !asignatura.profesor.isBlank()) {
            video.profesor = asignatura.profesor;
        }
    }

    /**
     * Aprendizaje suave: si la asignatura elegida no tenía canal y el vídeo sí, se le asocia
     * para que futuros vídeos del mismo canal se clasifiquen por canal.
     */
    private void aprenderCanalEnAsignatura(Asignatura asignatura, Video video) {
        if (video.canalYoutubeId != null && (asignatura.canalYoutubeId == null || asignatura.canalYoutubeId.isBlank())) {
            asignatura.canalYoutubeId = video.canalYoutubeId;
            asignatura.canalYoutubeNombre = video.canalYoutubeNombre;
            LOG.infof("[Clasificacion] Asignatura '%s' (id=%s) aprende canal id=%s",
                asignatura.nombre, asignatura.id, video.canalYoutubeId);
        }
    }

    // ── Texto y embeddings ───────────────────────────────────────────────────

    private String construirTextoVideo(Video video, Long idVideo) {
        StringBuilder sb = new StringBuilder();
        if (video.titulo != null) sb.append(video.titulo).append(". ");
        if (video.resumen != null && !video.resumen.isBlank()) sb.append(video.resumen).append(". ");

        List<ConceptoClaveVideoDTO> conceptos = conceptoRepositorio.buscarPorVideoOrdenado(idVideo);
        for (ConceptoClaveVideoDTO c : conceptos) {
            if (c.nombre() != null) sb.append(c.nombre()).append(". ");
            if (c.definicion() != null) sb.append(c.definicion()).append(" ");
        }
        List<CapituloVideoDTO> capitulos = capituloRepositorio.buscarPorVideoOrdenado(idVideo);
        for (CapituloVideoDTO cap : capitulos) {
            if (cap.titulo() != null) sb.append(cap.titulo()).append(". ");
        }
        return sb.toString().strip();
    }

    private String construirPalabrasClave(Long idVideo) {
        List<ConceptoClaveVideoDTO> conceptos = conceptoRepositorio.buscarPorVideoOrdenado(idVideo);
        List<String> nombres = new ArrayList<>();
        for (ConceptoClaveVideoDTO c : conceptos) {
            if (c.nombre() != null && !c.nombre().isBlank()) nombres.add(c.nombre().strip());
            if (nombres.size() >= 8) break;
        }
        return nombres.isEmpty() ? null : String.join(", ", nombres);
    }

    private List<Double> obtenerOcalcularEmbedding(Asignatura a) {
        if (a.embeddingResumen != null && !a.embeddingResumen.isBlank()) {
            List<Double> existente = deserializar(a.embeddingResumen);
            if (existente != null && !existente.isEmpty()) return existente;
        }
        // Calcular a partir de nombre + descripción + palabras clave y persistir para reusar.
        StringBuilder sb = new StringBuilder();
        if (a.nombre != null) sb.append(a.nombre).append(". ");
        if (a.descripcion != null && !a.descripcion.isBlank()) sb.append(a.descripcion).append(". ");
        if (a.palabrasClave != null && !a.palabrasClave.isBlank()) sb.append(a.palabrasClave);
        if (sb.toString().isBlank()) return null;
        try {
            List<Double> emb = embeddingServicio.generarEmbedding(sb.toString().strip());
            a.embeddingResumen = serializar(emb);
            return emb;
        } catch (Exception e) {
            LOG.warnf("[Clasificacion] No se pudo calcular embedding de la asignatura '%s': %s", a.nombre, e.getMessage());
            return null;
        }
    }

    private double coseno(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.isEmpty() || a.size() != b.size()) return 0.0;
        double prod = 0.0, na = 0.0, nb = 0.0;
        for (int i = 0; i < a.size(); i++) {
            double va = a.get(i), vb = b.get(i);
            prod += va * vb;
            na += va * va;
            nb += vb * vb;
        }
        if (na == 0.0 || nb == 0.0) return 0.0;
        return prod / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private String serializar(List<Double> embedding) {
        try {
            return mapeadorJson.writeValueAsString(embedding);
        } catch (Exception e) {
            return null;
        }
    }

    private List<Double> deserializar(String json) {
        try {
            Double[] arr = mapeadorJson.readValue(json, Double[].class);
            return List.of(arr);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Nombre sugerido ────────────────────────────────────────────────────────

    private String nombreSugerido(Video video, Long idVideo, AudioExtraccionServicio.MetadatosCanal canal) {
        if (canal.canalNombre() != null && !canal.canalNombre().isBlank()) {
            return "Vídeos de " + canal.canalNombre().strip();
        }
        // Categoría genérica basada en el concepto principal o el título.
        List<ConceptoClaveVideoDTO> conceptos = conceptoRepositorio.buscarPorVideoOrdenado(idVideo);
        if (!conceptos.isEmpty() && conceptos.get(0).nombre() != null && !conceptos.get(0).nombre().isBlank()) {
            return conceptos.get(0).nombre().strip();
        }
        if (video.titulo != null && !video.titulo.isBlank()) {
            String[] palabras = video.titulo.strip().split("\\s+");
            int n = Math.min(palabras.length, 4);
            return String.join(" ", java.util.Arrays.copyOfRange(palabras, 0, n));
        }
        return "Asignatura sin clasificar";
    }

    private String normalizar(String texto) {
        return Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase()
            .strip();
    }
}
