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
    public List<VideoResumenDTO> obtenerVideosPorAsignatura(Long idAsignatura) {
        List<Video> videos = videoRepositorio
            .list("asignaturaObj.id", Sort.by("fechaCreacion").descending(), idAsignatura);

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
        String nombreAsignatura = video.asignaturaObj != null
            ? video.asignaturaObj.nombre
            : (video.asignatura == null || video.asignatura.isBlank() ? "Sin asignatura" : video.asignatura);
        Long idAsignatura = video.asignaturaObj != null ? video.asignaturaObj.id : null;

        // El profesor relacionado tiene prioridad sobre el texto antiguo
        String nombreProfesor = video.profesorObj != null
            ? video.profesorObj.nombre
            : (video.profesor == null || video.profesor.isBlank() ? "Profesor pendiente" : video.profesor);
        Long idProfesor = video.profesorObj != null ? video.profesorObj.id : null;

        return new VideoResumenDTO(
            video.id,
            video.youtubeId,
            video.titulo,
            video.fechaCreacion,
            video.fuenteTranscripcion,
            numeroFragmentos,
            nombreAsignatura,
            nombreProfesor,
            video.fechaClase,
            Boolean.TRUE.equals(video.completado),
            idAsignatura,
            idProfesor,
            video.resumen,
            // El propio flag es la fuente de verdad (lo pone el clasificador y lo limpia
            // la asignación manual). No se condiciona a asignaturaObj para no enmascararlo.
            Boolean.TRUE.equals(video.asignaturaSugerida),
            video.criterioAsignacion != null ? video.criterioAsignacion.name() : null
        );
    }
}
