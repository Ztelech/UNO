package co.edu.unilibre.modelo;

import co.edu.unilibre.dto.Carta;
import co.edu.unilibre.dto.ColorCarta;
import co.edu.unilibre.dto.Jugador;
import co.edu.unilibre.dto.TipoCarta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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

    private static List<Jugador> nuevosJugadores() {
        List<Jugador> lista = new ArrayList<>();
        lista.add(new Jugador(1, "Jeff", false, new ArrayList<>(), false, 0, 0, 0));
        lista.add(new Jugador(2, "Brenda", false, new ArrayList<>(), false, 0, 0, 0));
        lista.add(new Jugador(3, "Camila", false, new ArrayList<>(), false, 0, 0, 0));
        return lista;
    }

    /** Ids que el reparto determinista entrega a cada jugador. */
    private static List<List<Integer>> manosDeterministas() {
        BarajaUno baraja = new BarajaUno(SEMILLA_PRUEBA);
        baraja.generar108Cartas();
        baraja.barajar();
        List<List<Integer>> manos = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        for (int i = 0; i < 21; i++) {
            manos.get(i % 3).add(baraja.robar().getId());
        }
        return manos;
    }

    /** Juega una partida honesta con el gestor real, siempre con la primera carta legal. */
    private static GestorPartidaUno jugarPartidaHonesta(long semilla, int maximoAcciones) {
        GestorPartidaUno gestor = new GestorPartidaUno();
        gestor.configurarPartida(nuevosJugadores(), semilla);
        gestor.repartirManosIniciales();
        gestor.iniciarDescarte();

        for (int paso = 0; paso < maximoAcciones && !gestor.isPartidaTerminada(); paso++) {
            Jugador actual = gestor.getJugadorActivo();
            Carta jugable = null;
            for (Carta c : actual.getMano()) {
                boolean masCuatroIlegal = c.getTipo() == TipoCarta.COMODIN_TOMA_CUATRO
                        && actual.tieneColor(gestor.getColorActivo());
                if (!masCuatroIlegal && c.esJugableSobre(gestor.getCartaDescarte(), gestor.getColorActivo())) {
                    jugable = c;
                    break;
                }
            }
            if (jugable == null) {
                gestor.robarCartaTurno(actual);
                gestor.pasarTurno();
            } else {
                if (actual.getMano().size() == 2) {
                    gestor.cantarUno(actual);
                }
                ColorCarta elegido = jugable.getColor() == ColorCarta.NEGRO ? ColorCarta.ROJO : null;
                assertTrue(gestor.jugarTurno(actual, jugable, elegido), "Jugada legal rechazada: " + jugable);
            }
        }
        return gestor;
    }

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
    @DisplayName("Auditoría: Jugar una carta que se repartió a otro jugador es trampa")
    public void testCartaDeOtroJugador() {
        String log = CABECERA + "JUGAR;1;" + manosDeterministas().get(1).get(0);
        assertTrue(this.auditor.auditarPartidaCompleta(SEMILLA_PRUEBA, log));
    }

    @Test
    @DisplayName("Auditoría: Un robo que no es la siguiente carta del mazo es trampa")
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
    }

    @Test
    @DisplayName("Auditoría: Una partida honesta completa pasa y finalizarRonda no marca sospecha")
    public void testPartidaHonestaCompleta() {
        GestorPartidaUno gestor = jugarPartidaHonesta(SEMILLA_PRUEBA, 2_000);
        assertTrue(gestor.isPartidaTerminada(), "La simulación debería terminar con un ganador.");

        assertFalse(this.auditor.auditarPartidaCompleta(SEMILLA_PRUEBA, gestor.getLogAcciones()));
        gestor.finalizarRonda();
        assertFalse(gestor.getPartidaFinalizada().isSospechaTrampa());
    }

    @Test
    @DisplayName("Auditoría: Muchas semillas distintas, todas honestas, pasan (incluye recargas del mazo)")
    public void testVariasSemillasHonestas() {
        for (long semilla = 1; semilla <= 30; semilla++) {
            GestorPartidaUno gestor = jugarPartidaHonesta(semilla, 2_000);
            assertFalse(this.auditor.auditarPartidaCompleta(semilla, gestor.getLogAcciones()),
                    "Falsa sospecha con la semilla " + semilla);
        }
    }

    @Test
    @DisplayName("Auditoría: El mismo log honesto con otra semilla levanta sospecha")
    public void testSemillaAlterada() {
        GestorPartidaUno gestor = jugarPartidaHonesta(SEMILLA_PRUEBA, 2_000);
        assertTrue(this.auditor.auditarPartidaCompleta(SEMILLA_PRUEBA + 1, gestor.getLogAcciones()));
    }

    @Test
    @DisplayName("auditarCartaRobada compara contra la posición que fija la semilla")
    public void testAuditarCartaRobada() {
        BarajaUno baraja = new BarajaUno(SEMILLA_PRUEBA);
        baraja.generar108Cartas();
        baraja.barajar();
        int primera = baraja.robar().getId();
        int segunda = baraja.robar().getId();

        this.auditor.reconstruirBaraja(SEMILLA_PRUEBA);
        assertTrue(this.auditor.auditarCartaRobada(primera, 0));
        assertTrue(this.auditor.auditarCartaRobada(segunda, 1));
        assertFalse(this.auditor.auditarCartaRobada(segunda, 0));
        assertFalse(this.auditor.auditarCartaRobada(primera, 500));
    }

    @Test
    @DisplayName("detectarAlteracionMemoria: detecta cartas añadidas o cambiadas en la mano")
    public void testDetectarAlteracionMemoria() {
        Carta a = new Carta(1, ColorCarta.ROJO, TipoCarta.NUMERO, 1, 1);
        Carta b = new Carta(2, ColorCarta.AZUL, TipoCarta.NUMERO, 2, 2);
        Carta intrusa = new Carta(3, ColorCarta.NEGRO, TipoCarta.COMODIN_TOMA_CUATRO, -1, 50);

        assertFalse(this.auditor.detectarAlteracionMemoria(List.of(a, b), List.of(b, a)), "El orden no importa.");
        assertTrue(this.auditor.detectarAlteracionMemoria(List.of(a, b, intrusa), List.of(a, b)));
        assertTrue(this.auditor.detectarAlteracionMemoria(List.of(a, intrusa), List.of(a, b)));
    }
}
