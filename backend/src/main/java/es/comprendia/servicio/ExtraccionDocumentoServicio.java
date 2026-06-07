package es.comprendia.servicio;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Extrae el texto de un documento (PDF, Word, PowerPoint, TXT…) con Apache Tika —"la navaja suiza"
 * de la extracción— y lo trocea en fragmentos manejables para vectorizar. Todo es local: no se
 * llama a ningún modelo de lenguaje, por lo que no consume tokens de chat.
 */
@ApplicationScoped
public class ExtraccionDocumentoServicio {

    private static final Logger LOG = Logger.getLogger(ExtraccionDocumentoServicio.class);

    // Tamaño de cada trozo (en caracteres) y solapamiento entre trozos para no cortar ideas.
    private static final int MAX_CARACTERES_TROZO = 900;
    private static final int SOLAPAMIENTO_CARACTERES = 150;

    /**
     * Extrae el texto del documento y lo divide en fragmentos. Devuelve la lista de textos (vacía si
     * el documento no tiene texto extraíble, p. ej. un PDF escaneado sin OCR).
     */
    public List<String> extraerYTrocear(byte[] contenido, String nombreArchivo) {
        String texto;
        try {
            Document documento = new ApacheTikaDocumentParser().parse(new ByteArrayInputStream(contenido));
            texto = documento.text();
        } catch (Exception e) {
            LOG.errorf("[Documento] No se pudo extraer texto de '%s': %s", nombreArchivo, e.getMessage());
            return List.of();
        }

        if (texto == null || texto.isBlank()) {
            LOG.warnf("[Documento] '%s' no contiene texto extraíble (¿escaneado sin OCR?)", nombreArchivo);
            return List.of();
        }

        DocumentSplitter troceador = DocumentSplitters.recursive(MAX_CARACTERES_TROZO, SOLAPAMIENTO_CARACTERES);
        List<String> trozos = new ArrayList<>();
        for (TextSegment segmento : troceador.split(Document.from(texto))) {
            String limpio = segmento.text().strip();
            if (!limpio.isEmpty()) {
                trozos.add(limpio);
            }
        }
        LOG.infof("[Documento] '%s': %d caracteres -> %d fragmentos", nombreArchivo, texto.length(), trozos.size());
        return trozos;
    }
}
