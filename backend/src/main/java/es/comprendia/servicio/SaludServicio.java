package es.comprendia.servicio;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SaludServicio {

    public String obtenerMensajeSalud() {
        return "ComprendiA backend activo";
    }
}
