package es.comprendia.repositorio;

import es.comprendia.entidad.Asignatura;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class AsignaturaRepositorio implements PanacheRepository<Asignatura> {

    public List<Asignatura> buscarTodasOrdenadasPorNombre() {
        return listAll(io.quarkus.panache.common.Sort.by("nombre").ascending());
    }
}
