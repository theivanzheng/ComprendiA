package es.comprendia.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EstadoTrabajoDTO {

    public enum Fase { DESCARGANDO, TRANSCRIBIENDO, GUARDANDO, EMBEDDINGS, ANALIZANDO, COMPLETADO, CANCELADO, ERROR }

    private final String id;
    private volatile Fase fase;
    private volatile RespuestaTranscripcionDTO resultado;
    private volatile String error;

    public EstadoTrabajoDTO(String id, Fase fase) {
        this.id = id;
        this.fase = fase;
    }

    public String getId() { return id; }
    public Fase getFase() { return fase; }
    public void setFase(Fase fase) { this.fase = fase; }
    public RespuestaTranscripcionDTO getResultado() { return resultado; }
    public void setResultado(RespuestaTranscripcionDTO resultado) { this.resultado = resultado; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
