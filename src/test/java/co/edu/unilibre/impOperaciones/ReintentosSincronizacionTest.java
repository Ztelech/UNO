package co.edu.unilibre.impOperaciones;

import co.edu.unilibre.dto.Jugador;
import co.edu.unilibre.dto.Partida;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Cola de pendientes en H2 y reintentos hacia la nube cuando vuelve el internet. */
public class ReintentosSincronizacionTest {

    private String urlLocal;
    private String nombreNube;

    @BeforeEach
    public void setUp() {
        this.urlLocal = BasesDePrueba.urlMemoria("local");
        this.nombreNube = BasesDePrueba.nombreNube();
    }

    private int enNube(GestorConexion g, String sqlCount, Object... parametros) throws Exception {
        return BasesDePrueba.contar(g.obtenerConexionNube(), sqlCount, parametros);
    }

    @Test
    @DisplayName("Sin internet: cada escritura fallida queda anotada en la cola local")
    public void testFalloQuedaPendiente() {
        GestorConexion g = BasesDePrueba.hibridaConNubeApagada(this.urlLocal, this.nombreNube, 0);
        new ImpOperacionesJugadorBD(g).crear(new Jugador(1, "Jeff", false));

        assertTrue(g.esperarSincronizacion(5_000));
        assertEquals(2, g.getPendientesDeSincronizar(), "Las tablas de la nube y el jugador 1.");
    }

    @Test
    @DisplayName("Al volver el internet, el reintento copia todo y vacía la cola")
    public void testReconexionSincronizaTodo() throws Exception {
        GestorConexion g = BasesDePrueba.hibridaConNubeApagada(this.urlLocal, this.nombreNube, 0);
        ImpOperacionesJugadorBD jugadores = new ImpOperacionesJugadorBD(g);
        ImpOperacionesPartidaBD partidas = new ImpOperacionesPartidaBD(g);

        Jugador jeff = new Jugador(1, "Jeff", false);
        jugadores.crear(jeff);
        jeff.setPuntosAcumulados(50);
        jugadores.actualizar(jeff); // misma entidad: sigue siendo un solo pendiente
        partidas.crear(new Partida("P-1", 42L, LocalDate.of(2026, 11, 15), 1, 50, false, "JUGADORES;0;1,2"));
        for (int turno = 1; turno <= 3; turno++) {
            partidas.registrarJugadaInstantanea("P-1", turno, 1, "ACCION " + turno);
        }
        assertTrue(g.esperarSincronizacion(5_000));
        assertEquals(6, g.getPendientesDeSincronizar(), "Esquema + jugador + partida + 3 jugadas.");

        BasesDePrueba.encenderNube(this.nombreNube);
        assertEquals(6, g.reintentarPendientes().get());

        assertEquals(0, g.getPendientesDeSincronizar());
        assertEquals(1, enNube(g, "SELECT COUNT(*) FROM jugadores WHERE id = 1 AND puntos_acumulados = 50"),
                "La nube recibe el estado más reciente, no el de la primera escritura.");
        assertEquals(1, enNube(g, "SELECT COUNT(*) FROM partidas WHERE id_partida = 'P-1'"));
        assertEquals(3, enNube(g, "SELECT COUNT(*) FROM jugadas_tiempo_real WHERE id_partida = 'P-1'"));
    }

    @Test
    @DisplayName("Si la nube sigue caída, el reintento no pierde nada y cuenta el intento")
    public void testNubeSigueCaida() throws Exception {
        GestorConexion g = BasesDePrueba.hibridaConNubeApagada(this.urlLocal, this.nombreNube, 0);
        new ImpOperacionesJugadorBD(g).crear(new Jugador(1, "Jeff", false));
        assertTrue(g.esperarSincronizacion(5_000));

        assertEquals(0, g.reintentarPendientes().get());
        assertEquals(2, g.getPendientesDeSincronizar());
        assertEquals(1, BasesDePrueba.contar(g.obtenerConexionLocal(),
                "SELECT COUNT(*) FROM sincronizacion_pendiente WHERE entidad = 'JUGADOR' AND intentos = 2"));
    }

    @Test
    @DisplayName("La cola vive en H2: sobrevive a un reinicio de la aplicación")
    public void testPendientesSobrevivenReinicio() throws Exception {
        GestorConexion antes = BasesDePrueba.hibridaConNubeApagada(this.urlLocal, this.nombreNube, 0);
        new ImpOperacionesJugadorBD(antes).crear(new Jugador(7, "Brenda", false));
        assertTrue(antes.esperarSincronizacion(5_000));
        antes.cerrar();

        BasesDePrueba.encenderNube(this.nombreNube);
        GestorConexion despues = new GestorConexion(ModoPersistencia.HIBRIDO_ASINCRONO, this.urlLocal, "sa", "",
                BasesDePrueba.urlNubeApagable(this.nombreNube), "sa", "", 0);
        new ImpOperacionesJugadorBD(despues); // registra cómo copiar jugadores

        assertEquals(2, despues.reintentarPendientes().get());
        assertEquals(0, despues.getPendientesDeSincronizar());
        assertEquals(1, enNube(despues, "SELECT COUNT(*) FROM jugadores WHERE nombre = 'Brenda'"));
    }

    @Test
    @DisplayName("El reintento automático sincroniza solo cuando vuelve el internet")
    public void testReintentoAutomatico() throws Exception {
        GestorConexion g = BasesDePrueba.hibridaConNubeApagada(this.urlLocal, this.nombreNube, 1);
        new ImpOperacionesJugadorBD(g).crear(new Jugador(1, "Jeff", false));
        assertTrue(g.esperarSincronizacion(5_000));
        assertTrue(g.getPendientesDeSincronizar() > 0);

        BasesDePrueba.encenderNube(this.nombreNube);
        long limite = System.currentTimeMillis() + 10_000;
        while (g.getPendientesDeSincronizar() > 0 && System.currentTimeMillis() < limite) {
            Thread.sleep(100);
        }

        assertEquals(0, g.getPendientesDeSincronizar(), "El reintento programado debió vaciar la cola.");
        assertEquals(1, enNube(g, "SELECT COUNT(*) FROM jugadores"));
    }

    @Test
    @DisplayName("En modo H2_LOCAL no hay cola de reintentos")
    public void testSinColaEnModoLocal() throws Exception {
        GestorConexion g = BasesDePrueba.soloLocal();
        new ImpOperacionesJugadorBD(g).crear(new Jugador(1, "Jeff", false));
        assertEquals(0, g.getPendientesDeSincronizar());
        assertEquals(0, g.reintentarPendientes().get());
    }
}
