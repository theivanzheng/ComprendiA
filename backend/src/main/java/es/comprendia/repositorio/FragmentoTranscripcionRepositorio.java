package es.comprendia.repositorio;

import es.comprendia.entidad.FragmentoTranscripcion;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.panache.common.Sort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class FragmentoTranscripcionRepositorio implements PanacheRepository<FragmentoTranscripcion> {

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
}
