package es.comprendia.infraestructura;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigSource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Permite definir la conexión a la base de datos con UNA sola variable, DATABASE_URL,
 * en formato libpq (postgresql://usuario:password@host:puerto/db?sslmode=require).
 * Como el driver JDBC no acepta ese formato directamente, esta fábrica lo parsea y
 * deriva quarkus.datasource.jdbc.url / username / password.
 *
 * Si DATABASE_URL no existe, no aporta nada y se usan los valores de application.properties.
 * Así el secreto vive solo en backend/.env (ignorado por git).
 */
public class DatasourceUrlConfigSourceFactory implements ConfigSourceFactory {

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        ConfigValue valor = context.getValue("DATABASE_URL");
        String raw = valor != null ? valor.getValue() : null;
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }

        try {
            Map<String, String> propiedades = parsear(raw.trim());
            return List.of(new ConfigSource() {
                @Override public Map<String, String> getProperties() { return propiedades; }
                @Override public Set<String> getPropertyNames() { return propiedades.keySet(); }
                @Override public String getValue(String nombre) { return propiedades.get(nombre); }
                @Override public String getName() { return "datasource-desde-DATABASE_URL"; }
                @Override public int getOrdinal() { return 290; }
            });
        } catch (Exception e) {
            // Si no se puede parsear, se ignora y se cae a application.properties
            return Collections.emptyList();
        }
    }

    @Override
    public OptionalInt getPriority() {
        return OptionalInt.of(290);
    }

    private Map<String, String> parsear(String databaseUrl) {
        URI uri = URI.create(databaseUrl);

        String usuario = null;
        String password = null;
        String userInfo = uri.getUserInfo();
        if (userInfo != null) {
            int sep = userInfo.indexOf(':');
            usuario = sep >= 0 ? userInfo.substring(0, sep) : userInfo;
            password = sep >= 0 ? userInfo.substring(sep + 1) : null;
            usuario = URLDecoder.decode(usuario, StandardCharsets.UTF_8);
            if (password != null) password = URLDecoder.decode(password, StandardCharsets.UTF_8);
        }

        String host = uri.getHost();
        int puerto = uri.getPort();
        String db = uri.getPath() == null ? "" : uri.getPath();
        String query = limpiarQuery(uri.getQuery());

        String jdbcUrl = "jdbc:postgresql://" + host
            + (puerto > 0 ? ":" + puerto : "")
            + db
            + (query.isEmpty() ? "" : "?" + query);

        Map<String, String> propiedades = new HashMap<>();
        propiedades.put("quarkus.datasource.jdbc.url", jdbcUrl);
        if (usuario != null) propiedades.put("quarkus.datasource.username", usuario);
        if (password != null) propiedades.put("quarkus.datasource.password", password);
        return propiedades;
    }

    // Conserva sslmode; descarta channelBinding (el driver pgjdbc no lo soporta y rompía la conexión)
    private String limpiarQuery(String query) {
        if (query == null || query.isBlank()) {
            return "sslmode=require";
        }
        List<String> partes = new ArrayList<>();
        boolean tieneSsl = false;
        for (String parte : query.split("&")) {
            String clave = parte.toLowerCase();
            if (clave.startsWith("channelbinding")) continue;
            if (clave.startsWith("sslmode")) tieneSsl = true;
            partes.add(parte);
        }
        if (!tieneSsl) partes.add("sslmode=require");
        return String.join("&", partes);
    }
}
