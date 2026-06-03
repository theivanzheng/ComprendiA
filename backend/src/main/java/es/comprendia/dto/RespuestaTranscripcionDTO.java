package es.comprendia.dto;

import java.util.List;

public class RespuestaTranscripcionDTO {

    private String idVideo;
    private Long idTranscripcion;
    private String titulo;
    private List<FragmentoTranscripcionDTO> fragmentos;
    private String fuenteTranscripcion;

    public RespuestaTranscripcionDTO(String idVideo, String titulo,
                                     List<FragmentoTranscripcionDTO> fragmentos,
                                     String fuenteTranscripcion) {
        this.idVideo = idVideo;
        this.titulo = titulo;
        this.fragmentos = fragmentos;
        this.fuenteTranscripcion = fuenteTranscripcion;
    }

    public String getIdVideo() {
        return idVideo;
    }

    public Long getIdTranscripcion() {
        return idTranscripcion;
    }

    public void setIdTranscripcion(Long idTranscripcion) {
        this.idTranscripcion = idTranscripcion;
    }

    public String getTitulo() {
        return titulo;
    }

    public List<FragmentoTranscripcionDTO> getFragmentos() {
        return fragmentos;
    }

    public String getFuenteTranscripcion() {
        return fuenteTranscripcion;
    }
}
