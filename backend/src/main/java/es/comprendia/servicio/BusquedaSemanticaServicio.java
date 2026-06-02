package es.comprendia.servicio;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.comprendia.dto.ResultadoBusquedaDTO;
import es.comprendia.entidad.FragmentoTranscripcion;
import es.comprendia.repositorio.FragmentoTranscripcionRepositorio;
import es.comprendia.repositorio.VideoRepositorio;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.Comparator;
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

    private final ObjectMapper mapeadorJson = new ObjectMapper();

    @Transactional
    public List<ResultadoBusquedaDTO> buscar(Long videoId, String pregunta) {
        if (videoRepositorio.findById(videoId) == null) {
            throw new NotFoundException("Vídeo con id " + videoId + " no encontrado");
        }

        List<FragmentoTranscripcion> fragmentos = fragmentoRepositorio.buscarConEmbedding(videoId);
        if (fragmentos.isEmpty()) {
            return List.of();
        }

        List<Double> embeddingPregunta = embeddingServicio.generarEmbedding(pregunta);

        return fragmentos.stream()
            .map(f -> calcularResultado(f, embeddingPregunta))
            .filter(r -> r != null)
            .sorted(Comparator.comparingDouble(ResultadoBusquedaDTO::similitud).reversed())
            .limit(MAX_RESULTADOS)
            .toList();
    }

    private ResultadoBusquedaDTO calcularResultado(FragmentoTranscripcion fragmento, List<Double> embeddingPregunta) {
        try {
            List<Double> embeddingFragmento = parsearEmbedding(fragmento.embeddingJson);
            double similitud = similitudCoseno(embeddingPregunta, embeddingFragmento);
            return new ResultadoBusquedaDTO(
                fragmento.texto,
                fragmento.tiempoInicio,
                fragmento.tiempoFin,
                fragmento.ordenFragmento,
                similitud
            );
        } catch (Exception e) {
            return null;
        }
    }

    private List<Double> parsearEmbedding(String json) {
        try {
            return mapeadorJson.readValue(json, new TypeReference<List<Double>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Error al parsear embedding JSON: " + e.getMessage(), e);
        }
    }

    private double similitudCoseno(List<Double> a, List<Double> b) {
        if (a.size() != b.size()) return 0.0;
        double productoPunto = 0.0;
        double normaA = 0.0;
        double normaB = 0.0;
        for (int i = 0; i < a.size(); i++) {
            productoPunto += a.get(i) * b.get(i);
            normaA += a.get(i) * a.get(i);
            normaB += b.get(i) * b.get(i);
        }
        if (normaA == 0.0 || normaB == 0.0) return 0.0;
        return productoPunto / (Math.sqrt(normaA) * Math.sqrt(normaB));
    }
}
