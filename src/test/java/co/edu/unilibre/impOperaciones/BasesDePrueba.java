package co.edu.unilibre.impOperaciones;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Bases H2 en memoria para las pruebas. "La nube" se simula con una segunda base H2 en modo
 * PostgreSQL, así las pruebas no dependen de internet ni ensucian la base real de Neon.
 */
public final class BasesDePrueba {

    private BasesDePrueba() {
    }

    public static String urlMemoria(String prefijo) {
        return "jdbc:h2:mem:" + prefijo + "_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    }

    private static final String OPCIONES_H2 = ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    /** Nombre único para una "nube" que se puede apagar y encender. */
    public static String nombreNube() {
        return "nube_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * URL de una nube apagada: con IFEXISTS=TRUE la conexión falla mientras la base no exista,
     * igual que Neon sin internet. Se "enciende" con {@link #encenderNube(String)}.
     */
    public static String urlNubeApagable(String nombre) {
        return "jdbc:h2:mem:" + nombre + OPCIONES_H2 + ";IFEXISTS=TRUE";
    }

    /** Crea la base en memoria: a partir de aquí la URL apagable conecta (vuelve el internet). */
    public static void encenderNube(String nombre) throws SQLException {
        java.sql.DriverManager.getConnection("jdbc:h2:mem:" + nombre + OPCIONES_H2, "sa", "").close();
    }

    /** Modo híbrido con la nube inicialmente apagada y reintentos automáticos cada {@code segundos} (0 = manual). */
    public static GestorConexion hibridaConNubeApagada(String urlLocal, String nombreNube, int segundos) {
        GestorConexion g = new GestorConexion(ModoPersistencia.HIBRIDO_ASINCRONO,
                urlLocal, "sa", "", urlNubeApagable(nombreNube), "sa", "", segundos);
        g.inicializarBaseDeDatos();
        return g;
    }

    /** Modo híbrido: H2 local + "nube" H2 en memoria. Tablas ya creadas en ambas. */
    public static GestorConexion hibrida() {
        GestorConexion g = new GestorConexion(ModoPersistencia.HIBRIDO_ASINCRONO,
                urlMemoria("local"), "sa", "", urlMemoria("nube"), "sa", "");
        g.inicializarBaseDeDatos();
        g.esperarSincronizacion(5_000);
        return g;
    }

    /** Solo H2 local, sin nube. */
    public static GestorConexion soloLocal() {
        GestorConexion g = new GestorConexion(ModoPersistencia.H2_LOCAL,
                urlMemoria("local"), "sa", "", null, null, null);
        g.inicializarBaseDeDatos();
        return g;
    }

    /** Cuenta filas con una consulta SELECT COUNT(*) ... sobre la conexión dada. */
    public static int contar(Connection conexion, String sqlCount, Object... parametros) throws SQLException {
        try (Connection c = conexion; PreparedStatement ps = c.prepareStatement(sqlCount)) {
            for (int i = 0; i < parametros.length; i++) {
                ps.setObject(i + 1, parametros[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
