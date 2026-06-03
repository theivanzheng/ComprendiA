package es.comprendia.servicio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.comprendia.dto.CapituloVideoDTO;
import es.comprendia.dto.ConceptoClaveVideoDTO;
import es.comprendia.entidad.FragmentoTranscripcion;
import es.comprendia.repositorio.CapituloVideoRepositorio;
import es.comprendia.repositorio.ConceptoClaveVideoRepositorio;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class AnalisisClaseServicio {

    private static final Logger LOG = Logger.getLogger(AnalisisClaseServicio.class);
    private static final int MAX_CARACTERES_TRANSCRIPCION = 14_000;

    @Inject
    ChatGptServicio chatGptServicio;

    @Inject
    CapituloVideoRepositorio capituloRepositorio;

    @Inject
    ConceptoClaveVideoRepositorio conceptoRepositorio;

    @ConfigProperty(name = "comprendia.analisis.gpt.habilitado", defaultValue = "true")
    boolean analisisGptHabilitado;

    private final ObjectMapper mapeadorJson = new ObjectMapper();

    public void generarYGuardar(Long idVideo, List<FragmentoTranscripcion> fragmentos) {
        if (idVideo == null || fragmentos == null || fragmentos.isEmpty()) {
            LOG.warn("[Analisis] Sin fragmentos: no se generan capitulos ni conceptos");
            return;
        }

        ResultadoAnalisis resultado;
        try {
            resultado = analisisGptHabilitado
                ? generarConGpt(fragmentos)
                : generarFallback(fragmentos);
        } catch (Exception e) {
            LOG.warnf("[Analisis] Fallback local por error generando capitulos con GPT: %s", e.getMessage());
            resultado = generarFallback(fragmentos);
        }

        capituloRepositorio.reemplazar(idVideo, resultado.capitulos());
        conceptoRepositorio.reemplazar(idVideo, resultado.conceptos());
        LOG.infof("[Analisis] Guardados %d capitulos y %d conceptos para video id=%s",
            resultado.capitulos().size(), resultado.conceptos().size(), idVideo);
    }

    private ResultadoAnalisis generarConGpt(List<FragmentoTranscripcion> fragmentos) {
        String sistema = """
            Eres un analista educativo. Tu tarea es convertir una transcripcion con timestamps en capitulos utiles para navegar una clase y conceptos clave para estudiar.
            Devuelve solo JSON valido. No uses markdown.
            Los capitulos deben agrupar ideas completas, no copiar fragmentos sueltos.
            Los conceptos deben ser concretos, tener definicion corta y apuntar al timestamp donde mejor se explican.
            """;

        String usuario = """
            Genera entre 4 y 8 capitulos y entre 6 y 10 conceptos clave.
            Responde con esta forma exacta:
            {
              "capitulos": [
                {"titulo": "...", "descripcion": "...", "tiempoInicio": 0, "tiempoFin": 120}
              ],
              "conceptos": [
                {"nombre": "...", "definicion": "...", "tiempoInicio": 15}
              ]
            }

            Transcripcion:
            """ + construirTranscripcionCompacta(fragmentos);

        String json = chatGptServicio.completarEstructurado(sistema, usuario, 1800);
        return parsearAnalisis(json, fragmentos);
    }

    private ResultadoAnalisis parsearAnalisis(String json, List<FragmentoTranscripcion> fragmentos) {
        try {
            JsonNode raiz = mapeadorJson.readTree(json);
            List<CapituloVideoDTO> capitulos = new ArrayList<>();
            List<ConceptoClaveVideoDTO> conceptos = new ArrayList<>();

            int orden = 0;
            for (JsonNode nodo : raiz.path("capitulos")) {
                String titulo = limpiar(nodo.path("titulo").asText("Capitulo " + (orden + 1)), 90);
                String descripcion = limpiar(nodo.path("descripcion").asText(""), 220);
                double inicio = normalizarTiempo(nodo.path("tiempoInicio").asDouble(0), fragmentos);
                double fin = normalizarTiempo(nodo.path("tiempoFin").asDouble(inicio), fragmentos);
                capitulos.add(new CapituloVideoDTO(titulo, descripcion, inicio, Math.max(fin, inicio), orden++, "IA"));
            }

            orden = 0;
            for (JsonNode nodo : raiz.path("conceptos")) {
                String nombre = limpiar(nodo.path("nombre").asText("Concepto " + (orden + 1)), 80);
                String definicion = limpiar(nodo.path("definicion").asText(""), 220);
                double inicio = normalizarTiempo(nodo.path("tiempoInicio").asDouble(0), fragmentos);
                conceptos.add(new ConceptoClaveVideoDTO(nombre, definicion, inicio, orden++));
            }

            if (capitulos.size() < 2 || conceptos.size() < 3) {
                throw new IllegalStateException("GPT devolvio un analisis demasiado pobre");
            }

            return new ResultadoAnalisis(capitulos, conceptos);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo parsear el JSON de analisis: " + e.getMessage(), e);
        }
    }

    private ResultadoAnalisis generarFallback(List<FragmentoTranscripcion> fragmentos) {
        List<FragmentoTranscripcion> ordenados = ordenar(fragmentos);
        List<CapituloVideoDTO> capitulos = new ArrayList<>();
        List<ConceptoClaveVideoDTO> conceptos = new ArrayList<>();

        int numeroCapitulos = Math.min(6, Math.max(3, ordenados.size() / 4));
        int tamanyoBloque = Math.max(1, (int) Math.ceil(ordenados.size() / (double) numeroCapitulos));

        for (int i = 0; i < ordenados.size(); i += tamanyoBloque) {
            List<FragmentoTranscripcion> bloque = ordenados.subList(i, Math.min(i + tamanyoBloque, ordenados.size()));
            FragmentoTranscripcion primero = bloque.get(0);
            FragmentoTranscripcion ultimo = bloque.get(bloque.size() - 1);
            int orden = capitulos.size();
            String titulo = tituloDesdeTexto(primero.texto, "Bloque " + (orden + 1));
            String descripcion = limpiar(unirTextos(bloque), 190);
            capitulos.add(new CapituloVideoDTO(
                titulo,
                descripcion,
                valorTiempo(primero.tiempoInicio),
                valorTiempo(ultimo.tiempoFin),
                orden,
                "AUTO"
            ));
        }

        for (int i = 0; i < Math.min(8, ordenados.size()); i++) {
            FragmentoTranscripcion fragmento = ordenados.get(i);
            conceptos.add(new ConceptoClaveVideoDTO(
                tituloDesdeTexto(fragmento.texto, "Concepto " + (i + 1)),
                limpiar(fragmento.texto, 180),
                valorTiempo(fragmento.tiempoInicio),
                i
            ));
        }

        return new ResultadoAnalisis(capitulos, conceptos);
    }

    private String construirTranscripcionCompacta(List<FragmentoTranscripcion> fragmentos) {
        StringBuilder constructor = new StringBuilder();
        for (FragmentoTranscripcion fragmento : ordenar(fragmentos)) {
            if (constructor.length() >= MAX_CARACTERES_TRANSCRIPCION) break;
            constructor
                .append("[")
                .append(formatearTiempo(valorTiempo(fragmento.tiempoInicio)))
                .append(" - ")
                .append(formatearTiempo(valorTiempo(fragmento.tiempoFin)))
                .append("] ")
                .append(fragmento.texto)
                .append("\n");
        }
        return constructor.toString();
    }

    private List<FragmentoTranscripcion> ordenar(List<FragmentoTranscripcion> fragmentos) {
        return fragmentos.stream()
            .sorted(Comparator.comparing(f -> f.ordenFragmento == null ? 0 : f.ordenFragmento))
            .toList();
    }

    private double normalizarTiempo(double segundos, List<FragmentoTranscripcion> fragmentos) {
        double maximo = fragmentos.stream()
            .map(f -> valorTiempo(f.tiempoFin))
            .max(Double::compareTo)
            .orElse(0.0);
        if (segundos < 0) return 0;
        if (maximo <= 0) return segundos;
        return Math.min(segundos, maximo);
    }

    private double valorTiempo(Double valor) {
        return valor == null ? 0.0 : valor;
    }

    private String limpiar(String texto, int maximo) {
        if (texto == null) return "";
        String limpio = texto.replaceAll("\\s+", " ").trim();
        if (limpio.length() <= maximo) return limpio;
        return limpio.substring(0, maximo).trim();
    }

    private String tituloDesdeTexto(String texto, String fallback) {
        String limpio = limpiar(texto, 90);
        if (limpio.isBlank()) return fallback;
        String[] palabras = limpio.split("\\s+");
        int limite = Math.min(7, palabras.length);
        return String.join(" ", java.util.Arrays.copyOfRange(palabras, 0, limite));
    }

    private String unirTextos(List<FragmentoTranscripcion> fragmentos) {
        return fragmentos.stream()
            .map(f -> f.texto == null ? "" : f.texto)
            .reduce("", (a, b) -> (a + " " + b).trim());
    }

    private String formatearTiempo(double segundos) {
        int total = (int) Math.floor(segundos);
        int minutos = total / 60;
        int resto = total % 60;
        return String.format("%d:%02d", minutos, resto);
    }

    private record ResultadoAnalisis(
        List<CapituloVideoDTO> capitulos,
        List<ConceptoClaveVideoDTO> conceptos
    ) {}
}
