package es.comprendia.servicio;

import es.comprendia.dto.FragmentoTranscripcionDTO;
import es.comprendia.dto.RespuestaTranscripcionDTO;
import es.comprendia.entidad.FragmentoTranscripcion;
import es.comprendia.entidad.Video;
import es.comprendia.repositorio.FragmentoTranscripcionRepositorio;
import es.comprendia.repositorio.VideoRepositorio;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;

@ApplicationScoped
public class TranscripcionPersistenciaServicio {

    @Inject
    VideoRepositorio videoRepositorio;

    @Inject
    FragmentoTranscripcionRepositorio fragmentoRepositorio;

    @Transactional
    public Video guardarTranscripcion(RespuestaTranscripcionDTO respuesta) {
        Video video = new Video();
        video.youtubeId = respuesta.getIdVideo();
        video.titulo = respuesta.getTitulo();
        video.fuenteTranscripcion = respuesta.getFuenteTranscripcion();
        video.fechaCreacion = LocalDateTime.now();
        videoRepositorio.persist(video);

        int orden = 0;
        for (FragmentoTranscripcionDTO dto : respuesta.getFragmentos()) {
            FragmentoTranscripcion fragmento = new FragmentoTranscripcion();
            fragmento.video = video;
            fragmento.texto = dto.getTexto();
            fragmento.tiempoInicio = dto.getTiempoInicio();
            fragmento.tiempoFin = dto.getTiempoFin();
            fragmento.ordenFragmento = orden++;
            fragmentoRepositorio.persist(fragmento);
        }

        return video;
    }
}
