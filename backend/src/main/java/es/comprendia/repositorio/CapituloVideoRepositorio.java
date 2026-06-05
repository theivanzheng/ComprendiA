package es.comprendia.repositorio;

import es.comprendia.dto.CapituloVideoDTO;
import es.comprendia.entidad.CapituloVideo;
import es.comprendia.entidad.Video;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class CapituloVideoRepositorio implements PanacheRepository<CapituloVideo> {

    public List<CapituloVideoDTO> buscarPorVideoOrdenado(Long idVideo) {
        return getEntityManager()
            .createQuery(
                "SELECT new es.comprendia.dto.CapituloVideoDTO(c.id, c.titulo, c.descripcion, " +
                    "c.tiempoInicio, c.tiempoFin, c.ordenCapitulo, c.origen, c.creadoManual, c.generadoPorIa) " +
                    "FROM CapituloVideo c WHERE c.video.id = :id " +
                    "ORDER BY c.tiempoInicio ASC, c.ordenCapitulo ASC, c.id ASC",
                CapituloVideoDTO.class)
            .setParameter("id", idVideo)
            .getResultList();
    }

    @Transactional
    public void reemplazar(Long idVideo, List<CapituloVideoDTO> capitulos) {
        delete("video.id", idVideo);
        Video video = getEntityManager().getReference(Video.class, idVideo);

        for (CapituloVideoDTO dto : capitulos) {
            CapituloVideo capitulo = new CapituloVideo();
            capitulo.video = video;
            capitulo.titulo = dto.titulo();
            capitulo.descripcion = dto.descripcion();
            capitulo.tiempoInicio = dto.tiempoInicio();
            capitulo.tiempoFin = dto.tiempoFin();
            capitulo.ordenCapitulo = dto.orden();
            capitulo.origen = dto.origen();
            // Si el DTO no especifica los flags, se infieren del origen
            boolean manual = Boolean.TRUE.equals(dto.creadoManual()) || "MANUAL".equalsIgnoreCase(dto.origen());
            capitulo.creadoManual = manual;
            capitulo.generadoPorIa = dto.generadoPorIa() != null ? dto.generadoPorIa() : !manual;
            persist(capitulo);
        }
    }
}
