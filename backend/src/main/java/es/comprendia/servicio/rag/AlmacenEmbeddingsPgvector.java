package es.comprendia.servicio.rag;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import es.comprendia.dto.ResultadoBusquedaDTO;
import es.comprendia.repositorio.FragmentoTranscripcionRepositorio;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/**
 * Adaptador que implementa el {@link EmbeddingStore} de LangChain4j sobre nuestra tabla pgvector
 * ({@code fragmentos_transcripcion}). Permite que el RAG use las abstracciones estándar de
 * LangChain4j (EmbeddingSearchRequest / EmbeddingSearchResult / TextSegment / Filter) en lugar de
 * SQL artesanal, sin cambiar el esquema ni reprocesar los embeddings ya guardados.
 *
 * <p>Solo soporta la <b>búsqueda</b> ({@link #search}). La ingesta de embeddings sigue en
 * {@code EmbeddingFragmentoServicio}, así que los métodos {@code add*} no se usan.</p>
 *
 * <p>El vídeo sobre el que se busca se pasa como filtro de metadatos:
 * {@code metadataKey("videoId").isEqualTo(id)}.</p>
 */
@ApplicationScoped
public class AlmacenEmbeddingsPgvector implements EmbeddingStore<TextSegment> {

    public static final String CLAVE_VIDEO = "videoId";
    public static final String CLAVE_TIEMPO_INICIO = "tiempoInicio";
    public static final String CLAVE_TIEMPO_FIN = "tiempoFin";
    public static final String CLAVE_ORDEN = "orden";

    @Inject
    FragmentoTranscripcionRepositorio fragmentoRepositorio;

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest peticion) {
        Long videoId = extraerVideoId(peticion.filter());
        if (videoId == null) {
            throw new IllegalArgumentException(
                "La búsqueda requiere un filtro metadataKey(\"" + CLAVE_VIDEO + "\").isEqualTo(idVideo)");
        }

        String vectorPgvector = aCadenaPgvector(peticion.queryEmbedding().vector());
        List<ResultadoBusquedaDTO> filas =
            fragmentoRepositorio.buscarPorSimilitud(videoId, vectorPgvector, peticion.maxResults());

        List<EmbeddingMatch<TextSegment>> coincidencias = new ArrayList<>(filas.size());
        for (ResultadoBusquedaDTO fila : filas) {
            Metadata metadatos = new Metadata();
            metadatos.put(CLAVE_VIDEO, videoId);
            metadatos.put(CLAVE_TIEMPO_INICIO, fila.tiempoInicio());
            metadatos.put(CLAVE_TIEMPO_FIN, fila.tiempoFin());
            metadatos.put(CLAVE_ORDEN, fila.orden());

            TextSegment segmento = TextSegment.from(fila.texto(), metadatos);
            // score = similitud coseno (1 - distancia); embedding no se rehidrata (no hace falta).
            coincidencias.add(new EmbeddingMatch<>(
                fila.similitud(), String.valueOf(fila.orden()), null, segmento));
        }
        return new EmbeddingSearchResult<>(coincidencias);
    }

    private Long extraerVideoId(Filter filtro) {
        if (filtro instanceof IsEqualTo igual
                && CLAVE_VIDEO.equals(igual.key())
                && igual.comparisonValue() instanceof Number numero) {
            return numero.longValue();
        }
        return null;
    }

    /** Convierte el vector a la sintaxis textual que entiende pgvector: {@code [v0,v1,...]}. */
    private String aCadenaPgvector(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    // ── Ingesta no soportada (se realiza en EmbeddingFragmentoServicio) ──────────────────────────
    @Override
    public String add(Embedding embedding) {
        throw ingestaNoSoportada();
    }

    @Override
    public void add(String id, Embedding embedding) {
        throw ingestaNoSoportada();
    }

    @Override
    public String add(Embedding embedding, TextSegment segmento) {
        throw ingestaNoSoportada();
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        throw ingestaNoSoportada();
    }

    private UnsupportedOperationException ingestaNoSoportada() {
        return new UnsupportedOperationException(
            "Este adaptador solo da soporte a la búsqueda; la ingesta se hace en EmbeddingFragmentoServicio.");
    }
}
