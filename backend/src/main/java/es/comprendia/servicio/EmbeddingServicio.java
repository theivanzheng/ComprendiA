package es.comprendia.servicio;

import dev.langchain4j.model.embedding.EmbeddingModel;
import es.comprendia.excepcion.ExcepcionEmbedding;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Genera embeddings de texto. Internamente usa el EmbeddingModel de LangChain4j
 * (configurado por la extensión quarkus-langchain4j-openai), en lugar de llamar a la API de
 * OpenAI con HTTP a mano. La firma pública no cambia: devuelve el vector como List&lt;Double&gt;,
 * así que el resto del sistema (RAG, fragmentos, clasificación) sigue igual.
 */
@ApplicationScoped
public class EmbeddingServicio {

    private static final Logger LOG = Logger.getLogger(EmbeddingServicio.class);

    @Inject
    EmbeddingModel embeddingModel;

    public List<Double> generarEmbedding(String texto) {
        try {
            float[] vector = embeddingModel.embed(texto).content().vector();
            List<Double> embedding = new ArrayList<>(vector.length);
            for (float valor : vector) {
                embedding.add((double) valor);
            }
            return embedding;
        } catch (Exception e) {
            LOG.errorf("[Embedding] Error generando embedding con LangChain4j: %s", e.getMessage());
            throw new ExcepcionEmbedding("Error al generar embedding: " + e.getMessage(), e);
        }
    }
}
