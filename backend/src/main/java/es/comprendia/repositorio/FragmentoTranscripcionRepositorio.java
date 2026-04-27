package es.comprendia.repositorio;

import es.comprendia.entidad.FragmentoTranscripcion;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.panache.common.Sort;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

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

    public List<FragmentoTranscripcion> buscarPorVideoOrdenado(Long idVideo) {
        return find("video.id = ?1", Sort.by("ordenFragmento"), idVideo).list();
    }

    @Transactional
    public void actualizarEmbedding(Long idFragmento, String embeddingJson) {
        LOG.infof("[Embedding] actualizarEmbedding iniciado para fragmento id=%s", idFragmento);
        int filasActualizadas = getEntityManager()
            .createQuery(
                "UPDATE FragmentoTranscripcion f SET f.embeddingJson = :ej WHERE f.id = :id")
            .setParameter("ej", embeddingJson)
            .setParameter("id", idFragmento)
            .executeUpdate();
        if (filasActualizadas == 0) {
            LOG.errorf("[Embedding] No se encontró el fragmento id=%s en la BD — embedding no guardado", idFragmento);
        } else {
            LOG.infof("[Embedding] Embedding guardado correctamente para fragmento id=%s (%d fila actualizada)",
                idFragmento, filasActualizadas);
        }
    }
}
