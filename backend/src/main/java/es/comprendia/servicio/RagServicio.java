package es.comprendia.servicio;

import es.comprendia.dto.CapituloVideoDTO;
import es.comprendia.dto.ConceptoClaveVideoDTO;
import es.comprendia.dto.ConsultaConversacionDTO;
import es.comprendia.dto.FragmentoDTO;
import es.comprendia.dto.MensajeChatDTO;
import es.comprendia.dto.RespuestaRagDTO;
import es.comprendia.dto.ResultadoBusquedaDTO;
import es.comprendia.entidad.Video;
import es.comprendia.repositorio.FragmentoTranscripcionRepositorio;
import es.comprendia.repositorio.CapituloVideoRepositorio;
import es.comprendia.repositorio.ConceptoClaveVideoRepositorio;
import es.comprendia.repositorio.VideoRepositorio;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class RagServicio {

    private static final Logger LOG = Logger.getLogger(RagServicio.class);
    private static final int NUM_FRAGMENTOS = 5;
    private static final int NUM_FRAGMENTOS_GLOBALES = 12;

    private static final String SISTEMA_GLOBAL =
        "Eres un asistente academico para una plataforma educativa. " +
        "Responde a preguntas globales sobre una clase ya procesada. " +
        "Usa titulo, capitulos, conceptos clave y extractos representativos. " +
        "No te limites a copiar fragmentos: sintetiza. " +
        "La respuesta debe ser breve, clara y util para un alumno. " +
        "No uses Markdown, encabezados con #, negritas ni numeraciones largas. " +
        "Usa exactamente estos cuatro apartados en lineas separadas: " +
        "De que trata: una o dos frases. " +
        "Que se consigue: una frase. " +
        "Pasos: tres o cuatro pasos maximo, separados por punto y coma. " +
        "Que muestra: una frase. " +
        "Maximo 140 palabras. " +
        "Si algo no aparece en el contexto, dilo con prudencia. Responde en el idioma de la pregunta.";

    @Inject
    VideoRepositorio videoRepositorio;

    @Inject
    FragmentoTranscripcionRepositorio fragmentoRepositorio;

    @Inject
    CapituloVideoRepositorio capituloRepositorio;

    @Inject
    ConceptoClaveVideoRepositorio conceptoRepositorio;

    @Inject
    EmbeddingServicio embeddingServicio;

    @Inject
    ChatGptServicio chatGptServicio;

    @Transactional
    public RespuestaRagDTO responder(Long videoId, String pregunta) {
        Video video = videoRepositorio.findById(videoId);
        if (video == null) {
            throw new NotFoundException("Vídeo con id " + videoId + " no encontrado");
        }

        if (esPreguntaGlobal(pregunta)) {
            return responderGlobal(video, pregunta);
        }

        return responderLocal(videoId, pregunta);
    }

    private RespuestaRagDTO responderLocal(Long videoId, String pregunta) {
        List<Double> vectorPregunta = embeddingServicio.generarEmbedding(pregunta);
        String embeddingStr = vectorPregunta.toString().replace(" ", "");
        List<ResultadoBusquedaDTO> fuentes = fragmentoRepositorio.buscarPorSimilitud(videoId, embeddingStr, NUM_FRAGMENTOS);

        if (fuentes.isEmpty()) {
            return new RespuestaRagDTO("No hay fragmentos con embedding disponibles para este vídeo.", fuentes);
        }

        String contexto = construirContexto(fuentes);
        LOG.infof("[RAG] Pregunta local. Contexto construido con %d fragmentos para: %s", fuentes.size(), pregunta);

        String respuesta = chatGptServicio.completar(contexto, pregunta);
        return new RespuestaRagDTO(respuesta, fuentes);
    }

    /**
     * Respuesta conversacional sobre el vídeo. Usa el historial reciente (memoria corta enviada
     * por el frontend, no persistida) y una entidad reciente para resolver referencias implícitas.
     * El historial NO se guarda en backend.
     */
    @Transactional
    public RespuestaRagDTO responderConversacion(Long videoId, ConsultaConversacionDTO consulta) {
        Video video = videoRepositorio.findById(videoId);
        if (video == null) {
            throw new NotFoundException("Vídeo con id " + videoId + " no encontrado");
        }

        String pregunta = consulta.pregunta();
        String entidadReciente = consulta.entidadReciente();
        List<MensajeChatDTO> historial = consulta.historial() == null ? List.of() : consulta.historial();

        // Las preguntas claramente globales (resumen, de qué trata) no necesitan hilo conversacional.
        if (esPreguntaGlobal(pregunta)) {
            return responderGlobal(video, pregunta);
        }

        // Recuperación semántica contextual: la entidad reciente solo se usa si la pregunta es una
        // referencia implícita; si trae sujeto propio nuevo, no se arrastra (evita contaminación).
        String entidadEfectiva = entidadAplicable(pregunta, entidadReciente);
        String textoBusqueda = entidadEfectiva == null ? pregunta : pregunta + " " + entidadEfectiva;
        List<Double> vectorPregunta = embeddingServicio.generarEmbedding(textoBusqueda);
        String embeddingStr = vectorPregunta.toString().replace(" ", "");
        List<ResultadoBusquedaDTO> fuentes = fragmentoRepositorio.buscarPorSimilitud(videoId, embeddingStr, NUM_FRAGMENTOS);

        if (fuentes.isEmpty()) {
            return new RespuestaRagDTO("Todavía no tengo la transcripción procesada para responder sobre este vídeo.", fuentes);
        }

        String contexto = construirContexto(fuentes);
        LOG.infof("[RAG] Conversacion. %d fragmentos, %d turnos previos, entidad aplicada='%s' para: %s",
            fuentes.size(), historial.size(), entidadEfectiva, pregunta);

        String respuesta = chatGptServicio.completarConversacion(contexto, pregunta, historial, entidadEfectiva);
        return new RespuestaRagDTO(respuesta, fuentes);
    }

    /** Resultado de la recuperación: el contexto (extractos) y las fuentes (momentos del vídeo). */
    public record PreparacionRag(String contexto, List<ResultadoBusquedaDTO> fuentes, String entidadEfectiva) {}

    /**
     * Fase de RECUPERACIÓN del RAG, separada de la generación. Hace la búsqueda semántica y
     * devuelve el contexto y las fuentes, SIN llamar a la LLM. Se usa en el chat por WebSocket:
     * la recuperación va en transacción (acceso a BD) y la generación en streaming va fuera.
     */
    @Transactional
    public PreparacionRag prepararConversacion(Long videoId, ConsultaConversacionDTO consulta) {
        Video video = videoRepositorio.findById(videoId);
        if (video == null) {
            throw new NotFoundException("Vídeo con id " + videoId + " no encontrado");
        }
        String entidadEfectiva = entidadAplicable(consulta.pregunta(), consulta.entidadReciente());
        String textoBusqueda = entidadEfectiva == null
            ? consulta.pregunta() : consulta.pregunta() + " " + entidadEfectiva;
        List<Double> vectorPregunta = embeddingServicio.generarEmbedding(textoBusqueda);
        String embeddingStr = vectorPregunta.toString().replace(" ", "");
        List<ResultadoBusquedaDTO> fuentes = fragmentoRepositorio.buscarPorSimilitud(videoId, embeddingStr, NUM_FRAGMENTOS);
        String contexto = fuentes.isEmpty() ? "" : construirContexto(fuentes);
        LOG.infof("[RAG-WS] Recuperados %d fragmentos (entidad aplicada='%s') para: %s",
            fuentes.size(), entidadEfectiva, consulta.pregunta());
        return new PreparacionRag(contexto, fuentes, entidadEfectiva);
    }

    /**
     * Decide el texto que se usa para la búsqueda semántica. Si la pregunta es corta o parece una
     * referencia implícita (poco contenido propio), se le añade la entidad reciente.
     */
    /**
     * Decide si una pregunta es una REFERENCIA IMPLÍCITA al tema anterior (un pronombre o una
     * pregunta muy corta sin sujeto propio: "¿y el móvil?", "¿cuánto costaba?", "¿y después?").
     * Solo en ese caso tiene sentido arrastrar la entidad reciente. Si la pregunta introduce un
     * sujeto nuevo ("¿de qué marcas de zapatillas habla?"), NO es implícita.
     */
    private boolean esReferenciaImplicita(String pregunta) {
        String n = normalizar(pregunta).trim();
        if (n.isEmpty()) return false;
        int palabras = n.split("\\s+").length;
        if (palabras <= 4) return true; // pregunta muy corta: casi seguro alude a lo anterior
        return contiene(n, "ese ", "esa ", "eso", "este ", "esta ", "esos ", "esas ",
            "el movil", "lo anterior", "lo mismo", "y despues", "y luego", "y antes");
    }

    /** Devuelve la entidad reciente SOLO si la pregunta es una referencia implícita; si no, null. */
    private String entidadAplicable(String pregunta, String entidadReciente) {
        if (entidadReciente == null || entidadReciente.isBlank()) return null;
        return esReferenciaImplicita(pregunta) ? entidadReciente : null;
    }

    private String construirTextoBusqueda(String pregunta, String entidadReciente) {
        String entidad = entidadAplicable(pregunta, entidadReciente);
        return entidad == null ? pregunta : pregunta + " " + entidad;
    }

    private RespuestaRagDTO responderGlobal(Video video, String pregunta) {
        List<CapituloVideoDTO> capitulos = capituloRepositorio.buscarPorVideoOrdenado(video.id);
        List<ConceptoClaveVideoDTO> conceptos = conceptoRepositorio.buscarPorVideoOrdenado(video.id);
        List<FragmentoDTO> fragmentos = fragmentoRepositorio.buscarPorVideoOrdenado(video.id);
        List<FragmentoDTO> muestra = seleccionarFragmentosRepresentativos(fragmentos, NUM_FRAGMENTOS_GLOBALES);

        if (fragmentos.isEmpty() && capitulos.isEmpty() && conceptos.isEmpty()) {
            return new RespuestaRagDTO("No hay suficiente informacion procesada para resumir esta clase.", List.of());
        }

        String contexto = construirContextoGlobal(video, capitulos, conceptos, muestra);
        String usuario = "Contexto global de la clase:\n" + contexto + "\n\nPregunta: " + pregunta;
        LOG.infof("[RAG] Pregunta global. Contexto: %d capitulos, %d conceptos, %d extractos para video id=%s",
            capitulos.size(), conceptos.size(), muestra.size(), video.id);

        String respuesta = chatGptServicio.completarPersonalizado(SISTEMA_GLOBAL, usuario, 380, 0.2);
        return new RespuestaRagDTO(respuesta, convertirFuentes(muestra));
    }

    private String construirContextoGlobal(
        Video video,
        List<CapituloVideoDTO> capitulos,
        List<ConceptoClaveVideoDTO> conceptos,
        List<FragmentoDTO> fragmentos) {

        StringBuilder sb = new StringBuilder();
        sb.append("Titulo: ").append(video.titulo).append("\n");
        sb.append("Fuente: ").append(video.fuenteTranscripcion == null ? "desconocida" : video.fuenteTranscripcion).append("\n\n");

        sb.append("Capitulos detectados:\n");
        if (capitulos.isEmpty()) {
            sb.append("- No hay capitulos persistidos.\n");
        } else {
            for (CapituloVideoDTO capitulo : capitulos) {
                sb.append("- [").append(formatearTiempo(capitulo.tiempoInicio())).append("] ")
                    .append(capitulo.titulo());
                if (capitulo.descripcion() != null && !capitulo.descripcion().isBlank()) {
                    sb.append(": ").append(capitulo.descripcion());
                }
                sb.append("\n");
            }
        }

        sb.append("\nConceptos clave:\n");
        if (conceptos.isEmpty()) {
            sb.append("- No hay conceptos persistidos.\n");
        } else {
            for (ConceptoClaveVideoDTO concepto : conceptos) {
                sb.append("- [").append(formatearTiempo(concepto.tiempoInicio())).append("] ")
                    .append(concepto.nombre()).append(": ").append(concepto.definicion()).append("\n");
            }
        }

        sb.append("\nExtractos representativos ordenados:\n");
        for (FragmentoDTO fragmento : fragmentos) {
            sb.append("- [").append(formatearTiempo(fragmento.tiempoInicio())).append(" - ")
                .append(formatearTiempo(fragmento.tiempoFin())).append("] ")
                .append(fragmento.texto()).append("\n");
        }
        return sb.toString();
    }

    private String construirContexto(List<ResultadoBusquedaDTO> fuentes) {
        // No se numeran los extractos para que el modelo no cite "fragmento [1]".
        // Solo se aporta el rango de tiempo en formato m:ss y el texto.
        StringBuilder sb = new StringBuilder();
        for (ResultadoBusquedaDTO f : fuentes) {
            sb.append("(").append(formatearTiempo(f.tiempoInicio())).append(" - ")
              .append(formatearTiempo(f.tiempoFin())).append(") ")
              .append(f.texto())
              .append("\n");
        }
        return sb.toString();
    }

    private List<FragmentoDTO> seleccionarFragmentosRepresentativos(List<FragmentoDTO> fragmentos, int limite) {
        if (fragmentos.size() <= limite) {
            return fragmentos;
        }

        Set<Integer> indices = new LinkedHashSet<>();
        double paso = (double) (fragmentos.size() - 1) / (limite - 1);
        for (int i = 0; i < limite; i++) {
            indices.add((int) Math.round(i * paso));
        }

        for (int i = 0; indices.size() < limite && i < fragmentos.size(); i++) {
            indices.add(i);
        }

        List<FragmentoDTO> seleccionados = new ArrayList<>();
        for (Integer indice : indices) {
            seleccionados.add(fragmentos.get(indice));
        }
        seleccionados.sort((a, b) -> Integer.compare(a.orden(), b.orden()));
        return seleccionados;
    }

    private List<ResultadoBusquedaDTO> convertirFuentes(List<FragmentoDTO> fragmentos) {
        return fragmentos.stream()
            .map(fragmento -> new ResultadoBusquedaDTO(
                fragmento.texto(),
                fragmento.tiempoInicio(),
                fragmento.tiempoFin(),
                fragmento.orden(),
                1.0
            ))
            .toList();
    }

    private boolean esPreguntaGlobal(String pregunta) {
        String texto = normalizar(pregunta);
        if (contiene(texto, "este concepto", "este termino", "que significa", "por que", "como se calcula",
            "donde dice", "en que minuto")) {
            return false;
        }
        return contiene(texto,
            "resume", "resumen", "sintetiza", "sinopsis", "de que trata", "que trata",
            "tema principal", "puntos principales", "ideas principales", "conceptos principales",
            "estructura", "que se explica", "que se ve", "como empieza", "como termina",
            "conclusion", "conclusiones", "overview", "summarize", "summary",
            "what is this video about", "main points");
    }

    private boolean contiene(String texto, String... patrones) {
        for (String patron : patrones) {
            if (texto.contains(patron)) {
                return true;
            }
        }
        return false;
    }

    private String normalizar(String texto) {
        return Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase();
    }

    private String formatearTiempo(Double segundos) {
        if (segundos == null) return "?:??";
        int m = segundos.intValue() / 60;
        int s = segundos.intValue() % 60;
        return String.format("%d:%02d", m, s);
    }
}
