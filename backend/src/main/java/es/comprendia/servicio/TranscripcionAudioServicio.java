package es.comprendia.servicio;

import es.comprendia.dto.FragmentoTranscripcionDTO;

import java.nio.file.Path;
import java.util.List;

public interface TranscripcionAudioServicio {

    // Dado un archivo de audio local, devuelve los fragmentos de transcripción con tiempos.
    List<FragmentoTranscripcionDTO> transcribir(Path archivoAudio);
}
