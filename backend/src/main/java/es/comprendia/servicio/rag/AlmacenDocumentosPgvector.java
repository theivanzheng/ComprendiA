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
import es.comprendia.dto.FuenteDocumentoDTO;
import es.comprendia.repositorio.FragmentoDocumentoRepositorio;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/**
 * Igual que {@link AlmacenEmbeddingsPgvector} pero sobre la tabla de documentos del curso
 * ({@code fragmentos_documento}). Implementa el {@link EmbeddingStore} de LangChain4j: la búsqueda
 * se filtra por vídeo (metadato {@code videoId}) y cada resultado lleva en su metadata el nombre del
 * documento de origen. La ingesta (add*) no se usa aquí: se hace en {@code DocumentoServicio}.
 */
@ApplicationScoped
public class AlmacenDocumentosPgvector implements EmbeddingStore<TextSegment> {

    public static final String CLAVE_VIDEO = "videoId";
    public static final String CLAVE_DOCUMENTO = "documento";

    @Inject
    FragmentoDocumentoRepositorio fragmentoDocumentoRepositorio;

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest peticion) {
        Long videoId = extraerVideoId(peticion.filter());
        if (videoId == null) {
            throw new IllegalArgumentException(
                "La búsqueda requiere un filtro metadataKey(\"" + CLAVE_VIDEO + "\").isEqualTo(idVideo)");
        }

        String vectorPgvector = aCadenaPgvector(peticion.queryEmbedding().vector());
        List<FuenteDocumentoDTO> filas =
            fragmentoDocumentoRepositorio.buscarPorSimilitud(videoId, vectorPgvector, peticion.maxResults());

        List<EmbeddingMatch<TextSegment>> coincidencias = new ArrayList<>(filas.size());
        int indice = 0;
        for (FuenteDocumentoDTO fila : filas) {
            Metadata metadatos = new Metadata();
            metadatos.put(CLAVE_VIDEO, videoId);
            metadatos.put(CLAVE_DOCUMENTO, fila.nombreDocumento());
            TextSegment segmento = TextSegment.from(fila.texto(), metadatos);
            // embeddingId no puede ser null/blank (LangChain4j lo valida); usamos el índice del resultado.
            coincidencias.add(new EmbeddingMatch<>(fila.similitud(), "doc-" + indice++, null, segmento));
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

    // ── Ingesta no soportada (se realiza en DocumentoServicio) ───────────────────────────────────
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
            "Este adaptador solo da soporte a la búsqueda; la ingesta se hace en DocumentoServicio.");
    }
}
