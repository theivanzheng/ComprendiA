package es.comprendia.repositorio;

import es.comprendia.entidad.Profesor;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ProfesorRepositorio implements PanacheRepository<Profesor> {

    public List<Profesor> buscarTodosOrdenados() {
        return listAll(Sort.by("nombre").ascending());
    }

    public Optional<Profesor> buscarPorNombre(String nombre) {
        return find("LOWER(nombre) = LOWER(?1)", nombre).firstResultOptional();
    }
}
