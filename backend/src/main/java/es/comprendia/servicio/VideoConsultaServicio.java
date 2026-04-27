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
            .map(v -> new VideoResumenDTO(
                v.id,
                v.youtubeId,
                v.titulo,
                v.fechaCreacion,
                v.fuenteTranscripcion,
                conteoPorVideo.getOrDefault(v.id, 0L)
            ))
            .toList();
    }
}
