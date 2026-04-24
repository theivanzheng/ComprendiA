package es.comprendia.excepcion;

public class ExcepcionTranscripcionAudio extends RuntimeException {

    public ExcepcionTranscripcionAudio(String mensaje) {
        super(mensaje);
    }

    public ExcepcionTranscripcionAudio(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
