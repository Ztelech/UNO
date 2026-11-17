package co.edu.unilibre.modelo;

import co.edu.unilibre.dto.Carta;
import co.edu.unilibre.dto.ColorCarta;
import co.edu.unilibre.dto.Jugador;
import co.edu.unilibre.dto.TipoCarta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AuditorSemillaTest {

    private static final long SEMILLA_PRUEBA = 987654321L;
    private static final String CABECERA = "JUGADORES;0;1,2,3\nREPARTO;0;7\n";

    private AuditorSemilla auditor;

    @BeforeEach
    public void setUp() {
        this.auditor = new AuditorSemilla();
    }

    /** Ids que el reparto determinista entrega a cada jugador. */
    private static List<List<Integer>> manosDeterministas() {
        List<Carta> orden = SimuladorPartida.barajaBarajada(SEMILLA_PRUEBA).obtenerCartasRestantes();
        List<List<Integer>> manos = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        for (int i = 0; i < 21; i++) {
            manos.get(i % 3).add(orden.get(i).getId());
        }
        return manos;
    }

    /** Partida repartida cuya carta inicial es un número (le toca a Jeff, sin efectos). */
    private static GestorPartidaUno partidaEnCurso(List<Jugador> jugadores) {
        GestorPartidaUno g = new GestorPartidaUno();
        g.configurarPartida(jugadores, SimuladorPartida.semillaConCartaInicial(TipoCarta.NUMERO));
        g.repartirManosIniciales();
        g.iniciarDescarte();
        return g;
    }

    /** Simula una alteración externa de la memoria: reordena el mazo oculto del gestor. */
    @SuppressWarnings("unchecked")
    private static void alterarMazoOculto(GestorPartidaUno g) throws Exception {
        Field campoBaraja = GestorPartidaUno.class.getDeclaredField("barajaRobo");
        campoBaraja.setAccessible(true);
        BarajaUno baraja = (BarajaUno) campoBaraja.get(g);
        Field campoCartas = BarajaUno.class.getDeclaredField("cartas");
        campoCartas.setAccessible(true);
        List<Carta> cartas = (List<Carta>) campoCartas.get(baraja);
        Collections.swap(cartas, 0, cartas.size() - 1);
    }

    // ------------------------------------------------------------------ auditoría del registro

    @Test
    @DisplayName("Auditoría: Un log vacío no levanta sospecha")
    public void testLogVacio() {
        assertFalse(this.auditor.auditarPartidaCompleta(SEMILLA_PRUEBA, ""));
    }

    @Test
    @DisplayName("Auditoría: Jugar cartas del reparto propio es limpio")
    public void testJugadaDelRepartoPropio() {
        List<List<Integer>> manos = manosDeterministas();
        String log = CABECERA
                + "JUGAR;1;" + manos.get(0).get(0) + "\n"
                + "JUGAR;2;" + manos.get(1).get(3) + "\n"
                + "JUGAR;3;" + manos.get(2).get(6);
        assertFalse(this.auditor.auditarPartidaCompleta(SEMILLA_PRUEBA, log));
    }

    @Test
    @DisplayName("RF26: Jugar una carta que se repartió a otro jugador es trampa")
    public void testCartaDeOtroJugador() {
        String log = CABECERA + "JUGAR;1;" + manosDeterministas().get(1).get(0);
        assertTrue(this.auditor.auditarPartidaCompleta(SEMILLA_PRUEBA, log));
    }

    @Test
    @DisplayName("RF25: Un robo que no es la siguiente carta del mazo es trampa")
    public void testRoboFalso() {
        String log = CABECERA + "ROBAR;1;" + manosDeterministas().get(2).get(0);
        assertTrue(this.auditor.auditarPartidaCompleta(SEMILLA_PRUEBA, log));
    }

    @Test
    @DisplayName("Auditoría: Jugar dos veces la misma carta es trampa")
    public void testCartaDuplicada() {
        int carta = manosDeterministas().get(0).get(0);
        String log = CABECERA + "JUGAR;1;" + carta + "\nJUGAR;1;" + carta;
        assertTrue(this.auditor.auditarPartidaCompleta(SEMILLA_PRUEBA, log));
    }

    @Test
    @DisplayName("Auditoría: Un registro sin cabecera o con líneas corruptas es sospechoso")
    public void testLogMalformado() {
        assertTrue(this.auditor.auditarPartidaCompleta(SEMILLA_PRUEBA, "JUGAR;1;5"));
        assertTrue(this.auditor.auditarPartidaCompleta(SEMILLA_PRUEBA, CABECERA + "JUGAR;uno;5"));
        assertTrue(this.auditor.auditarPartidaCompleta(SEMILLA_PRUEBA, CABECERA + "VOLAR;1;5"));
    }

    @Test
    @DisplayName("Auditoría: Muchas partidas honestas completas pasan (incluye recargas y cartas iniciales especiales)")
    public void testVariasSemillasHonestas() {
        for (long semilla = 1; semilla <= 40; semilla++) {
            GestorPartidaUno g = SimuladorPartida.partidaHonesta(semilla, SimuladorPartida.tresJugadores());
            assertFalse(this.auditor.auditarPartidaCompleta(semilla, g.getLogAcciones()),
                    "Falsa sospecha con la semilla " + semilla);
            assertTrue(g.getAlertasTrampa().isEmpty(), "Alertas falsas con la semilla " + semilla + ": " + g.getAlertasTrampa());
        }
    }

    @Test
    @DisplayName("Auditoría: Una partida honesta cerrada no queda marcada como sospechosa")
    public void testPartidaHonestaCerrada() {
        GestorPartidaUno g = SimuladorPartida.partidaHonesta(SEMILLA_PRUEBA, SimuladorPartida.tresJugadores());
        assertTrue(g.isPartidaTerminada());
        g.finalizarRonda();
        assertFalse(g.getPartidaFinalizada().isSospechaTrampa());
    }

    @Test
    @DisplayName("Auditoría: El mismo log honesto con otra semilla levanta sospecha")
    public void testSemillaAlterada() {
        GestorPartidaUno g = SimuladorPartida.partidaHonesta(SEMILLA_PRUEBA, SimuladorPartida.tresJugadores());
        assertTrue(this.auditor.auditarPartidaCompleta(SEMILLA_PRUEBA + 1, g.getLogAcciones()));
    }

    // ------------------------------------------------------------------ métodos del diagrama

    @Test
    @DisplayName("RF25: auditarCartaRobada compara contra el mazo reconstruido con la semilla")
    public void testAuditarCartaRobada() {
        List<Carta> orden = SimuladorPartida.barajaBarajada(SEMILLA_PRUEBA).obtenerCartasRestantes();
        this.auditor.iniciarMonitoreo(SEMILLA_PRUEBA, List.of(1, 2, 3));

        assertTrue(this.auditor.auditarCartaRobada(orden.get(0).getId(), 0));
        assertTrue(this.auditor.auditarCartaRobada(orden.get(1).getId(), 1));
        assertFalse(this.auditor.auditarCartaRobada(orden.get(1).getId(), 0));
        assertFalse(this.auditor.auditarCartaRobada(orden.get(0).getId(), 500));
    }

    @Test
    @DisplayName("RF26: validarCartaPertenenciaSemilla solo acepta cartas que la semilla le dio al jugador")
    public void testValidarCartaPertenencia() {
        List<List<Integer>> manos = manosDeterministas();
        this.auditor.iniciarMonitoreo(SEMILLA_PRUEBA, List.of(1, 2, 3));
        assertTrue(this.auditor.registrarAccion("REPARTO;0;7"));

        Carta deJeff = new Carta(manos.get(0).get(0), ColorCarta.ROJO, TipoCarta.NUMERO, 1, 1);
        Carta deBrenda = new Carta(manos.get(1).get(0), ColorCarta.ROJO, TipoCarta.NUMERO, 1, 1);
        assertTrue(this.auditor.validarCartaPertenenciaSemilla(1, deJeff, SEMILLA_PRUEBA));
        assertFalse(this.auditor.validarCartaPertenenciaSemilla(1, deBrenda, SEMILLA_PRUEBA));
        assertFalse(this.auditor.validarCartaPertenenciaSemilla(1, deJeff, SEMILLA_PRUEBA + 1), "Otra semilla.");
    }

    @Test
    @DisplayName("RF27: verificarIntegridadMazoRestante compara el mazo real con el recreado")
    public void testVerificarIntegridadMazo() {
        this.auditor.iniciarMonitoreo(SEMILLA_PRUEBA, List.of(1, 2, 3));
        assertTrue(this.auditor.registrarAccion("REPARTO;0;7"));

        BarajaUno real = SimuladorPartida.barajaBarajada(SEMILLA_PRUEBA);
        for (int i = 0; i < 21; i++) {
            real.robar();
        }
        assertTrue(this.auditor.verificarIntegridadMazoRestante(SEMILLA_PRUEBA, real.obtenerCartasRestantes(), 21));
        assertFalse(this.auditor.verificarIntegridadMazoRestante(SEMILLA_PRUEBA, real.obtenerCartasRestantes(), 20));

        List<Carta> alterado = new ArrayList<>(real.obtenerCartasRestantes());
        Collections.swap(alterado, 0, 1);
        assertFalse(this.auditor.verificarIntegridadMazoRestante(SEMILLA_PRUEBA, alterado, 21));
    }

    @Test
    @DisplayName("RF24: detectarAlteracionMemoria detecta cartas añadidas o cambiadas en la mano")
    public void testDetectarAlteracionMemoria() {
        Carta a = new Carta(1, ColorCarta.ROJO, TipoCarta.NUMERO, 1, 1);
        Carta b = new Carta(2, ColorCarta.AZUL, TipoCarta.NUMERO, 2, 2);
        Carta intrusa = new Carta(3, ColorCarta.NEGRO, TipoCarta.COMODIN_TOMA_CUATRO, -1, 50);

        assertFalse(this.auditor.detectarAlteracionMemoria(List.of(a, b), List.of(b, a)), "El orden no importa.");
        assertTrue(this.auditor.detectarAlteracionMemoria(List.of(a, b, intrusa), List.of(a, b)));
        assertTrue(this.auditor.detectarAlteracionMemoria(List.of(a, intrusa), List.of(a, b)));
    }

    // ------------------------------------------------------------------ en tiempo real con el gestor

    @Test
    @DisplayName("RF26: Lanzar una carta que la semilla nunca repartió se rechaza con alerta de trampa")
    public void testRechazoConAlertaEnTiempoReal() {
        List<Jugador> jugadores = SimuladorPartida.tresJugadores();
        GestorPartidaUno g = partidaEnCurso(jugadores);
        List<String> recibidas = new ArrayList<>();
        g.setOyenteAlertas(recibidas::add);

        Jugador jeff = jugadores.get(0);
        Carta inyectada = new Carta(900, ColorCarta.NEGRO, TipoCarta.COMODIN_COLOR, -1, 50);
        jeff.agregarCarta(inyectada); // alguien escribe una carta en la memoria del juego

        assertFalse(g.jugarTurno(jeff, inyectada, ColorCarta.ROJO));
        assertTrue(recibidas.stream().anyMatch(m -> m.startsWith("RF26")), "Se esperaba alerta RF26: " + recibidas);
        assertTrue(g.verificarIntegridad().stream().anyMatch(m -> m.startsWith("RF24")), "La mano alterada se detecta (RF24).");
        assertTrue(jeff.getMano().contains(inyectada), "La jugada fue rechazada.");
    }

    @Test
    @DisplayName("RF25 & RF27: Si se altera el mazo oculto, se detecta y se avisa al robar")
    public void testMazoAlteradoEnTiempoReal() throws Exception {
        List<Jugador> jugadores = SimuladorPartida.tresJugadores();
        GestorPartidaUno g = partidaEnCurso(jugadores);
        assertTrue(g.verificarIntegridad().isEmpty(), "Recién repartida, todo cuadra.");

        alterarMazoOculto(g);
        assertTrue(g.verificarIntegridad().stream().anyMatch(m -> m.startsWith("RF27")));

        g.robarCartaUno(jugadores.get(0));
        assertTrue(g.getAlertasTrampa().stream().anyMatch(m -> m.startsWith("RF25")), "Alertas: " + g.getAlertasTrampa());
    }

    @Test
    @DisplayName("RF24-RF27: Con alertas en tiempo real la partida cerrada queda marcada como sospechosa")
    public void testAlertaMarcaLaPartida() {
        List<Jugador> jugadores = SimuladorPartida.tresJugadores();
        GestorPartidaUno g = partidaEnCurso(jugadores);
        Jugador jeff = jugadores.get(0);
        jeff.agregarCarta(new Carta(900, ColorCarta.NEGRO, TipoCarta.COMODIN_COLOR, -1, 50));
        g.verificarIntegridad();
        jeff.getMano().removeIf(c -> c.getId() == 900);

        SimuladorPartida.jugarHastaElFinal(g, 3_000);
        assertTrue(g.isPartidaTerminada());
        g.finalizarRonda();
        assertTrue(g.getPartidaFinalizada().isSospechaTrampa());
    }
}
