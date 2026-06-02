package es.comprendia.servicio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.comprendia.dto.FragmentoTranscripcionDTO;
import es.comprendia.excepcion.ExcepcionTranscripcionAudio;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TranscripcionAudioWhisperServicio implements TranscripcionAudioServicio {

    private static final Logger LOG = Logger.getLogger(TranscripcionAudioWhisperServicio.class);
    private static final String URL_WHISPER = "https://api.openai.com/v1/audio/transcriptions";
    private static final Duration TIMEOUT_SOLICITUD = Duration.ofMinutes(5);

    @ConfigProperty(name = "comprendia.openai.api.clave", defaultValue = "")
    String claveApi;

    private final HttpClient clienteHttp = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();

    private final ObjectMapper mapeadorJson = new ObjectMapper();

    @Override
    public List<FragmentoTranscripcionDTO> transcribir(Path archivoAudio) {
        validarArchivo(archivoAudio);
        validarClaveApi();

        String separador = UUID.randomUUID().toString().replace("-", "");
        byte[] cuerpoMultipart = construirCuerpoMultipart(archivoAudio, separador);

        HttpRequest solicitud = HttpRequest.newBuilder()
            .uri(URI.create(URL_WHISPER))
            .header("Authorization", "Bearer " + claveApi)
            .header("Content-Type", "multipart/form-data; boundary=" + separador)
            .timeout(TIMEOUT_SOLICITUD)
            .POST(HttpRequest.BodyPublishers.ofByteArray(cuerpoMultipart))
            .build();

        try {
            LOG.infof("[Estado] Enviando audio a Whisper — archivo: %s", archivoAudio.getFileName());
            long inicioWhisper = System.currentTimeMillis();

            HttpResponse<String> respuesta = clienteHttp.send(solicitud, HttpResponse.BodyHandlers.ofString());
            LOG.infof("[Tiempo] Whisper completado en %d ms (HTTP %d)",
                System.currentTimeMillis() - inicioWhisper, respuesta.statusCode());

            if (respuesta.statusCode() != 200) {
                throw new ExcepcionTranscripcionAudio(
                    "Error de la API de Whisper (HTTP " + respuesta.statusCode() + "): " + respuesta.body());
            }

            return parsearRespuestaWhisper(respuesta.body());

        } catch (ExcepcionTranscripcionAudio e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExcepcionTranscripcionAudio("La transcripción fue interrumpida", e);
        } catch (IOException e) {
            throw new ExcepcionTranscripcionAudio("Error al comunicarse con la API de Whisper: " + e.getMessage(), e);
        }
    }

    private void validarArchivo(Path archivoAudio) {
        if (archivoAudio == null) {
            throw new ExcepcionTranscripcionAudio("La ruta del archivo de audio no puede ser nula");
        }
        if (!Files.exists(archivoAudio)) {
            throw new ExcepcionTranscripcionAudio("El archivo de audio no existe: " + archivoAudio);
        }
        try {
            if (Files.size(archivoAudio) == 0) {
                throw new ExcepcionTranscripcionAudio("El archivo de audio está vacío: " + archivoAudio);
            }
        } catch (IOException e) {
            throw new ExcepcionTranscripcionAudio("No se pudo leer el tamaño del archivo: " + e.getMessage(), e);
        }
    }

    private void validarClaveApi() {
        if (claveApi == null || claveApi.isBlank()) {
            throw new ExcepcionTranscripcionAudio(
                "La clave de la API de OpenAI no está configurada. " +
                "Establece la variable de entorno OPENAI_API_KEY antes de arrancar.");
        }
    }

    private byte[] construirCuerpoMultipart(Path archivoAudio, String separador) {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            byte[] contenidoAudio = Files.readAllBytes(archivoAudio);
            String nombreArchivo = archivoAudio.getFileName().toString();

            agregarCampoArchivo(salida, separador, nombreArchivo, contenidoAudio);
            agregarCampoTexto(salida, separador, "model", "whisper-1");
            agregarCampoTexto(salida, separador, "response_format", "verbose_json");
            agregarCampoTexto(salida, separador, "timestamp_granularities[]", "segment");

            salida.write(("--" + separador + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return salida.toByteArray();

        } catch (IOException e) {
            throw new ExcepcionTranscripcionAudio(
                "Error al construir la solicitud multipart: " + e.getMessage(), e);
        }
    }

    private void agregarCampoArchivo(ByteArrayOutputStream salida, String separador,
                                     String nombreArchivo, byte[] contenido) throws IOException {
        salida.write(("--" + separador + "\r\n").getBytes(StandardCharsets.UTF_8));
        salida.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + nombreArchivo + "\"\r\n")
            .getBytes(StandardCharsets.UTF_8));
        salida.write("Content-Type: audio/mpeg\r\n".getBytes(StandardCharsets.UTF_8));
        salida.write("\r\n".getBytes(StandardCharsets.UTF_8));
        salida.write(contenido);
        salida.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private void agregarCampoTexto(ByteArrayOutputStream salida, String separador,
                                   String nombre, String valor) throws IOException {
        salida.write(("--" + separador + "\r\n").getBytes(StandardCharsets.UTF_8));
        salida.write(("Content-Disposition: form-data; name=\"" + nombre + "\"\r\n")
            .getBytes(StandardCharsets.UTF_8));
        salida.write("\r\n".getBytes(StandardCharsets.UTF_8));
        salida.write(valor.getBytes(StandardCharsets.UTF_8));
        salida.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private List<FragmentoTranscripcionDTO> parsearRespuestaWhisper(String jsonRespuesta) {
        try {
            JsonNode nodoRaiz = mapeadorJson.readTree(jsonRespuesta);
            JsonNode segmentos = nodoRaiz.get("segments");

            if (segmentos == null || !segmentos.isArray()) {
                throw new ExcepcionTranscripcionAudio(
                    "La respuesta de Whisper no contiene segmentos válidos");
            }

            List<FragmentoTranscripcionDTO> fragmentos = new ArrayList<>();
            for (JsonNode segmento : segmentos) {
                String texto = segmento.path("text").asText("").trim();
                double inicio = segmento.path("start").asDouble();
                double fin = segmento.path("end").asDouble();

                if (!texto.isEmpty()) {
                    fragmentos.add(new FragmentoTranscripcionDTO(texto, inicio, fin));
                }
            }

            if (fragmentos.isEmpty()) {
                throw new ExcepcionTranscripcionAudio("Whisper no devolvió fragmentos de transcripción");
            }

            return fragmentos;

        } catch (ExcepcionTranscripcionAudio e) {
            throw e;
        } catch (Exception e) {
            throw new ExcepcionTranscripcionAudio(
                "Error al parsear la respuesta de Whisper: " + e.getMessage(), e);
        }
    }
}
