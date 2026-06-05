package es.comprendia.servicio;

import es.comprendia.dto.FragmentoTranscripcionDTO;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Reagrupa los fragmentos de transcripción (que a veces vienen en trozos de 1-2 segundos, p. ej.
 * de los subtítulos de YouTube) en ventanas más grandes de ~25 segundos, conservando las marcas
 * de tiempo (inicio del primero, fin del último). Fragmentos más grandes tienen más contenido,
 * lo que mejora la calidad de los embeddings y de la búsqueda semántica (recuperación RAG).
 *
 * Se hace a medida —y no con un troceador genérico— precisamente para preservar los tiempos y
 * poder seguir saltando al minuto exacto del vídeo.
 */
@ApplicationScoped
public class ReagrupadorFragmentosServicio {

    private static final Logger LOG = Logger.getLogger(ReagrupadorFragmentosServicio.class);

    // Se cierra una ventana cuando alcanza esta duración O este nº de caracteres.
    private static final double DURACION_OBJETIVO_SEGUNDOS = 25.0;
    private static final int MAX_CARACTERES = 600;

    public List<FragmentoTranscripcionDTO> reagrupar(List<FragmentoTranscripcionDTO> originales) {
        if (originales == null || originales.size() <= 1) {
            return originales;
        }

        List<FragmentoTranscripcionDTO> resultado = new ArrayList<>();
        StringBuilder texto = new StringBuilder();
        double inicio = -1;
        double fin = 0;

        for (FragmentoTranscripcionDTO fragmento : originales) {
            if (fragmento.getTexto() == null || fragmento.getTexto().isBlank()) {
                continue;
            }
            if (inicio < 0) {
                inicio = fragmento.getTiempoInicio();
            }
            if (texto.length() > 0) {
                texto.append(' ');
            }
            texto.append(fragmento.getTexto().strip());
            fin = fragmento.getTiempoFin();

            boolean duracionAlcanzada = (fin - inicio) >= DURACION_OBJETIVO_SEGUNDOS;
            boolean textoLargo = texto.length() >= MAX_CARACTERES;
            if (duracionAlcanzada || textoLargo) {
                resultado.add(new FragmentoTranscripcionDTO(texto.toString(), inicio, fin));
                texto.setLength(0);
                inicio = -1;
            }
        }
        // Resto pendiente (la última ventana, aunque no llegue al objetivo).
        if (texto.length() > 0) {
            resultado.add(new FragmentoTranscripcionDTO(texto.toString(), inicio, fin));
        }

        LOG.infof("[Troceado] Reagrupados %d fragmentos pequeños en %d fragmentos de ~%.0fs",
            originales.size(), resultado.size(), DURACION_OBJETIVO_SEGUNDOS);
        return resultado;
    }
}
