package es.comprendia.servicio;

import es.comprendia.dto.FragmentoTranscripcionDTO;
import es.comprendia.dto.RespuestaTranscripcionDTO;
import es.comprendia.excepcion.ExcepcionTranscripcionYoutube;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class YoutubeTranscripcionServicio {

    @Inject
    ClienteYoutubeServicio clienteYoutubeServicio;

    @Inject
    ParserSubtitulosServicio parserSubtitulosServicio;

    public RespuestaTranscripcionDTO obtenerTranscripcion(String idVideo) {
        String htmlPagina = clienteYoutubeServicio.obtenerPaginaYoutube(idVideo);
        String titulo = parserSubtitulosServicio.extraerTitulo(htmlPagina);
        String urlSubtitulos = parserSubtitulosServicio.extraerUrlSubtitulos(htmlPagina);
        String xmlSubtitulos = clienteYoutubeServicio.obtenerSubtitulosXml(urlSubtitulos);
        List<FragmentoTranscripcionDTO> fragmentos = parserSubtitulosServicio.parsearXmlSubtitulos(xmlSubtitulos);

        return new RespuestaTranscripcionDTO(idVideo, titulo, fragmentos, "SCRAPING");
    }
}
