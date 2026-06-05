package es.comprendia.infraestructura;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigSource;

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

    // Parseo MANUAL y tolerante (no usa java.net.URI, que devuelve host=null si la
    // contraseña tiene caracteres especiales). Formato esperado:
    //   postgresql://usuario:password@host[:puerto]/db[?query]
    private Map<String, String> parsear(String databaseUrl) {
        String s = databaseUrl.trim();
        int esquema = s.indexOf("://");
        if (esquema < 0) {
            throw new IllegalArgumentException("DATABASE_URL sin esquema postgresql://");
        }
        String resto = s.substring(esquema + 3);

        // Credenciales: hasta el último '@' (el host nunca contiene '@')
        String usuario = null;
        String password = null;
        int arroba = resto.lastIndexOf('@');
        if (arroba >= 0) {
            String credenciales = resto.substring(0, arroba);
            resto = resto.substring(arroba + 1);
            int dosPuntos = credenciales.indexOf(':');
            if (dosPuntos >= 0) {
                usuario = credenciales.substring(0, dosPuntos);
                password = credenciales.substring(dosPuntos + 1);
            } else {
                usuario = credenciales;
            }
            usuario = decodificar(usuario);
            if (password != null) password = decodificar(password);
        }

        // resto = host[:puerto][/db][?query]
        String query = null;
        int interrogacion = resto.indexOf('?');
        if (interrogacion >= 0) {
            query = resto.substring(interrogacion + 1);
            resto = resto.substring(0, interrogacion);
        }
        String db = "";
        int barra = resto.indexOf('/');
        if (barra >= 0) {
            db = resto.substring(barra); // incluye la '/'
            resto = resto.substring(0, barra);
        }
        String hostPuerto = resto; // host[:puerto]
        if (hostPuerto.isBlank()) {
            throw new IllegalArgumentException("DATABASE_URL sin host");
        }

        String jdbcQuery = limpiarQuery(query);
        String jdbcUrl = "jdbc:postgresql://" + hostPuerto + db
            + (jdbcQuery.isEmpty() ? "" : "?" + jdbcQuery);

        Map<String, String> propiedades = new HashMap<>();
        propiedades.put("quarkus.datasource.jdbc.url", jdbcUrl);
        if (usuario != null && !usuario.isBlank()) propiedades.put("quarkus.datasource.username", usuario);
        if (password != null) propiedades.put("quarkus.datasource.password", password);
        return propiedades;
    }

    private String decodificar(String valor) {
        try {
            return URLDecoder.decode(valor, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return valor; // si no es URL-encoding válido, usar tal cual
        }
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
