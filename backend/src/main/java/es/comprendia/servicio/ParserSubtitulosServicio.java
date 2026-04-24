package es.comprendia.servicio;

import es.comprendia.dto.FragmentoTranscripcionDTO;
import es.comprendia.excepcion.ExcepcionTranscripcionYoutube;
import jakarta.enterprise.context.ApplicationScoped;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class ParserSubtitulosServicio {

    public String extraerTitulo(String htmlPagina) {
        // Intenta con og:title, luego con name="title"
        for (String marcador : List.of("property=\"og:title\" content=\"", "name=\"title\" content=\"")) {
            int inicio = htmlPagina.indexOf(marcador);
            if (inicio != -1) {
                inicio += marcador.length();
                int fin = htmlPagina.indexOf('"', inicio);
                if (fin != -1) return htmlPagina.substring(inicio, fin);
            }
        }
        return "Vídeo de YouTube";
    }

    // Extrae la URL firmada de subtítulos desde el HTML de la página de YouTube.
    // YouTube embebe captionTracks en ytInitialPlayerResponse con baseUrl escapada.
    public String extraerUrlSubtitulos(String htmlPagina) {
        int posCaptions = htmlPagina.indexOf("\"captionTracks\":");
        if (posCaptions == -1) {
            throw new ExcepcionTranscripcionYoutube(
                "El vídeo no tiene subtítulos disponibles o son privados");
        }

        String marcadorUrl = "\"baseUrl\":\"";
        int inicioUrl = htmlPagina.indexOf(marcadorUrl, posCaptions);
        if (inicioUrl == -1) {
            throw new ExcepcionTranscripcionYoutube("No se encontró la URL de subtítulos");
        }
        inicioUrl += marcadorUrl.length();

        int finUrl = htmlPagina.indexOf('"', inicioUrl);
        if (finUrl == -1) {
            throw new ExcepcionTranscripcionYoutube("URL de subtítulos malformada");
        }

        return htmlPagina.substring(inicioUrl, finUrl)
            .replace("\\u0026", "&")
            .replace("\\/", "/");
    }

    public List<FragmentoTranscripcionDTO> parsearXmlSubtitulos(String xml) {
        try {
            DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
            // Prevenir ataques XXE
            fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder constructor = fabrica.newDocumentBuilder();
            Document documento = constructor.parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            NodeList nodos = documento.getElementsByTagName("text");
            List<FragmentoTranscripcionDTO> fragmentos = new ArrayList<>();

            for (int i = 0; i < nodos.getLength(); i++) {
                Element elemento = (Element) nodos.item(i);
                double tiempoInicio = Double.parseDouble(elemento.getAttribute("start"));
                String durAttr = elemento.getAttribute("dur");
                double duracion = durAttr.isEmpty() ? 0.0 : Double.parseDouble(durAttr);
                String texto = elemento.getTextContent().trim();

                if (!texto.isEmpty()) {
                    fragmentos.add(new FragmentoTranscripcionDTO(texto, tiempoInicio, tiempoInicio + duracion));
                }
            }

            if (fragmentos.isEmpty()) {
                throw new ExcepcionTranscripcionYoutube("No se encontraron fragmentos en los subtítulos");
            }

            return fragmentos;
        } catch (ExcepcionTranscripcionYoutube e) {
            throw e;
        } catch (Exception e) {
            throw new ExcepcionTranscripcionYoutube("Error al parsear el XML de subtítulos: " + e.getMessage());
        }
    }
}
