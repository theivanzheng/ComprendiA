package es.comprendia.excepcion;

public class ExcepcionDescargaAudio extends RuntimeException {

    public ExcepcionDescargaAudio(String mensaje) {
        super(mensaje);
    }

    public ExcepcionDescargaAudio(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
