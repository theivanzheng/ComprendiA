package es.comprendia.repositorio;

import es.comprendia.entidad.DocumentoClase;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class DocumentoClaseRepositorio implements PanacheRepository<DocumentoClase> {

    /** Documentos de una clase/vídeo, del más reciente al más antiguo. */
    public List<DocumentoClase> listarPorVideo(Long idVideo) {
        return list("video.id", Sort.by("fechaSubida").descending(), idVideo);
    }
}
