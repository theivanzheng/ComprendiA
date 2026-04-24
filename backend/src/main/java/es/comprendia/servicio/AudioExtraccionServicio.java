package es.comprendia.servicio;

import es.comprendia.excepcion.ExcepcionDescargaAudio;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class AudioExtraccionServicio {

    private static final String DIRECTORIO_TEMPORAL = "/tmp/comprendia/";
    private static final int TIMEOUT_SEGUNDOS = 120;

    // Devuelve la ruta al archivo MP3 descargado para el idVideo dado.
    public Path extraerAudio(String idVideo) {
        Path directorio = Path.of(DIRECTORIO_TEMPORAL);
        crearDirectorioSiNoExiste(directorio);

        Path archivoDestino = directorio.resolve(nombreArchivoSeguro(idVideo) + ".mp3");
        String urlVideo = "https://www.youtube.com/watch?v=" + idVideo;

        ejecutarYtDlp(urlVideo, archivoDestino);
        validarArchivoGenerado(archivoDestino);

        return archivoDestino;
    }

    private void crearDirectorioSiNoExiste(Path directorio) {
        try {
            Files.createDirectories(directorio);
        } catch (IOException e) {
            throw new ExcepcionDescargaAudio("No se pudo crear el directorio temporal: " + e.getMessage(), e);
        }
    }

    // Filtra el idVideo para que solo contenga caracteres seguros para nombre de archivo.
    private String nombreArchivoSeguro(String idVideo) {
        return idVideo.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    private void ejecutarYtDlp(String urlVideo, Path archivoDestino) {
        String rutaDestinoParcial = archivoDestino.toString().replace(".mp3", "");
        List<String> comando = List.of(
            "yt-dlp",
            "--extract-audio",
            "--audio-format", "mp3",
            "--audio-quality", "5",
            "--no-playlist",
            "--output", rutaDestinoParcial + ".%(ext)s",
            urlVideo
        );

        try {
            ProcessBuilder constructorProceso = new ProcessBuilder(comando);
            constructorProceso.redirectErrorStream(true);
            Process proceso = constructorProceso.start();

            String salidaProceso = new String(proceso.getInputStream().readAllBytes());
            boolean terminoATiempo = proceso.waitFor(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);

            if (!terminoATiempo) {
                proceso.destroyForcibly();
                throw new ExcepcionDescargaAudio("yt-dlp superó el tiempo límite de " + TIMEOUT_SEGUNDOS + " segundos");
            }

            int codigoSalida = proceso.exitValue();
            if (codigoSalida != 0) {
                throw new ExcepcionDescargaAudio("yt-dlp falló con código " + codigoSalida + ": " + salidaProceso.trim());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExcepcionDescargaAudio("La descarga de audio fue interrumpida", e);
        } catch (IOException e) {
            throw new ExcepcionDescargaAudio("No se pudo ejecutar yt-dlp. ¿Está instalado?", e);
        }
    }

    private void validarArchivoGenerado(Path archivo) {
        if (!Files.exists(archivo)) {
            throw new ExcepcionDescargaAudio("yt-dlp no generó el archivo esperado: " + archivo);
        }
        try {
            if (Files.size(archivo) == 0) {
                throw new ExcepcionDescargaAudio("El archivo de audio generado está vacío: " + archivo);
            }
        } catch (IOException e) {
            throw new ExcepcionDescargaAudio("No se pudo leer el tamaño del archivo: " + e.getMessage(), e);
        }
    }
}
