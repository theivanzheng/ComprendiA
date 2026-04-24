package es.comprendia.dto;

public class FragmentoTranscripcionDTO {

    private String texto;
    private double tiempoInicio;
    private double tiempoFin;

    public FragmentoTranscripcionDTO(String texto, double tiempoInicio, double tiempoFin) {
        this.texto = texto;
        this.tiempoInicio = tiempoInicio;
        this.tiempoFin = tiempoFin;
    }

    public String getTexto() {
        return texto;
    }

    public double getTiempoInicio() {
        return tiempoInicio;
    }

    public double getTiempoFin() {
        return tiempoFin;
    }
}
