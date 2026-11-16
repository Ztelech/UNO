package co.edu.unilibre.modelo;

import co.edu.unilibre.dto.Carta;
import co.edu.unilibre.dto.ColorCarta;
import co.edu.unilibre.dto.Jugador;
import co.edu.unilibre.dto.Partida;
import co.edu.unilibre.dto.TipoCarta;
import co.edu.unilibre.impOperaciones.BasesDePrueba;
import co.edu.unilibre.impOperaciones.GestorConexion;
import co.edu.unilibre.impOperaciones.ImpOperacionesJugadorBD;
import co.edu.unilibre.impOperaciones.ImpOperacionesPartidaBD;
import co.edu.unilibre.impOperaciones.ModoPersistencia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** GestorPartidaUno conectado a la persistencia híbrida (H2 + "nube" simulada). */
public class GestorPartidaUnoPersistenciaTest {

    private static final long SEMILLA_PRUEBA = 987654321L;

    private GestorConexion conexion;
    private ImpOperacionesPartidaBD daoPartidas;
    private ImpOperacionesJugadorBD daoJugadores;
    private GestorPartidaUno gestor;
    private List<Jugador> jugadores;

    @BeforeEach
    public void setUp() {
        this.conexion = BasesDePrueba.hibrida();
        this.daoPartidas = new ImpOperacionesPartidaBD(this.conexion);
        this.daoJugadores = new ImpOperacionesJugadorBD(this.conexion);
        this.gestor = new GestorPartidaUno(this.daoPartidas, new GestorRanking(this.daoJugadores));
        this.jugadores = new ArrayList<>(List.of(
                new Jugador(1, "Jeff", false), new Jugador(2, "Brenda", false), new Jugador(3, "Camila", false)));
    }

    /** Primera carta legal del jugador activo, o null. */
    private Carta primeraJugable() {
        Jugador actual = this.gestor.getJugadorActivo();
        for (Carta c : actual.getMano()) {
            boolean masCuatroIlegal = c.getTipo() == TipoCarta.COMODIN_TOMA_CUATRO
                    && actual.tieneColor(this.gestor.getColorActivo());
            if (!masCuatroIlegal && c.esJugableSobre(this.gestor.getCartaDescarte(), this.gestor.getColorActivo())) {
                return c;
            }
        }
        return null;
    }

    private void jugarHastaElFinal() {
        for (int paso = 0; paso < 2_000 && !this.gestor.isPartidaTerminada(); paso++) {
            Jugador actual = this.gestor.getJugadorActivo();
            Carta c = primeraJugable();
            if (c == null) {
                this.gestor.robarCartaTurno(actual);
                this.gestor.pasarTurno();
            } else {
                if (actual.getMano().size() == 2) {
                    this.gestor.cantarUno(actual);
                }
                this.gestor.jugarTurno(actual, c, c.getColor() == ColorCarta.NEGRO ? ColorCarta.AZUL : null);
            }
        }
    }

    @Test
    @DisplayName("Cada jugada queda en H2 en el mismo instante en que se juega")
    public void testJugadaRegistradaAlInstante() {
        this.gestor.configurarPartida(this.jugadores, SEMILLA_PRUEBA);
        this.gestor.repartirManosIniciales();
        this.gestor.iniciarDescarte();

        Jugador actual = this.gestor.getJugadorActivo();
        Carta c = primeraJugable();
        if (c != null) {
            this.gestor.jugarTurno(actual, c, c.getColor() == ColorCarta.NEGRO ? ColorCarta.AZUL : null);
        } else {
            this.gestor.robarCartaTurno(actual);
        }

        // Sin esperar a la nube: lo que se lee de H2 ya coincide con el log en memoria.
        List<String> registradas = this.daoPartidas.obtenerJugadasDePartida(this.gestor.getIdPartida());
        assertEquals(List.of(this.gestor.getLogAcciones().split("\n")), registradas);
        assertTrue(registradas.get(registradas.size() - 1).startsWith(c != null ? "JUGAR;" : "ROBAR;"));
    }

    @Test
    @DisplayName("Las jugadas en tiempo real llegan también a la nube")
    public void testJugadasReplicadasEnNube() throws Exception {
        this.gestor.configurarPartida(this.jugadores, SEMILLA_PRUEBA);
        this.gestor.repartirManosIniciales();
        this.gestor.iniciarDescarte();

        assertTrue(this.conexion.esperarSincronizacion(5_000));
        int enNube = BasesDePrueba.contar(this.conexion.obtenerConexionNube(),
                "SELECT COUNT(*) FROM jugadas_tiempo_real WHERE id_partida = ?", this.gestor.getIdPartida());
        assertEquals(3, enNube, "JUGADORES, REPARTO y DESCARTE");
    }

    @Test
    @DisplayName("finalizarRonda guarda la Partida completa y actualiza el ranking en H2 y en la nube")
    public void testFinalizarRondaPersiste() throws Exception {
        this.gestor.configurarPartida(this.jugadores, SEMILLA_PRUEBA);
        this.gestor.repartirManosIniciales();
        this.gestor.iniciarDescarte();
        jugarHastaElFinal();
        assertTrue(this.gestor.isPartidaTerminada());

        int puntos = this.gestor.finalizarRonda();
        Jugador ganador = this.gestor.verificarGanador();

        List<Partida> partidas = this.daoPartidas.obtenerTodos();
        assertEquals(1, partidas.size());
        Partida guardada = partidas.get(0);
        assertEquals(this.gestor.getIdPartida(), guardada.getIdPartida());
        assertEquals(SEMILLA_PRUEBA, guardada.getSemilla());
        assertEquals(ganador.getId(), guardada.getIdGanador());
        assertEquals(puntos, guardada.getPuntosGanador());
        assertFalse(guardada.isSospechaTrampa(), "Partida honesta: el auditor no debe sospechar.");
        assertEquals(this.gestor.getLogAcciones(), guardada.getLogJugadas());

        Jugador ganadorBD = this.daoJugadores.buscarPorId(ganador.getId());
        assertEquals(puntos, ganadorBD.getPuntosAcumulados());
        assertEquals(1, ganadorBD.getPartidasGanadas());
        assertEquals(3, this.daoJugadores.obtenerTodos().size());

        assertTrue(this.conexion.esperarSincronizacion(5_000));
        assertEquals(1, BasesDePrueba.contar(this.conexion.obtenerConexionNube(),
                "SELECT COUNT(*) FROM partidas WHERE id_partida = ?", this.gestor.getIdPartida()));
        assertEquals(3, BasesDePrueba.contar(this.conexion.obtenerConexionNube(), "SELECT COUNT(*) FROM jugadores"));
        assertEquals(0, this.conexion.getSincronizacionesFallidas());
    }

    @Test
    @DisplayName("finalizarRonda dos veces no duplica la partida")
    public void testFinalizarDosVeces() {
        this.gestor.configurarPartida(this.jugadores, SEMILLA_PRUEBA);
        this.gestor.repartirManosIniciales();
        this.gestor.iniciarDescarte();
        jugarHastaElFinal();

        int primera = this.gestor.finalizarRonda();
        assertEquals(primera, this.gestor.finalizarRonda());
        assertEquals(1, this.daoPartidas.obtenerTodos().size());
    }

    @Test
    @DisplayName("Partida completa sin internet: al volver la red, la nube queda igual que H2")
    public void testPartidaSinInternetLuegoSeSincroniza() throws Exception {
        String urlLocal = BasesDePrueba.urlMemoria("local");
        String nombreNube = BasesDePrueba.nombreNube();
        GestorConexion g = BasesDePrueba.hibridaConNubeApagada(urlLocal, nombreNube, 0);
        ImpOperacionesPartidaBD dao = new ImpOperacionesPartidaBD(g);
        this.gestor = new GestorPartidaUno(dao, new GestorRanking(new ImpOperacionesJugadorBD(g)));

        this.gestor.configurarPartida(this.jugadores, SEMILLA_PRUEBA);
        this.gestor.repartirManosIniciales();
        this.gestor.iniciarDescarte();
        jugarHastaElFinal();
        this.gestor.finalizarRonda();
        assertTrue(g.esperarSincronizacion(10_000));
        assertTrue(g.getPendientesDeSincronizar() > 0);

        BasesDePrueba.encenderNube(nombreNube);
        g.reintentarPendientes().get();

        assertEquals(0, g.getPendientesDeSincronizar());
        String id = this.gestor.getIdPartida();
        assertEquals(BasesDePrueba.contar(g.obtenerConexionLocal(),
                        "SELECT COUNT(*) FROM jugadas_tiempo_real WHERE id_partida = ?", id),
                BasesDePrueba.contar(g.obtenerConexionNube(),
                        "SELECT COUNT(*) FROM jugadas_tiempo_real WHERE id_partida = ?", id));
        assertEquals(1, BasesDePrueba.contar(g.obtenerConexionNube(),
                "SELECT COUNT(*) FROM partidas WHERE id_partida = ?", id));
        assertEquals(3, BasesDePrueba.contar(g.obtenerConexionNube(), "SELECT COUNT(*) FROM jugadores"));
    }

    @Test
    @DisplayName("Si la nube está caída la partida se juega completa igual")
    public void testPartidaSinInternet() {
        GestorConexion sinRed = new GestorConexion(ModoPersistencia.HIBRIDO_ASINCRONO,
                BasesDePrueba.urlMemoria("local"), "sa", "",
                "jdbc:postgresql://127.0.0.1:1/sin_red", "nadie", "nada");
        sinRed.inicializarBaseDeDatos();
        ImpOperacionesPartidaBD dao = new ImpOperacionesPartidaBD(sinRed);
        GestorPartidaUno g = new GestorPartidaUno(dao, new GestorRanking(new ImpOperacionesJugadorBD(sinRed)));
        this.gestor = g;

        g.configurarPartida(this.jugadores, SEMILLA_PRUEBA);
        g.repartirManosIniciales();
        g.iniciarDescarte();
        assertDoesNotThrow(this::jugarHastaElFinal);
        assertDoesNotThrow(g::finalizarRonda);
        assertEquals(1, dao.obtenerTodos().size(), "La partida quedó guardada en H2.");
    }
}
