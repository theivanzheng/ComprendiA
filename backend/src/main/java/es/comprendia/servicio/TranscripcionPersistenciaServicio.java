package es.comprendia.servicio;

import es.comprendia.dto.FragmentoTranscripcionDTO;
import es.comprendia.dto.RespuestaTranscripcionDTO;
import es.comprendia.entidad.FragmentoTranscripcion;
import es.comprendia.entidad.Video;
import es.comprendia.repositorio.FragmentoTranscripcionRepositorio;
import es.comprendia.repositorio.VideoRepositorio;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class TranscripcionPersistenciaServicio {

    private static final Logger LOG = Logger.getLogger(TranscripcionPersistenciaServicio.class);

    @Inject
    VideoRepositorio videoRepositorio;

    @Inject
    FragmentoTranscripcionRepositorio fragmentoRepositorio;

    @Inject
    EntityManager entityManager;

    @Transactional
    public List<FragmentoTranscripcion> guardarTranscripcion(RespuestaTranscripcionDTO respuesta) {
        LOG.info("[Estado] Guardando vídeo y fragmentos en PostgreSQL");
        long inicio = System.currentTimeMillis();
        Video video = new Video();
        video.youtubeId = respuesta.getIdVideo();
        video.titulo = respuesta.getTitulo();
        video.fuenteTranscripcion = respuesta.getFuenteTranscripcion();
        video.fechaCreacion = LocalDateTime.now();
        video.asignatura = "Sin asignatura";
        video.profesor = "Profesor pendiente";
        video.fechaClase = LocalDate.now();
        video.completado = false;
        videoRepositorio.persist(video);
        videoRepositorio.flush();
        respuesta.setIdTranscripcion(video.id);

        List<FragmentoTranscripcion> fragmentosGuardados = new ArrayList<>();
        int orden = 0;
        for (FragmentoTranscripcionDTO dto : respuesta.getFragmentos()) {
            FragmentoTranscripcion fragmento = new FragmentoTranscripcion();
            fragmento.video = video;
            fragmento.texto = dto.getTexto();
            fragmento.tiempoInicio = dto.getTiempoInicio();
            fragmento.tiempoFin = dto.getTiempoFin();
            fragmento.ordenFragmento = orden++;
            fragmentoRepositorio.persist(fragmento);
            fragmentosGuardados.add(fragmento);
        }

        LOG.infof("[Tiempo] Persistencia completada en %d ms — %d fragmentos, primer id=%s",
            System.currentTimeMillis() - inicio,
            fragmentosGuardados.size(),
            fragmentosGuardados.isEmpty() ? "ninguno" : fragmentosGuardados.get(0).id);
        return fragmentosGuardados;
    }

    @Transactional
    public void eliminarVideoCompleto(Long idVideo) {
        if (idVideo == null) return;

        LOG.infof("[Cancelacion] Eliminando datos persistidos del video id=%s", idVideo);
        entityManager.createQuery("DELETE FROM ConceptoClaveVideo c WHERE c.video.id = :id")
            .setParameter("id", idVideo)
            .executeUpdate();
        entityManager.createQuery("DELETE FROM CapituloVideo c WHERE c.video.id = :id")
            .setParameter("id", idVideo)
            .executeUpdate();
        entityManager.createQuery("DELETE FROM FragmentoTranscripcion f WHERE f.video.id = :id")
            .setParameter("id", idVideo)
            .executeUpdate();

        Video video = videoRepositorio.findById(idVideo);
        if (video != null) {
            videoRepositorio.delete(video);
        }
    }
}
