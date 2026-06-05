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

import java.text.Normalizer;
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

        List<FragmentoTranscripcion> ordenados = ordenar(fragmentos);
        double duracion = maxTiempoFin(ordenados);
        double primerInicio = valorTiempo(ordenados.get(0).tiempoInicio);
        // [Diagnóstico] Estado de los fragmentos que entran al análisis
        LOG.infof("[Analisis] Entrada: %d fragmentos, primer inicio=%.0fs, ultimo fin=%.0fs (duracion=%.0fs)",
            ordenados.size(), primerInicio, duracion, duracion);

        ResultadoAnalisis resultado;
        boolean usandoGpt;
        try {
            if (analisisGptHabilitado) {
                resultado = generarConGpt(ordenados, duracion);
                usandoGpt = true;
                LOG.infof("[Analisis] Capitulos generados por GPT (%d capitulos)", resultado.capitulos().size());
            } else {
                resultado = generarFallback(ordenados);
                usandoGpt = false;
                LOG.info("[Analisis] GPT deshabilitado: usando fallback local por contenido");
            }
        } catch (Exception e) {
            LOG.warnf("[Analisis] Fallback local porque GPT falló: %s", e.getMessage());
            resultado = generarFallback(ordenados);
            usandoGpt = false;
        }

        // Normalización temporal final (orden ASC, dedup, fin = inicio del siguiente, sin
        // solapamientos). Se aplica a AMBOS caminos (GPT y fallback) antes de persistir.
        List<CapituloVideoDTO> capitulosNorm = ajustarMonotonicidadYFines(resultado.capitulos(), duracion);
        resultado = new ResultadoAnalisis(capitulosNorm, resultado.conceptos());
        double maxCapFin = maxTiempoFinCapitulos(resultado.capitulos());

        capituloRepositorio.reemplazar(idVideo, resultado.capitulos());
        conceptoRepositorio.reemplazar(idVideo, resultado.conceptos());
        LOG.infof("[Analisis] Guardados %d capitulos (fuente=%s, cubren hasta %.0fs de %.0fs) y %d conceptos para video id=%s",
            resultado.capitulos().size(), usandoGpt ? "GPT" : "FALLBACK", maxCapFin, duracion,
            resultado.conceptos().size(), idVideo);
    }

    private double maxTiempoFin(List<FragmentoTranscripcion> fragmentos) {
        return fragmentos.stream().map(f -> valorTiempo(f.tiempoFin)).max(Double::compareTo).orElse(0.0);
    }

    private double maxTiempoFinCapitulos(List<CapituloVideoDTO> capitulos) {
        return capitulos.stream()
            .map(c -> c.tiempoFin() == null ? 0.0 : c.tiempoFin())
            .max(Double::compareTo).orElse(0.0);
    }

    private ResultadoAnalisis generarConGpt(List<FragmentoTranscripcion> fragmentos, double duracion) {
        String sistema = """
            Eres un analista educativo. Conviertes la transcripcion de una clase en capitulos
            para navegarla y en conceptos clave para estudiar.
            Devuelve solo JSON valido, sin markdown.
            NO inventes tiempos ni segundos: no incluyas marcas de tiempo en la respuesta.
            Para cada capitulo y concepto incluye "fraseClave": una frase LITERAL (copiada tal cual)
            de la transcripcion en el punto donde EMPIEZA ese tema o donde se explica ese concepto.
            La fraseClave debe poder encontrarse en la transcripcion (5 a 12 palabras).
            """;

        String usuario = """
            Genera entre 5 y 8 capitulos en orden cronologico que cubran toda la clase,
            y entre 6 y 10 conceptos clave. Responde con esta forma exacta:
            {
              "capitulos": [
                {"titulo": "...", "descripcion": "...", "fraseClave": "frase literal de la transcripcion donde empieza este capitulo"}
              ],
              "conceptos": [
                {"nombre": "...", "definicion": "...", "fraseClave": "frase literal de la transcripcion donde se explica el concepto"}
              ]
            }

            Transcripcion (cada linea: [segundo] texto). Usala solo para extraer la fraseClave literal:
            """ + construirTranscripcionCompacta(fragmentos);

        String json = chatGptServicio.completarEstructurado(sistema, usuario, 1800);
        return parsearAnalisis(json, fragmentos, duracion);
    }

    private ResultadoAnalisis parsearAnalisis(String json, List<FragmentoTranscripcion> fragmentos, double duracion) {
        try {
            JsonNode raiz = mapeadorJson.readTree(json);
            List<FragmentoTranscripcion> ordenados = ordenar(fragmentos);

            // ── Capítulos: timestamp = fragmento real más parecido a la fraseClave, monótono ──
            record CapDatos(String titulo, String descripcion, String fraseClave) {}
            List<CapDatos> capDatos = new ArrayList<>();
            int orden = 0;
            for (JsonNode nodo : raiz.path("capitulos")) {
                String titulo = limpiar(nodo.path("titulo").asText("Capitulo " + (orden + 1)), 90);
                String descripcion = limpiar(nodo.path("descripcion").asText(""), 220);
                String frase = limpiar(nodo.path("fraseClave").asText(""), 200);
                capDatos.add(new CapDatos(titulo, descripcion, frase));
                orden++;
            }

            List<CapituloVideoDTO> capitulos = new ArrayList<>();
            int indicePrevio = -1; // garantiza orden temporal estrictamente creciente
            int[] indicesUsados = new int[capDatos.size()];
            for (int i = 0; i < capDatos.size(); i++) {
                CapDatos c = capDatos.get(i);
                int idx = emparejarFragmento(ordenados, c.fraseClave() + " " + c.titulo(), indicePrevio + 1);
                if (idx <= indicePrevio) idx = Math.min(indicePrevio + 1, ordenados.size() - 1);
                indicePrevio = idx;
                indicesUsados[i] = idx;
                FragmentoTranscripcion frag = ordenados.get(idx);
                double inicio = valorTiempo(frag.tiempoInicio);
                // [Diagnóstico] Trazabilidad título → fragmento real → timestamp
                LOG.infof("[Analisis][Cap %d] titulo='%s' | fraseClave='%s' -> fragmento#%d (t=%.0fs) texto='%s'",
                    i + 1, c.titulo(), c.fraseClave(), idx, inicio, recorte(frag.texto, 60));
                capitulos.add(new CapituloVideoDTO(null, c.titulo(), c.descripcion(), inicio, inicio, i, "IA", false, true));
            }
            // La normalización temporal final (orden, fines, sin solapamientos) se hace en
            // generarYGuardar, común a GPT y fallback.

            // ── Conceptos: timestamp = fragmento real más parecido (sin forzar monotonía) ──
            List<ConceptoClaveVideoDTO> conceptos = new ArrayList<>();
            orden = 0;
            for (JsonNode nodo : raiz.path("conceptos")) {
                String nombre = limpiar(nodo.path("nombre").asText("Concepto " + (orden + 1)), 80);
                String definicion = limpiar(nodo.path("definicion").asText(""), 220);
                String frase = limpiar(nodo.path("fraseClave").asText(""), 200);
                int idx = emparejarFragmento(ordenados, frase + " " + nombre, 0);
                double inicio = idx >= 0 ? valorTiempo(ordenados.get(idx).tiempoInicio) : 0.0;
                LOG.infof("[Analisis][Concepto %d] '%s' | fraseClave='%s' -> fragmento#%d (t=%.0fs)",
                    orden + 1, nombre, frase, idx, inicio);
                conceptos.add(new ConceptoClaveVideoDTO(null, nombre, definicion, inicio, null, orden++, false, true));
            }

            if (capitulos.size() < 2 || conceptos.size() < 3) {
                throw new IllegalStateException("GPT devolvio un analisis demasiado pobre");
            }

            return new ResultadoAnalisis(capitulos, conceptos);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo parsear el JSON de analisis: " + e.getMessage(), e);
        }
    }

    // Devuelve el índice del fragmento (a partir de desde) con mayor solape léxico con el texto
    // objetivo. -1 si no hay candidatos.
    private int emparejarFragmento(List<FragmentoTranscripcion> ordenados, String objetivo, int desde) {
        java.util.Set<String> tokensObjetivo = tokensSignificativos(objetivo);
        int mejorIdx = -1;
        int mejorPuntos = -1;
        for (int j = Math.max(0, desde); j < ordenados.size(); j++) {
            int puntos = solape(ordenados.get(j).texto, tokensObjetivo);
            if (puntos > mejorPuntos) {
                mejorPuntos = puntos;
                mejorIdx = j;
            }
        }
        // Si no hubo ningún solape real, no forzamos un match arbitrario aquí
        if (mejorPuntos <= 0 && desde > 0) return -1;
        return mejorIdx;
    }

    private int solape(String textoFragmento, java.util.Set<String> tokensObjetivo) {
        if (tokensObjetivo.isEmpty()) return 0;
        java.util.Set<String> tokensFragmento = tokensSignificativos(textoFragmento);
        int puntos = 0;
        for (String t : tokensObjetivo) {
            if (tokensFragmento.contains(t)) puntos++;
        }
        return puntos;
    }

    private static final java.util.Set<String> VACIAS = java.util.Set.of(
        "que", "con", "una", "por", "para", "los", "las", "del", "como", "más", "mas",
        "the", "and", "for", "that", "this", "with", "you", "your", "are", "was", "but", "have", "has");

    private java.util.Set<String> tokensSignificativos(String texto) {
        if (texto == null) return java.util.Set.of();
        String norm = Normalizer.normalize(texto.toLowerCase(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("[^a-z0-9\\s]", " ");
        java.util.Set<String> tokens = new java.util.HashSet<>();
        for (String t : norm.split("\\s+")) {
            if (t.length() >= 3 && !VACIAS.contains(t)) tokens.add(t);
        }
        return tokens;
    }

    // Normaliza los tiempos de los capítulos: los ORDENA por tiempo_inicio, elimina
    // duplicados/cercanos y fija tiempo_fin = inicio del siguiente (último = duración).
    // Garantiza: orden ASC, sin solapamientos, fin > inicio, orden_capitulo recalculado.
    private static final double DISTANCIA_MINIMA = 2.0; // segundos mínimos entre capítulos

    private List<CapituloVideoDTO> ajustarMonotonicidadYFines(List<CapituloVideoDTO> capitulos, double duracion) {
        if (capitulos.isEmpty()) return capitulos;

        // [Log] Antes de normalizar
        LOG.info("[Analisis] Capitulos ANTES de normalizar:");
        for (CapituloVideoDTO c : capitulos) {
            LOG.infof("  inicio=%.1f fin=%.1f '%s'",
                valorTiempo(c.tiempoInicio()), valorTiempo(c.tiempoFin()), c.titulo());
        }

        // 1) Ordenar por tiempo_inicio ASC
        List<CapituloVideoDTO> ordenados = new ArrayList<>(capitulos);
        ordenados.sort(Comparator.comparingDouble(c -> valorTiempo(c.tiempoInicio())));

        // 2) Eliminar duplicados / capítulos demasiado cercanos en el tiempo
        List<CapituloVideoDTO> filtrados = new ArrayList<>();
        double ultimoInicio = Double.NEGATIVE_INFINITY;
        for (CapituloVideoDTO c : ordenados) {
            double inicio = valorTiempo(c.tiempoInicio());
            if (inicio > ultimoInicio + DISTANCIA_MINIMA) {
                filtrados.add(c);
                ultimoInicio = inicio;
            } else {
                LOG.infof("[Analisis] Descartado por duplicado/cercania (inicio=%.1f): '%s'", inicio, c.titulo());
            }
        }

        // 3) Reasignar orden_capitulo y fijar tiempo_fin = inicio del siguiente (último = duración)
        List<CapituloVideoDTO> resultado = new ArrayList<>();
        for (int i = 0; i < filtrados.size(); i++) {
            CapituloVideoDTO c = filtrados.get(i);
            double inicio = valorTiempo(c.tiempoInicio());
            double fin = (i + 1 < filtrados.size())
                ? valorTiempo(filtrados.get(i + 1).tiempoInicio())
                : Math.max(duracion, inicio + 1);
            if (fin <= inicio) fin = inicio + 1; // nunca fin <= inicio
            resultado.add(new CapituloVideoDTO(
                c.id(), c.titulo(), c.descripcion(), inicio, fin, i, c.origen(), c.creadoManual(), c.generadoPorIa()));
        }

        // [Log] Después de normalizar + detección de solapamientos
        LOG.info("[Analisis] Capitulos DESPUES de normalizar:");
        for (int i = 0; i < resultado.size(); i++) {
            CapituloVideoDTO c = resultado.get(i);
            LOG.infof("  #%d [%.1f -> %.1f] '%s'",
                c.orden(), valorTiempo(c.tiempoInicio()), valorTiempo(c.tiempoFin()), c.titulo());
            if (i + 1 < resultado.size()
                && valorTiempo(c.tiempoFin()) > valorTiempo(resultado.get(i + 1).tiempoInicio()) + 0.01) {
                LOG.warnf("[Analisis] SOLAPAMIENTO: cap#%d fin=%.1f > cap#%d inicio=%.1f",
                    i, valorTiempo(c.tiempoFin()), i + 1, valorTiempo(resultado.get(i + 1).tiempoInicio()));
            }
        }
        return resultado;
    }

    private String recorte(String texto, int max) {
        if (texto == null) return "";
        String t = texto.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    private ResultadoAnalisis generarFallback(List<FragmentoTranscripcion> fragmentos) {
        List<FragmentoTranscripcion> ordenados = ordenar(fragmentos);
        List<CapituloVideoDTO> capitulos = new ArrayList<>();
        List<ConceptoClaveVideoDTO> conceptos = new ArrayList<>();

        // Pocos bloques amplios (3–5) basados en agrupar contenido, no en minutos exactos.
        int numeroCapitulos = Math.min(5, Math.max(3, ordenados.size() / 10));
        int tamanyoBloque = Math.max(1, (int) Math.ceil(ordenados.size() / (double) numeroCapitulos));

        for (int i = 0; i < ordenados.size(); i += tamanyoBloque) {
            List<FragmentoTranscripcion> bloque = ordenados.subList(i, Math.min(i + tamanyoBloque, ordenados.size()));
            FragmentoTranscripcion primero = bloque.get(0);
            FragmentoTranscripcion ultimo = bloque.get(bloque.size() - 1);
            int orden = capitulos.size();
            // Título a partir del texto real del bloque (nunca "Bloque N" / "Capítulo N")
            String titulo = tituloDesdeTexto(primero.texto, tituloDesdeTexto(unirTextos(bloque), "Parte " + (orden + 1)));
            String descripcion = limpiar(unirTextos(bloque), 190);
            capitulos.add(new CapituloVideoDTO(
                null,
                titulo,
                descripcion,
                valorTiempo(primero.tiempoInicio),
                valorTiempo(ultimo.tiempoFin),
                orden,
                "AUTO",
                false,
                true
            ));
        }

        for (int i = 0; i < Math.min(8, ordenados.size()); i++) {
            FragmentoTranscripcion fragmento = ordenados.get(i);
            conceptos.add(new ConceptoClaveVideoDTO(
                null,
                tituloDesdeTexto(fragmento.texto, "Concepto " + (i + 1)),
                limpiar(fragmento.texto, 180),
                valorTiempo(fragmento.tiempoInicio),
                null,
                i,
                false,
                true
            ));
        }

        return new ResultadoAnalisis(capitulos, conceptos);
    }

    // Construye la transcripción para GPT con el segundo de inicio de cada fragmento.
    // Si excede el presupuesto de caracteres, NO corta el principio: muestrea fragmentos
    // de forma uniforme a lo largo de TODO el vídeo (incluido el final), para que los
    // capítulos puedan cubrir la duración completa.
    private String construirTranscripcionCompacta(List<FragmentoTranscripcion> fragmentos) {
        List<FragmentoTranscripcion> ordenados = ordenar(fragmentos);

        int tamanoTotal = ordenados.stream()
            .mapToInt(f -> (f.texto == null ? 0 : f.texto.length()) + 12)
            .sum();

        List<FragmentoTranscripcion> seleccion;
        if (tamanoTotal <= MAX_CARACTERES_TRANSCRIPCION) {
            seleccion = ordenados;
        } else {
            int tamanoMedio = Math.max(1, tamanoTotal / ordenados.size());
            int objetivo = Math.max(8, MAX_CARACTERES_TRANSCRIPCION / tamanoMedio);
            seleccion = muestrearUniforme(ordenados, objetivo);
            LOG.infof("[Analisis] Transcripcion larga (%d chars): muestreados %d de %d fragmentos repartidos por todo el video",
                tamanoTotal, seleccion.size(), ordenados.size());
        }

        StringBuilder constructor = new StringBuilder();
        for (FragmentoTranscripcion fragmento : seleccion) {
            constructor
                .append("[")
                .append((int) valorTiempo(fragmento.tiempoInicio))
                .append("] ")
                .append(fragmento.texto == null ? "" : fragmento.texto.trim())
                .append("\n");
        }
        return constructor.toString();
    }

    // Selecciona 'objetivo' elementos repartidos uniformemente, garantizando el último.
    private List<FragmentoTranscripcion> muestrearUniforme(List<FragmentoTranscripcion> lista, int objetivo) {
        if (lista.size() <= objetivo) return lista;
        java.util.LinkedHashSet<Integer> indices = new java.util.LinkedHashSet<>();
        double paso = (double) (lista.size() - 1) / (objetivo - 1);
        for (int i = 0; i < objetivo; i++) {
            indices.add((int) Math.round(i * paso));
        }
        indices.add(lista.size() - 1); // asegurar que el final del vídeo está presente
        List<FragmentoTranscripcion> resultado = new ArrayList<>();
        for (int indice : indices) {
            resultado.add(lista.get(indice));
        }
        return resultado;
    }

    private List<FragmentoTranscripcion> ordenar(List<FragmentoTranscripcion> fragmentos) {
        return fragmentos.stream()
            .sorted(Comparator.comparing(f -> f.ordenFragmento == null ? 0 : f.ordenFragmento))
            .toList();
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

    private record ResultadoAnalisis(
        List<CapituloVideoDTO> capitulos,
        List<ConceptoClaveVideoDTO> conceptos
    ) {}
}
