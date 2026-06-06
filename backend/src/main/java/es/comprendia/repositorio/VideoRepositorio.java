package es.comprendia.repositorio;

import es.comprendia.entidad.Video;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class VideoRepositorio implements PanacheRepository<Video> {

    /** Actualiza el resumen generado para una clase. */
    @Transactional
    public void actualizarResumen(Long idVideo, String resumen) {
        update("resumen = ?1 where id = ?2", resumen, idVideo);
    }
}
