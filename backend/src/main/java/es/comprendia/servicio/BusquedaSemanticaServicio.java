package es.comprendia.servicio;

import es.comprendia.dto.ResultadoBusquedaDTO;
import es.comprendia.repositorio.FragmentoTranscripcionRepositorio;
import es.comprendia.repositorio.VideoRepositorio;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

@ApplicationScoped
public class BusquedaSemanticaServicio {

    private static final int MAX_RESULTADOS = 5;

    @Inject
    VideoRepositorio videoRepositorio;

    @Inject
    FragmentoTranscripcionRepositorio fragmentoRepositorio;

    @Inject
    EmbeddingServicio embeddingServicio;

    @Transactional
    public List<ResultadoBusquedaDTO> buscar(Long videoId, String pregunta) {
        if (videoRepositorio.findById(videoId) == null) {
            throw new NotFoundException("Vídeo con id " + videoId + " no encontrado");
        }

        List<Double> vectorPregunta = embeddingServicio.generarEmbedding(pregunta);
        String embeddingStr = vectorPregunta.toString().replace(" ", "");

        return fragmentoRepositorio.buscarPorSimilitud(videoId, embeddingStr, MAX_RESULTADOS);
    }
}
