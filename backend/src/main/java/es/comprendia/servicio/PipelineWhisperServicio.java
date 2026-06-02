package es.comprendia.servicio;

import es.comprendia.dto.FragmentoTranscripcionDTO;
import es.comprendia.dto.RespuestaTranscripcionDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@ApplicationScoped
public class PipelineWhisperServicio {

    private static final Logger LOG = Logger.getLogger(PipelineWhisperServicio.class);

    @Inject
    AudioExtraccionServicio audioExtraccionServicio;

    @Inject
    TranscripcionAudioWhisperServicio transcripcionAudioWhisperServicio;

    // Orquesta la descarga de audio y la transcripción. Borra el archivo temporal al terminar.
    public RespuestaTranscripcionDTO transcribirDesdeAudio(String idVideo) {
        LOG.infof("[Estado] Pipeline Whisper iniciado para: %s", idVideo);
        long inicioTotal = System.currentTimeMillis();

        Path archivoAudio = null;
        try {
            archivoAudio = audioExtraccionServicio.extraerAudio(idVideo);
            LOG.info("[Estado] Iniciando transcripción Whisper");

            List<FragmentoTranscripcionDTO> fragmentos = transcripcionAudioWhisperServicio.transcribir(archivoAudio);

            LOG.infof("[Estado] Transcripción recibida — %d fragmentos en %d ms total (descarga + Whisper)",
                fragmentos.size(), System.currentTimeMillis() - inicioTotal);

            return new RespuestaTranscripcionDTO(idVideo, "Transcripción de audio - " + idVideo, fragmentos, "REAL");
        } finally {
            eliminarArchivoTemporal(archivoAudio);
        }
    }

    private void eliminarArchivoTemporal(Path archivo) {
        if (archivo == null) return;
        try {
            Files.deleteIfExists(archivo);
        } catch (IOException e) {
            // Limpieza fallida: el archivo permanecerá en /tmp/comprendia/ hasta la próxima ejecución
        }
    }
}
