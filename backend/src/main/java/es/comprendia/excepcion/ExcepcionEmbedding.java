package es.comprendia.excepcion;

public class ExcepcionEmbedding extends RuntimeException {
    public ExcepcionEmbedding(String mensaje) { super(mensaje); }
    public ExcepcionEmbedding(String mensaje, Throwable causa) { super(mensaje, causa); }
}
