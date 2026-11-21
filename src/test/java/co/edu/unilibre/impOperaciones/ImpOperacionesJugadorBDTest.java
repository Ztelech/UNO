package co.edu.unilibre.impOperaciones;

import co.edu.unilibre.dto.Carta;
import co.edu.unilibre.dto.ColorCarta;
import co.edu.unilibre.dto.Jugador;
import co.edu.unilibre.dto.TipoCarta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ImpOperacionesJugadorBDTest {

    private GestorConexion gestorConexion;
    private ImpOperacionesJugadorBD dao;

    @BeforeEach
    public void setUp() {
        this.gestorConexion = BasesDePrueba.hibrida();
        this.dao = new ImpOperacionesJugadorBD(this.gestorConexion);
    }

    private int filasEnNube(String sql, Object... parametros) throws Exception {
        assertTrue(this.gestorConexion.esperarSincronizacion(5_000));
        return BasesDePrueba.contar(this.gestorConexion.obtenerConexionNube(), sql, parametros);
    }

    @Test
    @DisplayName("crear: el jugador está en H2 al instante y llega a la nube")
    public void testCrear() throws Exception {
        this.dao.crear(new Jugador(1, "Jeff"));

        List<Jugador> todos = this.dao.obtenerTodos();
        assertEquals(1, todos.size());
        assertEquals("Jeff", todos.get(0).getNombre());
        assertEquals(1, filasEnNube("SELECT COUNT(*) FROM jugadores WHERE nombre = ?", "Jeff"));
    }

    @Test
    @DisplayName("actualizar: cambia puntos en H2; en Neon el perfil existe pero no se pisan sus estadísticas")
    public void testActualizar() throws Exception {
        Jugador brenda = new Jugador(2, "Brenda");
        this.dao.crear(brenda);
        brenda.setPuntosAcumulados(112);
        brenda.setPartidasJugadas(3);
        brenda.setPartidasGanadas(1);

        this.dao.actualizar(brenda);

        Jugador leido = this.dao.buscarPorId(2);
        assertEquals(112, leido.getPuntosAcumulados());
        assertEquals(3, leido.getPartidasJugadas());
        assertEquals(1, leido.getPartidasGanadas());
        // En Neon las estadísticas las suma cada partida al subir; otro computador no las sobrescribe.
        assertEquals(1, filasEnNube("SELECT COUNT(*) FROM jugadores WHERE id = 2 AND puntos_acumulados = 0"));
    }

    @Test
    @DisplayName("actualizar un jugador inexistente lanza IllegalStateException")
    public void testActualizarInexistente() {
        assertThrows(IllegalStateException.class, () -> this.dao.actualizar(new Jugador(99, "Nadie")));
    }

    @Test
    @DisplayName("crear un id repetido lanza IllegalStateException")
    public void testIdDuplicado() {
        this.dao.crear(new Jugador(1, "Jeff"));
        assertThrows(IllegalStateException.class, () -> this.dao.crear(new Jugador(1, "Otro")));
    }

    @Test
    @DisplayName("RF20: la base no permite dos perfiles con el mismo nombre")
    public void testNombreUnico() {
        this.dao.crear(new Jugador(1, "Jeff"));
        assertThrows(IllegalStateException.class, () -> this.dao.crear(new Jugador(2, "Jeff")));
    }

    @Test
    @DisplayName("RF20: obtenerSiguienteId devuelve el siguiente id libre")
    public void testSiguienteId() {
        assertEquals(1, this.dao.obtenerSiguienteId());
        this.dao.crear(new Jugador(5, "Brenda"));
        assertEquals(6, this.dao.obtenerSiguienteId());
    }

    @Test
    @DisplayName("buscarPorNombre y buscarPorId devuelven null si no existe")
    public void testBusquedas() {
        this.dao.crear(new Jugador(3, "Camila"));
        assertEquals(3, this.dao.buscarPorNombre("Camila").getId());
        assertNull(this.dao.buscarPorNombre("Nadie"));
        assertNull(this.dao.buscarPorId(42));
    }

    @Test
    @DisplayName("Persistencia en frío: la mano en juego no se guarda")
    public void testManoNoSePersiste() {
        Jugador camila = new Jugador(3, "Camila");
        camila.agregarCarta(new Carta(1, ColorCarta.ROJO, TipoCarta.NUMERO, 5, 5));
        this.dao.crear(camila);

        assertTrue(this.dao.buscarPorId(3).getMano().isEmpty());
    }

    @Test
    @DisplayName("Ranking: obtenerRankingOrdenadoPorPuntos ordena y limita a 10")
    public void testRanking() {
        for (int i = 1; i <= 12; i++) {
            Jugador j = new Jugador(i, "J" + i);
            j.setPuntosAcumulados(i * 100);
            this.dao.crear(j);
        }
        List<Jugador> top = this.dao.obtenerRankingOrdenadoPorPuntos();
        assertEquals(10, top.size());
        assertEquals(1200, top.get(0).getPuntosAcumulados());
        assertEquals(300, top.get(9).getPuntosAcumulados());
    }
}
