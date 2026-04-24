package es.comprendia.servicio;

import es.comprendia.excepcion.ExcepcionTranscripcionYoutube;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@ApplicationScoped
public class ClienteYoutubeServicio {

    private static final String AGENTE_USUARIO =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final HttpClient clienteHttp = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    public String obtenerPaginaYoutube(String idVideo) {
        try {
            HttpRequest solicitud = HttpRequest.newBuilder()
                .uri(URI.create("https://www.youtube.com/watch?v=" + idVideo))
                .header("User-Agent", AGENTE_USUARIO)
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

            HttpResponse<String> respuesta = clienteHttp.send(solicitud, HttpResponse.BodyHandlers.ofString());

            if (respuesta.statusCode() != 200) {
                throw new ExcepcionTranscripcionYoutube(
                    "YouTube respondió con código " + respuesta.statusCode());
            }
            return respuesta.body();
        } catch (ExcepcionTranscripcionYoutube e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExcepcionTranscripcionYoutube("Petición a YouTube interrumpida");
        } catch (IOException e) {
            throw new ExcepcionTranscripcionYoutube("Error de red al conectar con YouTube: " + e.getMessage());
        }
    }

    public String obtenerSubtitulosXml(String urlSubtitulos) {
        try {
            HttpRequest solicitud = HttpRequest.newBuilder()
                .uri(URI.create(urlSubtitulos))
                .header("User-Agent", AGENTE_USUARIO)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

            HttpResponse<String> respuesta = clienteHttp.send(solicitud, HttpResponse.BodyHandlers.ofString());
            return respuesta.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExcepcionTranscripcionYoutube("Petición de subtítulos interrumpida");
        } catch (IOException e) {
            throw new ExcepcionTranscripcionYoutube("Error al obtener subtítulos: " + e.getMessage());
        }
    }
}
