package es.comprendia.infraestructura;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@ApplicationScoped
public class InicializadorExtensiones {

    private static final Logger LOG = Logger.getLogger(InicializadorExtensiones.class);

    @Inject
    DataSource dataSource;

    void inicializar(@Observes StartupEvent ev) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE EXTENSION IF NOT EXISTS vector");
            LOG.info("Extensión pgvector habilitada");
        } catch (Exception e) {
            LOG.debugf("No se pudo habilitar pgvector (normal en H2/tests): %s", e.getMessage());
        }
    }
}
