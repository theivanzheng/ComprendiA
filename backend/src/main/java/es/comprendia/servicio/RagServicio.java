package es.comprendia.servicio;

import es.comprendia.dto.RespuestaRagDTO;
import es.comprendia.dto.ResultadoBusquedaDTO;
import es.comprendia.repositorio.FragmentoTranscripcionRepositorio;
import es.comprendia.repositorio.VideoRepositorio;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class RagServicio {

    private static final Logger LOG = Logger.getLogger(RagServicio.class);
    private static final int NUM_FRAGMENTOS = 5;

    @Inject
    VideoRepositorio videoRepositorio;

    @Inject
    FragmentoTranscripcionRepositorio fragmentoRepositorio;

    @Inject
    EmbeddingServicio embeddingServicio;

    @Inject
    ChatGptServicio chatGptServicio;

    @Transactional
    public RespuestaRagDTO responder(Long videoId, String pregunta) {
        if (videoRepositorio.findById(videoId) == null) {
            throw new NotFoundException("Vídeo con id " + videoId + " no encontrado");
        }

        // 1. Recuperar fragmentos más relevantes con pgvector
        List<Double> vectorPregunta = embeddingServicio.generarEmbedding(pregunta);
        String embeddingStr = vectorPregunta.toString().replace(" ", "");
        List<ResultadoBusquedaDTO> fuentes = fragmentoRepositorio.buscarPorSimilitud(videoId, embeddingStr, NUM_FRAGMENTOS);

        if (fuentes.isEmpty()) {
            return new RespuestaRagDTO("No hay fragmentos con embedding disponibles para este vídeo.", fuentes);
        }

        // 2. Construir el contexto con los fragmentos recuperados
        String contexto = construirContexto(fuentes);
        LOG.infof("[RAG] Contexto construido con %d fragmentos para la pregunta: %s", fuentes.size(), pregunta);

        // 3. Llamar a GPT con el contexto y la pregunta
        String respuesta = chatGptServicio.completar(contexto, pregunta);

        return new RespuestaRagDTO(respuesta, fuentes);
    }

    private String construirContexto(List<ResultadoBusquedaDTO> fuentes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fuentes.size(); i++) {
            ResultadoBusquedaDTO f = fuentes.get(i);
            sb.append("[").append(i + 1).append("] ")
              .append("[").append(formatearTiempo(f.tiempoInicio())).append(" - ")
              .append(formatearTiempo(f.tiempoFin())).append("] ")
              .append(f.texto())
              .append("\n");
        }
        return sb.toString();
    }

    private String formatearTiempo(double segundos) {
        int m = (int) segundos / 60;
        int s = (int) segundos % 60;
        return String.format("%d:%02d", m, s);
    }
}
