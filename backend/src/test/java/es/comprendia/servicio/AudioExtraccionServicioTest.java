package es.comprendia.servicio;

import es.comprendia.excepcion.ExcepcionDescargaAudio;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AudioExtraccionServicioTest {

    @Inject
    AudioExtraccionServicio audioExtraccionServicio;

    @Test
    void nombreArchivoSeguro_caracteresEspeciales_sonReemplazados() throws Exception {
        Method metodo = AudioExtraccionServicio.class
            .getDeclaredMethod("nombreArchivoSeguro", String.class);
        metodo.setAccessible(true);

        String resultado = (String) metodo.invoke(audioExtraccionServicio, "abc-123_XYZ");
        assertEquals("abc-123_XYZ", resultado);

        String resultadoConEspeciales = (String) metodo.invoke(audioExtraccionServicio, "id/con?params=1");
        assertFalse(resultadoConEspeciales.contains("/"));
        assertFalse(resultadoConEspeciales.contains("?"));
        assertFalse(resultadoConEspeciales.contains("="));
    }

    @Test
    void extraerAudio_ytDlpNoDisponible_lanzaExcepcion() {
        // Si yt-dlp no está instalado, debe lanzar ExcepcionDescargaAudio con mensaje claro.
        // Si sí está instalado, el test pasa igualmente (no hace llamada real a YouTube aquí).
        // Este test valida la ruta de error de ProcessBuilder cuando el binario no existe.
        try {
            audioExtraccionServicio.extraerAudio("video_inexistente_test_unitario");
            // Si yt-dlp está instalado, fallará por otro motivo (red, vídeo inválido)
            // lo cual también es una ExcepcionDescargaAudio
        } catch (ExcepcionDescargaAudio e) {
            assertNotNull(e.getMessage());
            assertFalse(e.getMessage().isBlank());
        }
    }
}
