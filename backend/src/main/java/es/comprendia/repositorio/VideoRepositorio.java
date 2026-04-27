package es.comprendia.repositorio;

import es.comprendia.entidad.Video;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VideoRepositorio implements PanacheRepository<Video> {
}
