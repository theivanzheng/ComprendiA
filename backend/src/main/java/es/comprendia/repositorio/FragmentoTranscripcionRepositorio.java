package es.comprendia.repositorio;

import es.comprendia.dto.FragmentoDTO;
import es.comprendia.dto.ResultadoBusquedaDTO;
import es.comprendia.entidad.FragmentoTranscripcion;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class FragmentoTranscripcionRepositorio implements PanacheRepository<FragmentoTranscripcion> {

    private static final Logger LOG = Logger.getLogger(FragmentoTranscripcionRepositorio.class);

    @SuppressWarnings("unchecked")
    public Map<Long, Long> contarPorVideos(List<Long> idsVideo) {
        if (idsVideo.isEmpty()) {
            return Map.of();
        }
        List<Object[]> filas = (List<Object[]>) getEntityManager()
            .createQuery(
                "SELECT f.video.id, COUNT(f) FROM FragmentoTranscripcion f " +
                "WHERE f.video.id IN :ids GROUP BY f.video.id")
            .setParameter("ids", idsVideo)
            .getResultList();

        Map<Long, Long> resultado = new HashMap<>();
        for (Object[] fila : filas) {
            resultado.put((Long) fila[0], (Long) fila[1]);
        }
        return resultado;
    }

    // Devuelve solo texto y tiempos, sin cargar el embedding de la BD
    @SuppressWarnings("unchecked")
    public List<FragmentoDTO> buscarPorVideoOrdenado(Long idVideo) {
        return getEntityManager()
            .createQuery(
                "SELECT new es.comprendia.dto.FragmentoDTO(f.texto, f.tiempoInicio, f.tiempoFin, f.ordenFragmento) " +
                "FROM FragmentoTranscripcion f WHERE f.video.id = :id ORDER BY f.ordenFragmento",
                FragmentoDTO.class)
            .setParameter("id", idVideo)
            .getResultList();
    }

    // Búsqueda semántica con pgvector: calcula distancia coseno en SQL y devuelve los más similares
    @SuppressWarnings("unchecked")
    public List<ResultadoBusquedaDTO> buscarPorSimilitud(Long idVideo, String embeddingPregunta, int limite) {
        List<Object[]> filas = getEntityManager()
            .createNativeQuery(
                "SELECT texto, tiempo_inicio, tiempo_fin, orden_fragmento, " +
                "       1 - (embedding_json <=> CAST(:emb AS vector)) AS similitud " +
                "FROM fragmentos_transcripcion " +
                "WHERE video_id = :videoId AND embedding_json IS NOT NULL " +
                "ORDER BY embedding_json <=> CAST(:emb AS vector) " +
                "LIMIT :limite")
            .setParameter("emb", embeddingPregunta)
            .setParameter("videoId", idVideo)
            .setParameter("limite", limite)
            .getResultList();

        return filas.stream()
            .map(fila -> new ResultadoBusquedaDTO(
                (String)  fila[0],
                ((Number) fila[1]).doubleValue(),
                ((Number) fila[2]).doubleValue(),
                ((Number) fila[3]).intValue(),
                ((Number) fila[4]).doubleValue()
            ))
            .toList();
    }

    @Transactional
    public void actualizarEmbedding(Long idFragmento, String embeddingJson) {
        LOG.infof("[Embedding] Guardando embedding para fragmento id=%s", idFragmento);
        int filas = getEntityManager()
            .createNativeQuery(
                "UPDATE fragmentos_transcripcion SET embedding_json = CAST(:ej AS vector) WHERE id = :id")
            .setParameter("ej", embeddingJson)
            .setParameter("id", idFragmento)
            .executeUpdate();
        if (filas == 0) {
            LOG.errorf("[Embedding] Fragmento id=%s no encontrado — embedding no guardado", idFragmento);
        }
    }
}
