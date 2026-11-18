package co.edu.unilibre.impOperaciones;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

public class GestorConexionTest {

    private static final String SQL_TABLAS = "SELECT COUNT(*) FROM information_schema.tables "
            + "WHERE LOWER(table_name) IN ('jugadores', 'partidas', 'jugadas_tiempo_real')";

    @Test
    @DisplayName("DDL: inicializarBaseDeDatos crea las 3 tablas en H2 y en la nube (modo híbrido)")
    public void testTablasEnAmbasBases() throws Exception {
        GestorConexion g = BasesDePrueba.hibrida();

        assertEquals(3, BasesDePrueba.contar(g.obtenerConexionLocal(), SQL_TABLAS));
        assertEquals(3, BasesDePrueba.contar(g.obtenerConexionNube(), SQL_TABLAS));
    }

    @Test
    @DisplayName("DDL: inicializar dos veces no falla (CREATE TABLE IF NOT EXISTS)")
    public void testInicializarIdempotente() {
        GestorConexion g = BasesDePrueba.hibrida();
        assertDoesNotThrow(g::inicializarBaseDeDatos);
        assertTrue(g.esperarSincronizacion(5_000));
        assertEquals(0, g.getSincronizacionesFallidas());
    }

    @Test
    @DisplayName("Híbrido: la escritura en H2 es inmediata y la réplica en la nube no bloquea al jugador")
    public void testEscrituraNoBloqueante() throws Exception {
        GestorConexion g = BasesDePrueba.hibrida();
        String insertar = "INSERT INTO jugadores (id, nombre, es_bot) VALUES (1, 'Jeff', FALSE)";

        long inicio = System.nanoTime();
        CompletableFuture<Void> replica = g.ejecutarEscritura(
                con -> { try (Statement st = con.createStatement()) { st.executeUpdate(insertar); } },
                con -> {
                    try {
                        Thread.sleep(700); // simula una red lenta hacia Neon
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    try (Statement st = con.createStatement()) { st.executeUpdate(insertar); }
                });
        long milisegundos = (System.nanoTime() - inicio) / 1_000_000;

        assertTrue(milisegundos < 500, "La partida no debe esperar a la nube; tardó " + milisegundos + " ms");
        assertEquals(1, BasesDePrueba.contar(g.obtenerConexionLocal(), "SELECT COUNT(*) FROM jugadores"));
        assertFalse(replica.isDone(), "La réplica sigue en curso mientras el jugador ya tiene respuesta.");

        assertTrue(g.esperarSincronizacion(5_000));
        assertEquals(1, BasesDePrueba.contar(g.obtenerConexionNube(), "SELECT COUNT(*) FROM jugadores"));
    }

    @Test
    @DisplayName("Híbrido: si la nube está caída la partida continúa y el fallo solo se cuenta")
    public void testCaidaDeLaNubeNoInterrumpe() throws Exception {
        GestorConexion g = new GestorConexion(ModoPersistencia.HIBRIDO_ASINCRONO,
                BasesDePrueba.urlMemoria("local"), "sa", "",
                "jdbc:postgresql://127.0.0.1:1/sin_red", "nadie", "nada");

        assertDoesNotThrow(g::inicializarBaseDeDatos);
        ImpOperacionesJugadorBD dao = new ImpOperacionesJugadorBD(g);
        assertDoesNotThrow(() -> dao.crear(new co.edu.unilibre.dto.Jugador(1, "Jeff", false)));

        assertTrue(g.esperarSincronizacion(20_000));
        assertEquals(1, dao.obtenerTodos().size(), "H2 local tiene el dato aunque la nube falle.");
        assertTrue(g.getSincronizacionesFallidas() >= 2);
    }

    @Test
    @DisplayName("H2_LOCAL: nunca intenta escribir en la nube")
    public void testModoLocalNoReplica() throws Exception {
        GestorConexion g = new GestorConexion(ModoPersistencia.H2_LOCAL,
                BasesDePrueba.urlMemoria("local"), "sa", "",
                BasesDePrueba.urlMemoria("nube"), "sa", "");
        g.inicializarBaseDeDatos();
        g.esperarSincronizacion(2_000);

        assertEquals(0, g.getSincronizacionesExitosas() + g.getSincronizacionesFallidas());
        assertEquals(0, BasesDePrueba.contar(g.obtenerConexionNube(), SQL_TABLAS), "La nube no debe tocarse.");
    }

    @Test
    @DisplayName("NEON_NUBE: escribe y lee directamente de la base en la nube")
    public void testModoNube() throws Exception {
        String urlNube = BasesDePrueba.urlMemoria("nube");
        GestorConexion g = new GestorConexion(ModoPersistencia.NEON_NUBE,
                BasesDePrueba.urlMemoria("local"), "sa", "", urlNube, "sa", "");
        g.inicializarBaseDeDatos();

        try (var con = g.obtenerConexion();
             PreparedStatement ps = con.prepareStatement("INSERT INTO jugadores (id, nombre, es_bot) VALUES (?, ?, ?)")) {
            ps.setInt(1, 7);
            ps.setString(2, "Brenda");
            ps.setBoolean(3, false);
            ps.executeUpdate();
        }
        assertEquals(1, BasesDePrueba.contar(g.obtenerConexionNube(), "SELECT COUNT(*) FROM jugadores"));
        assertEquals(0, BasesDePrueba.contar(g.obtenerConexionLocal(), SQL_TABLAS), "H2 local no se usa.");
    }

    @Test
    @DisplayName("NEON_NUBE sin conexión configurada lanza IllegalStateException")
    public void testModoNubeSinConfiguracion() {
        assertThrows(IllegalStateException.class, () -> new GestorConexion(ModoPersistencia.NEON_NUBE,
                BasesDePrueba.urlMemoria("local"), "sa", "", null, null, null));
    }

    @Test
    @DisplayName("Híbrido sin nube configurada funciona solo con H2")
    public void testHibridoSinNube() {
        GestorConexion g = new GestorConexion(ModoPersistencia.HIBRIDO_ASINCRONO,
                BasesDePrueba.urlMemoria("local"), "sa", "", "", null, null);
        assertFalse(g.isNubeConfigurada());
        assertDoesNotThrow(g::inicializarBaseDeDatos);
    }
}
