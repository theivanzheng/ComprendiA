package es.comprendia.servicio;

import es.comprendia.dto.FragmentoTranscripcionDTO;
import es.comprendia.dto.RespuestaTranscripcionDTO;
import es.comprendia.excepcion.ExcepcionTranscripcionYoutube;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class TranscripcionYoutubeServicio {

    private static final Set<String> DOMINIOS_YOUTUBE = Set.of(
        "youtube.com", "www.youtube.com", "youtu.be"
    );

    @Inject
    YoutubeTranscripcionServicio youtubeTranscripcionServicio;

    @Inject
    PipelineWhisperServicio pipelineWhisperServicio;

    @Inject
    TranscripcionPersistenciaServicio transcripcionPersistenciaServicio;

    @Inject
    @ConfigProperty(name = "comprendia.transcripcion.modo", defaultValue = "simulada")
    String modoTranscripcion;

    public RespuestaTranscripcionDTO procesarUrlYoutube(String urlVideo) {
        validarUrlYoutube(urlVideo);
        String idVideo = extraerIdVideo(urlVideo);

        RespuestaTranscripcionDTO respuesta = switch (modoTranscripcion) {
            case "scraping" -> procesarConScraping(idVideo);
            case "whisper"  -> pipelineWhisperServicio.transcribirDesdeAudio(idVideo);
            default         -> construirTranscripcionSimulada(idVideo);
        };

        transcripcionPersistenciaServicio.guardarTranscripcion(respuesta);
        return respuesta;
    }

    private RespuestaTranscripcionDTO procesarConScraping(String idVideo) {
        try {
            return youtubeTranscripcionServicio.obtenerTranscripcion(idVideo);
        } catch (ExcepcionTranscripcionYoutube e) {
            // Sin subtítulos disponibles: fallback a simulada
            return construirTranscripcionSimulada(idVideo);
        }
    }

    private void validarUrlYoutube(String urlVideo) {
        if (urlVideo == null || urlVideo.isBlank()) {
            throw new IllegalArgumentException("La URL del vídeo no puede estar vacía");
        }
        try {
            String host = new URI(urlVideo).getHost();
            if (host == null || !DOMINIOS_YOUTUBE.contains(host)) {
                throw new IllegalArgumentException("La URL no corresponde a un vídeo de YouTube");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("La URL no tiene un formato válido");
        }
    }

    private String extraerIdVideo(String urlVideo) {
        if (urlVideo.contains("youtube.com/watch?v=")) {
            int inicio = urlVideo.indexOf("v=") + 2;
            int fin = urlVideo.indexOf('&', inicio);
            return fin == -1 ? urlVideo.substring(inicio) : urlVideo.substring(inicio, fin);
        }
        if (urlVideo.contains("youtu.be/")) {
            int inicio = urlVideo.indexOf("youtu.be/") + 9;
            int fin = urlVideo.indexOf('?', inicio);
            return fin == -1 ? urlVideo.substring(inicio) : urlVideo.substring(inicio, fin);
        }
        throw new IllegalArgumentException("Formato de URL de YouTube no reconocido");
    }

    private RespuestaTranscripcionDTO construirTranscripcionSimulada(String idVideo) {
        List<FragmentoTranscripcionDTO> fragmentos = List.of(
            new FragmentoTranscripcionDTO("Bienvenido a esta clase simulada.", 0.0, 10.0),
            new FragmentoTranscripcionDTO("En esta parte se introduce el concepto principal.", 10.0, 25.0),
            new FragmentoTranscripcionDTO("Ahora vemos un ejemplo práctico del tema.", 25.0, 45.0),
            new FragmentoTranscripcionDTO("Resumen y conclusiones de la clase.", 45.0, 60.0)
        );
        return new RespuestaTranscripcionDTO(idVideo, "Clase simulada de ComprendiA", fragmentos, "SIMULADA");
    }
}
