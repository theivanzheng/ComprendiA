package es.comprendia.infraestructura;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@ApplicationScoped
public class InicializadorExtensiones {

    private static final Logger LOG = Logger.getLogger(InicializadorExtensiones.class);

    @Inject
    Instance<DataSource> dataSourceInstance;

    void inicializar(@Observes StartupEvent ev) {
        if (!dataSourceInstance.isResolvable()) {
            LOG.debug("[pgvector] DataSource no disponible — omitiendo CREATE EXTENSION vector");
            return;
        }
        try (Connection conn = dataSourceInstance.get().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE EXTENSION IF NOT EXISTS vector");
            LOG.info("[pgvector] Extensión vector habilitada en PostgreSQL");
        } catch (Exception e) {
            LOG.debugf("[pgvector] No se pudo habilitar vector (%s) — normal en H2 o sin BD",
                e.getClass().getSimpleName());
        }
    }
}
