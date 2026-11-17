package co.edu.unilibre.modelo;

import co.edu.unilibre.dto.Carta;
import co.edu.unilibre.dto.ColorCarta;
import co.edu.unilibre.dto.Jugador;
import co.edu.unilibre.dto.Partida;
import co.edu.unilibre.impOperaciones.BasesDePrueba;
import co.edu.unilibre.impOperaciones.GestorConexion;
import co.edu.unilibre.impOperaciones.ImpOperacionesJugadorBD;
import co.edu.unilibre.impOperaciones.ImpOperacionesPartidaBD;
import co.edu.unilibre.impOperaciones.ModoPersistencia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** GestorPartidaUno conectado a la persistencia híbrida (H2 + "nube" simulada) y al ranking. */
public class GestorPartidaUnoPersistenciaTest {

    private static final long SEMILLA_PRUEBA = 987654321L;

    private GestorConexion conexion;
    private ImpOperacionesPartidaBD daoPartidas;
    private ImpOperacionesJugadorBD daoJugadores;
    private GestorRanking ranking;
    private GestorPartidaUno gestor;
    private List<Jugador> jugadores;

    @BeforeEach
    public void setUp() {
        this.conexion = BasesDePrueba.hibrida();
        this.daoPartidas = new ImpOperacionesPartidaBD(this.conexion);
        this.daoJugadores = new ImpOperacionesJugadorBD(this.conexion);
        this.ranking = new GestorRanking(this.daoJugadores);
        this.gestor = new GestorPartidaUno(this.daoPartidas);
        // RF20: los jugadores entran con su perfil.
        this.jugadores = new java.util.ArrayList<>(List.of(
                this.ranking.registrarOSeleccionarPerfil("Jeff"),
                this.ranking.registrarOSeleccionarPerfil("Brenda"),
                this.ranking.registrarOSeleccionarPerfil("Camila")));
    }

    private void iniciar(GestorPartidaUno g) {
        g.configurarPartida(this.jugadores, SEMILLA_PRUEBA);
        g.repartirManosIniciales();
        g.iniciarDescarte();
    }

    @Test
    @DisplayName("RNF04: Cada jugada queda en H2 en el mismo instante en que se juega")
    public void testJugadaRegistradaAlInstante() {
        iniciar(this.gestor);
        Jugador actual = this.gestor.obtenerJugadorActivo();
        if (this.gestor.isColorInicialPendiente()) {
            this.gestor.elegirColorInicial(actual, ColorCarta.ROJO);
        } else {
            Carta c = SimuladorPartida.primeraJugable(this.gestor, actual);
            if (c != null) {
                this.gestor.jugarTurno(actual, c, c.getColor() == ColorCarta.NEGRO ? ColorCarta.AZUL : null);
            } else {
                this.gestor.robarCartaUno(actual);
            }
        }

        // Sin esperar a la nube: lo que se lee de H2 ya coincide con el log en memoria.
        List<String> registradas = this.daoPartidas.obtenerJugadasDePartida(this.gestor.getIdPartida());
        assertEquals(List.of(this.gestor.getLogAcciones().split("\n")), registradas);
    }

    @Test
    @DisplayName("Las jugadas en tiempo real llegan también a la nube")
    public void testJugadasReplicadasEnNube() throws Exception {
        iniciar(this.gestor);
        int enLocal = this.daoPartidas.obtenerJugadasDePartida(this.gestor.getIdPartida()).size();

        assertTrue(this.conexion.esperarSincronizacion(5_000));
        assertEquals(enLocal, BasesDePrueba.contar(this.conexion.obtenerConexionNube(),
                "SELECT COUNT(*) FROM jugadas_tiempo_real WHERE id_partida = ?", this.gestor.getIdPartida()));
    }

    @Test
    @DisplayName("RF21 & RF22: al cerrar la ronda se guarda la Partida y el ranking en H2 y en la nube")
    public void testFinalizarRondaPersiste() throws Exception {
        iniciar(this.gestor);
        SimuladorPartida.jugarHastaElFinal(this.gestor, 3_000);
        assertTrue(this.gestor.isPartidaTerminada());

        int puntos = this.gestor.finalizarRonda();
        Jugador ganador = this.gestor.verificarGanador();
        this.ranking.procesarResultadosPartida(ganador, puntos, this.jugadores);

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
        assertEquals(ganador.getNombre(), this.ranking.generarTablaPosicionesTop10().get(0).getNombre());

        assertTrue(this.conexion.esperarSincronizacion(5_000));
        assertEquals(1, BasesDePrueba.contar(this.conexion.obtenerConexionNube(),
                "SELECT COUNT(*) FROM partidas WHERE id_partida = ?", this.gestor.getIdPartida()));
        assertEquals(3, BasesDePrueba.contar(this.conexion.obtenerConexionNube(), "SELECT COUNT(*) FROM jugadores"));
        assertEquals(0, this.conexion.getSincronizacionesFallidas());
    }

    @Test
    @DisplayName("finalizarRonda dos veces no duplica la partida")
    public void testFinalizarDosVeces() {
        iniciar(this.gestor);
        SimuladorPartida.jugarHastaElFinal(this.gestor, 3_000);

        int primera = this.gestor.finalizarRonda();
        assertEquals(primera, this.gestor.finalizarRonda());
        assertEquals(1, this.daoPartidas.obtenerTodos().size());
    }

    @Test
    @DisplayName("Partida completa sin internet: al volver la red, la nube queda igual que H2")
    public void testPartidaSinInternetLuegoSeSincroniza() throws Exception {
        String nombreNube = BasesDePrueba.nombreNube();
        GestorConexion g = BasesDePrueba.hibridaConNubeApagada(BasesDePrueba.urlMemoria("local"), nombreNube, 0);
        ImpOperacionesPartidaBD dao = new ImpOperacionesPartidaBD(g);
        GestorRanking rankingSinRed = new GestorRanking(new ImpOperacionesJugadorBD(g));
        GestorPartidaUno partida = new GestorPartidaUno(dao);

        iniciar(partida);
        SimuladorPartida.jugarHastaElFinal(partida, 3_000);
        int puntos = partida.finalizarRonda();
        rankingSinRed.procesarResultadosPartida(partida.verificarGanador(), puntos, this.jugadores);
        assertTrue(g.esperarSincronizacion(10_000));
        assertTrue(g.getPendientesDeSincronizar() > 0);

        BasesDePrueba.encenderNube(nombreNube);
        g.reintentarPendientes().get();

        assertEquals(0, g.getPendientesDeSincronizar());
        String id = partida.getIdPartida();
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
                "jdbc:postgresql://127.0.0.1:1/sin_red", "nadie", "nada", 0);
        sinRed.inicializarBaseDeDatos();
        ImpOperacionesPartidaBD dao = new ImpOperacionesPartidaBD(sinRed);
        GestorPartidaUno partida = new GestorPartidaUno(dao);

        iniciar(partida);
        assertDoesNotThrow(() -> SimuladorPartida.jugarHastaElFinal(partida, 3_000));
        assertDoesNotThrow(partida::finalizarRonda);
        assertEquals(1, dao.obtenerTodos().size(), "La partida quedó guardada en H2.");
    }
}
