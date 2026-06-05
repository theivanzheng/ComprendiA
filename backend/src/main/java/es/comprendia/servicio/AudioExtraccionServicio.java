package es.comprendia.servicio;

import es.comprendia.excepcion.ExcepcionDescargaAudio;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class AudioExtraccionServicio {

    private static final Logger LOG = Logger.getLogger(AudioExtraccionServicio.class);
    private static final String DIRECTORIO_TEMPORAL = "/tmp/comprendia/";
    private static final int TIMEOUT_SEGUNDOS = 120;

    public String obtenerTitulo(String idVideo) {
        String urlVideo = "https://www.youtube.com/watch?v=" + idVideo;
        List<String> comando = List.of(
            "yt-dlp", "--print", "%(title)s", "--no-download", "--no-playlist", urlVideo
        );
        try {
            ProcessBuilder builder = new ProcessBuilder(comando);
            builder.redirectErrorStream(true);
            Process proceso = builder.start();

            StringBuilder salida = new StringBuilder();
            Thread lector = new Thread(() -> {
                try (var s = proceso.getInputStream()) {
                    salida.append(new String(s.readAllBytes()).strip());
                } catch (IOException ignorado) {}
            });
            lector.setDaemon(true);
            lector.start();

            boolean termino = proceso.waitFor(30, TimeUnit.SECONDS);
            lector.join(2000);

            if (!termino || proceso.exitValue() != 0 || salida.toString().isBlank()) {
                return "Vídeo " + idVideo;
            }
            // Solo la primera línea, por si yt-dlp añade avisos
            return salida.toString().lines().findFirst().orElse("Vídeo " + idVideo);
        } catch (Exception e) {
            LOG.warnf("No se pudo obtener el título del vídeo %s: %s", idVideo, e.getMessage());
            return "Vídeo " + idVideo;
        }
    }

    /** Metadatos del canal de YouTube (pueden ser null si yt-dlp no los devuelve). */
    public record MetadatosCanal(String canalId, String canalNombre) {}

    /**
     * Obtiene el id y el nombre del canal de YouTube con yt-dlp (sin descargar nada).
     * Si falla o no hay datos, devuelve un MetadatosCanal con campos null.
     */
    public MetadatosCanal obtenerMetadatosCanal(String idVideo) {
        String urlVideo = "https://www.youtube.com/watch?v=" + idVideo;
        // Separador improbable en nombres de canal para partir la salida con seguridad.
        List<String> comando = List.of(
            "yt-dlp", "--print", "%(channel_id)s|||%(channel)s",
            "--no-download", "--no-playlist", urlVideo
        );
        try {
            ProcessBuilder builder = new ProcessBuilder(comando);
            builder.redirectErrorStream(true);
            Process proceso = builder.start();

            StringBuilder salida = new StringBuilder();
            Thread lector = new Thread(() -> {
                try (var s = proceso.getInputStream()) {
                    salida.append(new String(s.readAllBytes()).strip());
                } catch (IOException ignorado) {}
            });
            lector.setDaemon(true);
            lector.start();

            boolean termino = proceso.waitFor(30, TimeUnit.SECONDS);
            lector.join(2000);

            if (!termino || proceso.exitValue() != 0 || salida.toString().isBlank()) {
                LOG.warnf("[Canal] yt-dlp no devolvió metadatos de canal para %s", idVideo);
                return new MetadatosCanal(null, null);
            }
            String linea = salida.toString().lines().findFirst().orElse("");
            String[] partes = linea.split("\\|\\|\\|", 2);
            String canalId = limpiarMetadato(partes.length > 0 ? partes[0] : null);
            String canalNombre = limpiarMetadato(partes.length > 1 ? partes[1] : null);
            LOG.infof("[Canal] Canal detectado para %s: id=%s, nombre=%s", idVideo, canalId, canalNombre);
            return new MetadatosCanal(canalId, canalNombre);
        } catch (Exception e) {
            LOG.warnf("[Canal] No se pudo obtener el canal del vídeo %s: %s", idVideo, e.getMessage());
            return new MetadatosCanal(null, null);
        }
    }

    // yt-dlp imprime "NA" cuando un campo no existe; se trata como ausente.
    private String limpiarMetadato(String valor) {
        if (valor == null) return null;
        String v = valor.strip();
        return (v.isBlank() || v.equals("NA")) ? null : v;
    }

    public Path extraerAudio(String idVideo) {
        LOG.infof("[Estado] Iniciando descarga de audio para: %s", idVideo);
        long inicio = System.currentTimeMillis();

        Path directorio = Path.of(DIRECTORIO_TEMPORAL);
        crearDirectorioSiNoExiste(directorio);

        Path archivoDestino = directorio.resolve(nombreArchivoSeguro(idVideo) + ".mp3");
        String urlVideo = "https://www.youtube.com/watch?v=" + idVideo;

        ejecutarYtDlp(urlVideo, archivoDestino);
        validarArchivoGenerado(archivoDestino);

        LOG.infof("[Tiempo] Descarga audio completada en %d ms", System.currentTimeMillis() - inicio);
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

        LOG.infof("[Tiempo] Ejecutando yt-dlp (timeout=%ds)", TIMEOUT_SEGUNDOS);
        long inicio = System.currentTimeMillis();

        try {
            ProcessBuilder constructorProceso = new ProcessBuilder(comando);
            constructorProceso.redirectErrorStream(true);
            Process proceso = constructorProceso.start();

            // Leer la salida en un hilo daemon para no bloquear waitFor()
            StringBuilder salidaBuilder = new StringBuilder();
            Thread lectorSalida = new Thread(() -> {
                try (var stream = proceso.getInputStream()) {
                    salidaBuilder.append(new String(stream.readAllBytes()));
                } catch (IOException ignorado) {}
            });
            lectorSalida.setDaemon(true);
            lectorSalida.start();

            boolean terminoATiempo = proceso.waitFor(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);

            if (!terminoATiempo) {
                proceso.destroyForcibly();
                throw new ExcepcionDescargaAudio("yt-dlp superó el tiempo límite de " + TIMEOUT_SEGUNDOS + " segundos");
            }

            lectorSalida.join(3000);
            String salidaProceso = salidaBuilder.toString();
            int codigoSalida = proceso.exitValue();
            long duracion = System.currentTimeMillis() - inicio;

            if (codigoSalida != 0) {
                throw new ExcepcionDescargaAudio(
                    "yt-dlp falló con código " + codigoSalida + " en " + duracion + "ms: " + salidaProceso.trim());
            }

            LOG.infof("[Tiempo] yt-dlp completado en %d ms (código=%d)", duracion, codigoSalida);

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
            long tamanyoBytes = Files.size(archivo);
            if (tamanyoBytes == 0) {
                throw new ExcepcionDescargaAudio("El archivo de audio generado está vacío: " + archivo);
            }
            LOG.infof("[Estado] Audio descargado — archivo: %s (%d bytes)", archivo.getFileName(), tamanyoBytes);
        } catch (IOException e) {
            throw new ExcepcionDescargaAudio("No se pudo leer el tamaño del archivo: " + e.getMessage(), e);
        }
    }
}
