package es.comprendia.servicio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.comprendia.dto.FragmentoTranscripcionDTO;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Intenta obtener la transcripción directamente de los subtítulos de YouTube usando yt-dlp,
 * evitando descargar audio y llamar a Whisper. Prefiere español, luego inglés; acepta
 * subtítulos automáticos si no hay manuales. Si no hay subtítulos válidos, devuelve vacío
 * y el pipeline cae a Whisper.
 */
@ApplicationScoped
public class SubtitulosYoutubeServicio {

    private static final Logger LOG = Logger.getLogger(SubtitulosYoutubeServicio.class);
    private static final String DIRECTORIO_TEMPORAL = "/tmp/comprendia/";
    private static final int TIMEOUT_SEGUNDOS = 40;
    // Orden de preferencia de idioma (prefijos)
    private static final List<String> IDIOMAS_PREFERIDOS = List.of("es", "en");

    private final ObjectMapper mapeadorJson = new ObjectMapper();

    public record SubtitulosResultado(String idioma, List<FragmentoTranscripcionDTO> fragmentos) {}

    public Optional<SubtitulosResultado> obtenerSubtitulos(String idVideo) {
        LOG.info("[Subtitulos] Buscando subtítulos de YouTube…");
        Path directorio = Path.of(DIRECTORIO_TEMPORAL);
        try {
            Files.createDirectories(directorio);
        } catch (IOException e) {
            LOG.warnf("[Subtitulos] No se pudo crear el directorio temporal: %s", e.getMessage());
            return Optional.empty();
        }

        String base = directorio.resolve(nombreSeguro(idVideo) + "_sub").toString();
        List<Path> generados = new ArrayList<>();
        try {
            ejecutarYtDlpSubtitulos(idVideo, base);

            // Buscar el mejor archivo .lang.json3 según preferencia de idioma
            Path elegido = elegirArchivo(directorio, nombreSeguro(idVideo) + "_sub");
            if (elegido == null) {
                LOG.info("[Subtitulos] No hay subtítulos válidos, usando Whisper");
                return Optional.empty();
            }
            generados.add(elegido);

            String idioma = extraerIdioma(elegido);
            List<FragmentoTranscripcionDTO> fragmentos = parsearJson3(elegido);
            if (fragmentos.size() < 3) {
                LOG.infof("[Subtitulos] Subtítulos demasiado pobres (%d fragmentos), usando Whisper", fragmentos.size());
                return Optional.empty();
            }

            LOG.infof("[Subtitulos] Subtítulos encontrados: idioma=%s, %d fragmentos", idioma, fragmentos.size());
            return Optional.of(new SubtitulosResultado(idioma, fragmentos));

        } catch (Exception e) {
            LOG.infof("[Subtitulos] No se pudieron usar subtítulos (%s), usando Whisper", e.getMessage());
            return Optional.empty();
        } finally {
            limpiarArchivos(directorio, nombreSeguro(idVideo) + "_sub");
        }
    }

    private void ejecutarYtDlpSubtitulos(String idVideo, String baseSalida) throws Exception {
        String url = "https://www.youtube.com/watch?v=" + idVideo;
        List<String> comando = List.of(
            "yt-dlp",
            "--write-subs",
            "--write-auto-subs",
            "--sub-langs", "es.*,en.*",
            "--sub-format", "json3",
            "--skip-download",
            "--no-playlist",
            "--output", baseSalida + ".%(ext)s",
            url
        );

        ProcessBuilder builder = new ProcessBuilder(comando);
        builder.redirectErrorStream(true);
        Process proceso = builder.start();

        StringBuilder salida = new StringBuilder();
        Thread lector = new Thread(() -> {
            try (var s = proceso.getInputStream()) {
                salida.append(new String(s.readAllBytes()));
            } catch (IOException ignorado) {}
        });
        lector.setDaemon(true);
        lector.start();

        boolean termino = proceso.waitFor(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
        if (!termino) {
            proceso.destroyForcibly();
            throw new IllegalStateException("yt-dlp (subtítulos) superó el tiempo límite");
        }
        lector.join(2000);
        // No lanzamos si exitValue != 0: puede salir != 0 simplemente porque no hay subtítulos;
        // la presencia/ausencia del archivo json3 decide.
    }

    // Elige el archivo de subtítulos según preferencia de idioma (es antes que en).
    private Path elegirArchivo(Path directorio, String prefijoBase) throws IOException {
        try (Stream<Path> ficheros = Files.list(directorio)) {
            List<Path> candidatos = ficheros
                .filter(p -> {
                    String n = p.getFileName().toString();
                    return n.startsWith(prefijoBase + ".") && n.endsWith(".json3");
                })
                .toList();

            for (String idioma : IDIOMAS_PREFERIDOS) {
                Optional<Path> encontrado = candidatos.stream()
                    .filter(p -> extraerIdioma(p).toLowerCase().startsWith(idioma))
                    .min(Comparator.comparing(p -> p.getFileName().toString()));
                if (encontrado.isPresent()) return encontrado.get();
            }
            // Si no coincide la preferencia pero hay alguno, usar el primero disponible
            return candidatos.stream().findFirst().orElse(null);
        }
    }

    // De "abc_sub.es.json3" extrae "es"
    private String extraerIdioma(Path archivo) {
        String n = archivo.getFileName().toString();
        n = n.substring(0, n.length() - ".json3".length()); // quitar extensión
        int punto = n.lastIndexOf('.');
        return punto >= 0 ? n.substring(punto + 1) : "desconocido";
    }

    private List<FragmentoTranscripcionDTO> parsearJson3(Path archivo) throws IOException {
        JsonNode raiz = mapeadorJson.readTree(Files.readString(archivo));
        List<FragmentoTranscripcionDTO> fragmentos = new ArrayList<>();

        for (JsonNode evento : raiz.path("events")) {
            JsonNode segs = evento.path("segs");
            if (!segs.isArray() || segs.isEmpty()) continue;

            StringBuilder texto = new StringBuilder();
            for (JsonNode seg : segs) {
                texto.append(seg.path("utf8").asText(""));
            }
            String limpio = texto.toString().replace("\n", " ").replaceAll("\\s+", " ").trim();
            if (limpio.isEmpty()) continue;

            double inicio = evento.path("tStartMs").asLong(0) / 1000.0;
            double dur = evento.path("dDurationMs").asLong(0) / 1000.0;
            double fin = inicio + Math.max(dur, 0);

            // Evitar duplicar el texto consecutivo (típico en subtítulos automáticos)
            if (!fragmentos.isEmpty()) {
                FragmentoTranscripcionDTO ultimo = fragmentos.get(fragmentos.size() - 1);
                if (ultimo.getTexto().equalsIgnoreCase(limpio)) {
                    continue;
                }
            }
            fragmentos.add(new FragmentoTranscripcionDTO(limpio, inicio, fin));
        }
        return fragmentos;
    }

    private void limpiarArchivos(Path directorio, String prefijoBase) {
        try (Stream<Path> ficheros = Files.list(directorio)) {
            ficheros
                .filter(p -> p.getFileName().toString().startsWith(prefijoBase + "."))
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignorado) {}
                });
        } catch (IOException ignorado) {}
    }

    private String nombreSeguro(String idVideo) {
        return idVideo.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
