package es.comprendia.servicio;

import es.comprendia.dto.FragmentoTranscripcionDTO;
import es.comprendia.excepcion.ExcepcionTranscripcionAudio;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class TranscripcionAudioSimuladaServicioTest {

    @Inject
    TranscripcionAudioSimuladaServicio transcripcionAudioSimuladaServicio;

    @Test
    void transcribir_archivoValido_devuelveFragmentos(@TempDir Path directorioTemporal) throws IOException {
        Path archivoAudio = directorioTemporal.resolve("audio.mp3");
        Files.writeString(archivoAudio, "contenido simulado");

        List<FragmentoTranscripcionDTO> fragmentos = transcripcionAudioSimuladaServicio.transcribir(archivoAudio);

        assertNotNull(fragmentos);
        assertFalse(fragmentos.isEmpty());
    }

    @Test
    void transcribir_archivoValido_fragmentosTienenTextoYTiempos(@TempDir Path directorioTemporal) throws IOException {
        Path archivoAudio = directorioTemporal.resolve("audio.mp3");
        Files.writeString(archivoAudio, "contenido simulado");

        List<FragmentoTranscripcionDTO> fragmentos = transcripcionAudioSimuladaServicio.transcribir(archivoAudio);

        for (FragmentoTranscripcionDTO fragmento : fragmentos) {
            assertNotNull(fragmento.getTexto());
            assertFalse(fragmento.getTexto().isBlank());
            assertTrue(fragmento.getTiempoFin() > fragmento.getTiempoInicio());
        }
    }

    @Test
    void transcribir_fragmentosOrdenadosCronologicamente(@TempDir Path directorioTemporal) throws IOException {
        Path archivoAudio = directorioTemporal.resolve("audio.mp3");
        Files.writeString(archivoAudio, "contenido simulado");

        List<FragmentoTranscripcionDTO> fragmentos = transcripcionAudioSimuladaServicio.transcribir(archivoAudio);

        for (int i = 1; i < fragmentos.size(); i++) {
            assertTrue(fragmentos.get(i).getTiempoInicio() >= fragmentos.get(i - 1).getTiempoInicio());
        }
    }

    @Test
    void transcribir_archivoInexistente_lanzaExcepcion(@TempDir Path directorioTemporal) {
        Path archivoInexistente = directorioTemporal.resolve("no_existe.mp3");

        assertThrows(ExcepcionTranscripcionAudio.class,
            () -> transcripcionAudioSimuladaServicio.transcribir(archivoInexistente));
    }

    @Test
    void transcribir_archivoNulo_lanzaExcepcion() {
        assertThrows(ExcepcionTranscripcionAudio.class,
            () -> transcripcionAudioSimuladaServicio.transcribir(null));
    }
}
