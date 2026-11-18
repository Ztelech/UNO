package co.edu.unilibre.impOperaciones;

import co.edu.unilibre.dto.Partida;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba contra la base real de Neon. Solo corre si se activa a propósito con
 * TEST_NEON_REAL=true (o UNO_PROBAR_NEON=true):
 * <pre>$env:TEST_NEON_REAL="true"; mvn test</pre>
 * Toma la conexión de DB_URL/DB_USER/DB_PASSWORD o de config.local.properties y borra lo que escribe.
 */
@EnabledIf("neonActivado")
public class NeonIntegracionTest {

    static boolean neonActivado() {
        return "true".equalsIgnoreCase(System.getenv("TEST_NEON_REAL"))
                || "true".equalsIgnoreCase(System.getenv("UNO_PROBAR_NEON"));
    }

    @Test
    @DisplayName("Neon: inicializarBaseDeDatos deja las 3 tablas en el esquema public")
    public void testTablasCreadasEnNeon() throws Exception {
        GestorConexion g = new GestorConexion(ModoPersistencia.HIBRIDO_ASINCRONO,
                BasesDePrueba.urlMemoria("local"), "sa", "");
        assertTrue(g.isNubeConfigurada(), "Configure DB_URL o config.local.properties.");
        g.inicializarBaseDeDatos();
        assertTrue(g.esperarSincronizacion(30_000), "Neon no respondió a tiempo.");
        assertEquals(0, g.getPendientesDeSincronizar(), "Las tablas no se pudieron crear en Neon.");

        assertEquals(3, BasesDePrueba.contar(g.obtenerConexionNube(),
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' "
                        + "AND table_name IN ('jugadores', 'partidas', 'jugadas_tiempo_real')"));
    }

    @Test
    @DisplayName("Neon: el modo híbrido replica en la base real una partida y sus jugadas")
    public void testSincronizacionConNeon() throws Exception {
        GestorConexion g = new GestorConexion(ModoPersistencia.HIBRIDO_ASINCRONO,
                BasesDePrueba.urlMemoria("local"), "sa", "");
        assertTrue(g.isNubeConfigurada(), "Configure DB_URL o config.local.properties.");
        g.inicializarBaseDeDatos();

        String idPartida = "PRUEBA-" + UUID.randomUUID().toString().substring(0, 8);
        ImpOperacionesPartidaBD dao = new ImpOperacionesPartidaBD(g);
        try {
            long inicio = System.nanoTime();
            dao.crear(new Partida(idPartida, 123456789L, LocalDate.now(), 1, 50, false, "JUGADORES;0;1,2"));
            dao.registrarJugadaInstantanea(idPartida, 1, 0, "JUGADORES;0;1,2");
            long milisegundos = (System.nanoTime() - inicio) / 1_000_000;
            assertTrue(milisegundos < 500, "Escribir no debe esperar a Neon; tardó " + milisegundos + " ms");

            assertTrue(g.esperarSincronizacion(30_000), "Neon no respondió a tiempo.");
            assertEquals(0, g.getSincronizacionesFallidas(), "Hubo fallos al sincronizar con Neon.");
            assertEquals(1, BasesDePrueba.contar(g.obtenerConexionNube(),
                    "SELECT COUNT(*) FROM partidas WHERE id_partida = ?", idPartida));
            assertEquals(1, BasesDePrueba.contar(g.obtenerConexionNube(),
                    "SELECT COUNT(*) FROM jugadas_tiempo_real WHERE id_partida = ?", idPartida));
        } finally {
            try (Connection con = g.obtenerConexionNube()) {
                for (String tabla : new String[]{"jugadas_tiempo_real", "partidas"}) {
                    try (PreparedStatement ps = con.prepareStatement("DELETE FROM " + tabla + " WHERE id_partida = ?")) {
                        ps.setString(1, idPartida);
                        ps.executeUpdate();
                    }
                }
            }
        }
    }
}
