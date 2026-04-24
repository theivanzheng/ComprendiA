package es.comprendia.servicio;

import es.comprendia.dto.FragmentoTranscripcionDTO;
import es.comprendia.excepcion.ExcepcionTranscripcionAudio;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@ApplicationScoped
public class TranscripcionAudioSimuladaServicio implements TranscripcionAudioServicio {

    @Override
    public List<FragmentoTranscripcionDTO> transcribir(Path archivoAudio) {
        validarArchivo(archivoAudio);
        return construirFragmentosSimulados();
    }

    private void validarArchivo(Path archivoAudio) {
        if (archivoAudio == null) {
            throw new ExcepcionTranscripcionAudio("La ruta del archivo de audio no puede ser nula");
        }
        if (!Files.exists(archivoAudio)) {
            throw new ExcepcionTranscripcionAudio("El archivo de audio no existe: " + archivoAudio);
        }
    }

    private List<FragmentoTranscripcionDTO> construirFragmentosSimulados() {
        return List.of(
            new FragmentoTranscripcionDTO("Introducción al tema principal de la clase.", 0.0, 15.0),
            new FragmentoTranscripcionDTO("Explicación de los conceptos fundamentales.", 15.0, 40.0),
            new FragmentoTranscripcionDTO("Demostración práctica con ejemplos reales.", 40.0, 75.0),
            new FragmentoTranscripcionDTO("Análisis de casos de uso y aplicaciones.", 75.0, 110.0),
            new FragmentoTranscripcionDTO("Resumen, conclusiones y próximos pasos.", 110.0, 130.0)
        );
    }
}
