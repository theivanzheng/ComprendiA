package es.comprendia.repositorio;

import es.comprendia.dto.ConceptoClaveVideoDTO;
import es.comprendia.entidad.ConceptoClaveVideo;
import es.comprendia.entidad.Video;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class ConceptoClaveVideoRepositorio implements PanacheRepository<ConceptoClaveVideo> {

    public List<ConceptoClaveVideoDTO> buscarPorVideoOrdenado(Long idVideo) {
        return getEntityManager()
            .createQuery(
                "SELECT new es.comprendia.dto.ConceptoClaveVideoDTO(c.nombre, c.definicion, " +
                    "c.tiempoInicio, c.ordenConcepto) " +
                    "FROM ConceptoClaveVideo c WHERE c.video.id = :id ORDER BY c.ordenConcepto",
                ConceptoClaveVideoDTO.class)
            .setParameter("id", idVideo)
            .getResultList();
    }

    @Transactional
    public void reemplazar(Long idVideo, List<ConceptoClaveVideoDTO> conceptos) {
        delete("video.id", idVideo);
        Video video = getEntityManager().getReference(Video.class, idVideo);

        for (ConceptoClaveVideoDTO dto : conceptos) {
            ConceptoClaveVideo concepto = new ConceptoClaveVideo();
            concepto.video = video;
            concepto.nombre = dto.nombre();
            concepto.definicion = dto.definicion();
            concepto.tiempoInicio = dto.tiempoInicio();
            concepto.ordenConcepto = dto.orden();
            persist(concepto);
        }
    }
}
