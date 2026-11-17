package co.edu.unilibre.modelo;

import co.edu.unilibre.dto.Jugador;
import co.edu.unilibre.impOperaciones.BasesDePrueba;
import co.edu.unilibre.impOperaciones.ImpOperacionesJugadorBD;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GestorRankingTest {

    private ImpOperacionesJugadorBD dao;
    private GestorRanking ranking;

    @BeforeEach
    public void setUp() {
        this.dao = new ImpOperacionesJugadorBD(BasesDePrueba.soloLocal());
        this.ranking = new GestorRanking(this.dao);
    }

    private Jugador registrar(int id, String nombre, int puntos, int jugadas, int ganadas) {
        Jugador j = new Jugador(id, nombre, new ArrayList<>(), false, puntos, jugadas, ganadas);
        this.dao.crear(j);
        return j;
    }

    @Test
    @DisplayName("RF20: registrarOSeleccionarPerfil crea un perfil nuevo o devuelve el existente")
    public void testRegistrarOSeleccionarPerfil() {
        Jugador jeff = this.ranking.registrarOSeleccionarPerfil("Jeff");
        Jugador brenda = this.ranking.registrarOSeleccionarPerfil("  Brenda ");
        Jugador otraVezJeff = this.ranking.registrarOSeleccionarPerfil("Jeff");

        assertEquals("Brenda", brenda.getNombre(), "Se quitan los espacios sobrantes.");
        assertNotEquals(jeff.getId(), brenda.getId());
        assertEquals(jeff.getId(), otraVezJeff.getId(), "El mismo nombre selecciona el mismo perfil.");
        assertEquals(2, this.dao.obtenerTodos().size());
    }

    @Test
    @DisplayName("RF20: un nombre vacío o demasiado largo no crea perfil")
    public void testNombreInvalido() {
        assertThrows(IllegalArgumentException.class, () -> this.ranking.registrarOSeleccionarPerfil("   "));
        assertThrows(IllegalArgumentException.class, () -> this.ranking.registrarOSeleccionarPerfil(null));
        assertThrows(IllegalArgumentException.class, () -> this.ranking.registrarOSeleccionarPerfil("x".repeat(101)));
        assertTrue(this.dao.obtenerTodos().isEmpty());
    }

    @Test
    @DisplayName("RF21 & RF22: todos suman una partida jugada y el ganador suma victoria y puntos")
    public void testProcesarResultadosPartida() {
        Jugador jeff = registrar(1, "Jeff", 100, 5, 2);
        Jugador brenda = registrar(2, "Brenda", 80, 5, 3);

        this.ranking.procesarResultadosPartida(jeff, 76, List.of(jeff, brenda));

        Jugador jeffBD = this.dao.buscarPorId(1);
        assertEquals(176, jeffBD.getPuntosAcumulados());
        assertEquals(6, jeffBD.getPartidasJugadas());
        assertEquals(3, jeffBD.getPartidasGanadas());
        Jugador brendaBD = this.dao.buscarPorId(2);
        assertEquals(80, brendaBD.getPuntosAcumulados());
        assertEquals(6, brendaBD.getPartidasJugadas());
        assertEquals(3, brendaBD.getPartidasGanadas());
    }

    @Test
    @DisplayName("Fin de partida: un jugador nuevo se registra en la base")
    public void testJugadorNuevoSeRegistra() {
        Jugador camila = new Jugador(3, "Camila");
        this.ranking.procesarResultadosPartida(camila, 40, List.of(camila));

        Jugador leido = this.dao.buscarPorId(3);
        assertNotNull(leido);
        assertEquals(40, leido.getPuntosAcumulados());
        assertEquals(1, leido.getPartidasGanadas());
    }

    @Test
    @DisplayName("Fin de partida: parte de los valores guardados, no de los que traiga el objeto en memoria")
    public void testParteDeLaBase() {
        registrar(1, "Jeff", 500, 10, 4);
        Jugador enMemoria = new Jugador(1, "Jeff"); // estadísticas en 0
        this.ranking.procesarResultadosPartida(enMemoria, 10, List.of(enMemoria));

        assertEquals(510, this.dao.buscarPorId(1).getPuntosAcumulados());
        assertEquals(11, this.dao.buscarPorId(1).getPartidasJugadas());
    }

    @Test
    @DisplayName("RF23: Top 10 ordenado por puntos, máximo 10 y desempate por partidas ganadas")
    public void testTablaTop10() {
        for (int i = 1; i <= 12; i++) {
            registrar(i, "J" + i, i * 10, 1, 0);
        }
        registrar(20, "Mateo", 500, 10, 2);
        registrar(21, "Sofia", 500, 10, 5);

        List<Jugador> top = this.ranking.generarTablaPosicionesTop10();

        assertEquals(10, top.size());
        assertEquals("Sofia", top.get(0).getNombre());
        assertEquals("Mateo", top.get(1).getNombre());
        assertEquals(120, top.get(2).getPuntosAcumulados());
    }

    @Test
    @DisplayName("Estadísticas: texto con puntos, partidas y porcentaje de victorias")
    public void testEstadisticas() {
        registrar(1, "Jeff", 3120, 40, 10);
        assertEquals("Jeff: 3120 puntos, 40 partidas jugadas, 10 ganadas (25.0 %)",
                this.ranking.obtenerEstadisticasJugador(1));
        assertEquals("Jugador no encontrado", this.ranking.obtenerEstadisticasJugador(99));
    }
}
