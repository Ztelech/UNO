package co.edu.unilibre.impOperaciones;

import co.edu.unilibre.dto.Partida;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ImpOperacionesPartidaBDTest {

    private GestorConexion gestorConexion;
    private ImpOperacionesPartidaBD dao;

    @BeforeEach
    public void setUp() {
        this.gestorConexion = BasesDePrueba.hibrida();
        this.dao = new ImpOperacionesPartidaBD(this.gestorConexion);
    }

    @Test
    @DisplayName("crear guarda todos los campos de la partida, incluida una semilla grande, en H2 y en la nube")
    public void testCrearYObtenerTodos() throws Exception {
        String log = "JUGADORES;0;1,2\nREPARTO;0;7\nJUGAR;1;17";
        this.dao.crear(new Partida("P-0042", 9_000_000_000L, LocalDate.of(2026, 11, 4), 1, 112, false, log));

        List<Partida> todas = this.dao.obtenerTodos();
        assertEquals(1, todas.size());
        Partida leida = todas.get(0);
        assertEquals("P-0042", leida.getIdPartida());
        assertEquals(9_000_000_000L, leida.getSemilla());
        assertEquals(LocalDate.of(2026, 11, 4), leida.getFecha());
        assertEquals(1, leida.getIdGanador());
        assertEquals(112, leida.getPuntosGanador());
        assertFalse(leida.isSospechaTrampa());
        assertEquals(log, leida.getLogJugadas());

        assertTrue(this.gestorConexion.esperarSincronizacion(5_000));
        assertEquals(1, BasesDePrueba.contar(this.gestorConexion.obtenerConexionNube(),
                "SELECT COUNT(*) FROM partidas WHERE id_partida = ? AND semilla = ?", "P-0042", 9_000_000_000L));
    }

    @Test
    @DisplayName("actualizar permite marcar la sospecha de trampa tras la auditoría")
    public void testActualizarSospecha() throws Exception {
        Partida p = new Partida("P-0041", 555012345L, LocalDate.of(2026, 11, 3), 3, 40, false, "JUGADORES;0;1,3");
        this.dao.crear(p);
        p.setSospechaTrampa(true);
        this.dao.actualizar(p);

        assertTrue(this.dao.obtenerTodos().get(0).isSospechaTrampa());
        assertTrue(this.gestorConexion.esperarSincronizacion(5_000));
        assertEquals(1, BasesDePrueba.contar(this.gestorConexion.obtenerConexionNube(),
                "SELECT COUNT(*) FROM partidas WHERE sospecha_trampa = TRUE"));
    }

    @Test
    @DisplayName("registrarJugadaInstantanea: queda en H2 al instante, en orden de turno, y se replica")
    public void testJugadasEnTiempoReal() throws Exception {
        this.dao.registrarJugadaInstantanea("P-0001", 1, 0, "JUGADORES;0;1,2");
        this.dao.registrarJugadaInstantanea("P-0001", 2, 1, "JUGAR;1;17");
        this.dao.registrarJugadaInstantanea("P-0001", 3, 2, "ROBAR;2;45");
        this.dao.registrarJugadaInstantanea("P-0002", 1, 0, "JUGADORES;0;3,4");

        assertEquals(List.of("JUGADORES;0;1,2", "JUGAR;1;17", "ROBAR;2;45"), this.dao.obtenerJugadasDePartida("P-0001"));

        assertTrue(this.gestorConexion.esperarSincronizacion(5_000));
        assertEquals(4, BasesDePrueba.contar(this.gestorConexion.obtenerConexionNube(),
                "SELECT COUNT(*) FROM jugadas_tiempo_real"));
    }

    @Test
    @DisplayName("Sin partidas guardadas obtenerTodos devuelve una lista vacía")
    public void testSinPartidas() {
        assertTrue(this.dao.obtenerTodos().isEmpty());
    }
}
