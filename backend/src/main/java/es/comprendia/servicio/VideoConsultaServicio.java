package es.comprendia.servicio;

import es.comprendia.dto.VideoResumenDTO;
import es.comprendia.entidad.Video;
import es.comprendia.repositorio.FragmentoTranscripcionRepositorio;
import es.comprendia.repositorio.VideoRepositorio;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class VideoConsultaServicio {

    @Inject
    VideoRepositorio videoRepositorio;

    @Inject
    FragmentoTranscripcionRepositorio fragmentoRepositorio;

    @Transactional
    public List<VideoResumenDTO> obtenerVideos(int pagina, int tamanyo) {
        List<Video> videos = videoRepositorio
            .findAll(Sort.by("fechaCreacion").descending())
            .page(pagina, tamanyo)
            .list();

        if (videos.isEmpty()) {
            return List.of();
        }

        List<Long> idsVideo = videos.stream().map(v -> v.id).toList();
        Map<Long, Long> conteoPorVideo = fragmentoRepositorio.contarPorVideos(idsVideo);

        return videos.stream()
            .map(v -> convertirAResumen(v, conteoPorVideo.getOrDefault(v.id, 0L)))
            .toList();
    }

    @Transactional
    public VideoResumenDTO obtenerVideo(Long id) {
        Video video = videoRepositorio.findById(id);
        if (video == null) {
            return null;
        }

        long numeroFragmentos = fragmentoRepositorio.count("video.id", id);
        return convertirAResumen(video, numeroFragmentos);
    }

    private VideoResumenDTO convertirAResumen(Video video, long numeroFragmentos) {
        return new VideoResumenDTO(
            video.id,
            video.youtubeId,
            video.titulo,
            video.fechaCreacion,
            video.fuenteTranscripcion,
            numeroFragmentos,
            video.asignatura == null || video.asignatura.isBlank() ? "Sin asignatura" : video.asignatura,
            video.profesor == null || video.profesor.isBlank() ? "Profesor pendiente" : video.profesor,
            video.fechaClase,
            Boolean.TRUE.equals(video.completado)
        );
    }
}
