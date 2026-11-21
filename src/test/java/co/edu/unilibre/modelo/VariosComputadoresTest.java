package co.edu.unilibre.modelo;

import co.edu.unilibre.dto.Jugador;
import co.edu.unilibre.dto.Partida;
import co.edu.unilibre.impOperaciones.BasesDePrueba;
import co.edu.unilibre.impOperaciones.GestorConexion;
import co.edu.unilibre.impOperaciones.ImpOperacionesJugadorBD;
import co.edu.unilibre.impOperaciones.ImpOperacionesPartidaBD;
import co.edu.unilibre.impOperaciones.ModoPersistencia;
import co.edu.unilibre.impOperaciones.SincronizadorNube;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dos computadores con su propio H2 y una sola base en la "nube" (H2 en memoria que simula Neon).
 * Reproduce el caso real: en el segundo computador se jugó sin config.local.properties.
 */
public class VariosComputadoresTest {

    private String urlNube;

    @BeforeEach
    public void setUp() throws Exception {
        String nombre = BasesDePrueba.nombreNube();
        BasesDePrueba.encenderNube(nombre);
        this.urlNube = BasesDePrueba.urlNubeApagable(nombre);
    }

    /** Un computador: su H2, sus DAOs y su ranking. */
    private record Computador(GestorConexion conexion, ImpOperacionesJugadorBD jugadores,
                              ImpOperacionesPartidaBD partidas, GestorRanking ranking) {
    }

    private static Computador computador(String urlLocal, String urlNube) {
        GestorConexion c = new GestorConexion(ModoPersistencia.HIBRIDO_ASINCRONO,
                urlLocal, "sa", "", urlNube, "sa", "", 0);
        c.inicializarBaseDeDatos();
        c.esperarSincronizacion(5_000);
        ImpOperacionesJugadorBD dj = new ImpOperacionesJugadorBD(c);
        return new Computador(c, dj, new ImpOperacionesPartidaBD(c), new GestorRanking(dj, c::isNubeDisponible));
    }

    /** Juega una partida honesta completa con esos perfiles y actualiza el ranking, como la interfaz. */
    private static Partida jugar(Computador pc, List<Jugador> jugadores, long semilla) {
        GestorPartidaUno g = new GestorPartidaUno(pc.partidas());
        g.configurarPartida(new ArrayList<>(jugadores), semilla);
        g.repartirManosIniciales();
        g.iniciarDescarte();
        SimuladorPartida.jugarHastaElFinal(g, 3_000);
        assertTrue(g.isPartidaTerminada());
        int puntos = g.finalizarRonda();
        pc.ranking().procesarResultadosPartida(g.verificarGanador(), puntos, jugadores);
        assertTrue(pc.conexion().esperarSincronizacion(10_000));
        return g.getPartidaFinalizada();
    }

    private static Map<String, int[]> jugadoresDe(Connection con) throws Exception {
        Map<String, int[]> mapa = new HashMap<>();
        try (Connection c = con;
             PreparedStatement ps = c.prepareStatement(
                     "SELECT nombre, id, partidas_jugadas, partidas_ganadas, puntos_acumulados FROM jugadores");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                mapa.put(rs.getString(1), new int[]{rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)});
            }
        }
        return mapa;
    }

    @Test
    @DisplayName("Lo jugado en un computador sin Neon se sube al configurarlo, con los perfiles unidos por nombre")
    public void testComputadorSinConfiguracionSeSincronizaDespues() throws Exception {
        // Computador A (con Neon): Jeff=1, Brenda=2.
        Computador a = computador(BasesDePrueba.urlMemoria("pcA"), this.urlNube);
        assertTrue(new SincronizadorNube(a.conexion()).sincronizar().conectado());
        Jugador jeffA = a.ranking().registrarOSeleccionarPerfil("Jeff");
        Jugador brendaA = a.ranking().registrarOSeleccionarPerfil("Brenda");
        Partida partidaA = jugar(a, List.of(jeffA, brendaA), 11L);

        // Computador B SIN config.local.properties: Brenda=1, Ana=2 (ids locales que chocan con Neon).
        String localB = BasesDePrueba.urlMemoria("pcB");
        Computador bSinNube = computador(localB, null);
        Jugador brendaB = bSinNube.ranking().registrarOSeleccionarPerfil("Brenda");
        Jugador anaB = bSinNube.ranking().registrarOSeleccionarPerfil("Ana");
        assertEquals(1, brendaB.getId());
        Partida partidaB = jugar(bSinNube, List.of(brendaB, anaB), 22L);
        assertEquals(1, BasesDePrueba.contar(a.conexion().obtenerConexionNube(), "SELECT COUNT(*) FROM partidas"),
                "Sin configuración, B no subió nada (el caso del otro computador).");

        // Se agrega la configuración en B y se vuelve a abrir el juego.
        Computador b = computador(localB, this.urlNube);
        SincronizadorNube.Resultado r = new SincronizadorNube(b.conexion()).sincronizar();
        assertTrue(r.conectado());
        assertEquals(2, r.perfilesUnificados(), "Brenda pasa al id de Neon y Ana a un id libre.");
        assertEquals(1, r.partidasSubidas());

        // Neon: las dos partidas y un solo perfil por nombre.
        assertEquals(2, BasesDePrueba.contar(b.conexion().obtenerConexionNube(), "SELECT COUNT(*) FROM partidas"));
        Map<String, int[]> nube = jugadoresDe(b.conexion().obtenerConexionNube());
        assertEquals(3, nube.size());
        assertEquals(brendaA.getId(), nube.get("Brenda")[0], "Brenda conserva su id de Neon.");
        assertEquals(2, nube.get("Brenda")[1], "Brenda jugó una partida en cada computador.");
        int ganadasEsperadas = (partidaA.getIdGanador() == brendaA.getId() ? 1 : 0)
                + (partidaB.getIdGanador() == brendaB.getId() ? 1 : 0);
        assertEquals(ganadasEsperadas, nube.get("Brenda")[2]);

        // B quedó con los mismos ids y estadísticas que Neon.
        assertEquals(nube.keySet(), jugadoresDe(b.conexion().obtenerConexionLocal()).keySet());
        Map<String, int[]> localSincronizado = jugadoresDe(b.conexion().obtenerConexionLocal());
        for (String nombre : nube.keySet()) {
            assertArrayEquals(nube.get(nombre), localSincronizado.get(nombre), "Perfil " + nombre);
        }

        // La partida de B quedó con los ids nuevos y sigue pasando la auditoría.
        Partida subida = b.partidas().obtenerTodos().stream()
                .filter(p -> p.getIdPartida().equals(partidaB.getIdPartida())).findFirst().orElseThrow();
        int idAnaNube = nube.get("Ana")[0];
        assertTrue(subida.getLogJugadas().startsWith("JUGADORES;0;" + brendaA.getId() + "," + idAnaNube));
        assertFalse(new AuditorSemilla().auditarPartidaCompleta(subida.getSemilla(), subida.getLogJugadas()));
    }

    @Test
    @DisplayName("Sincronizar dos veces no suma dos veces la misma partida")
    public void testSincronizarEsIdempotente() throws Exception {
        Computador a = computador(BasesDePrueba.urlMemoria("pcA"), this.urlNube);
        new SincronizadorNube(a.conexion()).sincronizar();
        Jugador jeff = a.ranking().registrarOSeleccionarPerfil("Jeff");
        Jugador brenda = a.ranking().registrarOSeleccionarPerfil("Brenda");
        jugar(a, List.of(jeff, brenda), 33L);
        Map<String, int[]> antes = jugadoresDe(a.conexion().obtenerConexionNube());

        new SincronizadorNube(a.conexion()).sincronizar();
        new SincronizadorNube(a.conexion()).sincronizar();

        Map<String, int[]> despues = jugadoresDe(a.conexion().obtenerConexionNube());
        for (String nombre : antes.keySet()) {
            assertArrayEquals(antes.get(nombre), despues.get(nombre), "Perfil " + nombre);
        }
        assertEquals(1, antes.get("Jeff")[1]);
    }

    @Test
    @DisplayName("Con Neon disponible, un perfil creado en otro computador se usa con el mismo id")
    public void testPerfilDeOtroComputador() throws Exception {
        Computador a = computador(BasesDePrueba.urlMemoria("pcA"), this.urlNube);
        new SincronizadorNube(a.conexion()).sincronizar();
        Jugador jeffA = a.ranking().registrarOSeleccionarPerfil("Jeff");
        a.ranking().registrarOSeleccionarPerfil("Brenda");
        assertTrue(a.conexion().esperarSincronizacion(5_000));

        Computador c = computador(BasesDePrueba.urlMemoria("pcC"), this.urlNube);
        c.conexion().marcarNubeDisponible(true);
        Jugador jeffC = c.ranking().registrarOSeleccionarPerfil("Jeff");
        Jugador zoe = c.ranking().registrarOSeleccionarPerfil("Zoe");

        assertEquals(jeffA.getId(), jeffC.getId());
        assertEquals(3, zoe.getId(), "Un perfil nuevo toma el siguiente id libre de Neon.");
    }

    @Test
    @DisplayName("El ranking del menú es el de Neon (todos los computadores) cuando hay conexión")
    public void testRankingGlobal() throws Exception {
        Computador a = computador(BasesDePrueba.urlMemoria("pcA"), this.urlNube);
        new SincronizadorNube(a.conexion()).sincronizar();
        Jugador jeff = a.ranking().registrarOSeleccionarPerfil("Jeff");
        Jugador brenda = a.ranking().registrarOSeleccionarPerfil("Brenda");
        jugar(a, List.of(jeff, brenda), 44L);

        Computador c = computador(BasesDePrueba.urlMemoria("pcC"), this.urlNube);
        c.conexion().marcarNubeDisponible(true);
        List<Jugador> top = c.ranking().generarTablaPosicionesTop10();
        assertEquals(2, top.size(), "C no jugó, pero ve el ranking de A.");
    }

    @Test
    @DisplayName("renumerarRegistro cambia los ids de jugador sin tocar las cartas")
    public void testRenumerarRegistro() throws Exception {
        java.lang.reflect.Method m = SincronizadorNube.class.getDeclaredMethod("renumerarRegistro", String.class, Map.class);
        m.setAccessible(true);
        String log = "JUGADORES;0;1,2\nREPARTO;0;7\nJUGAR;1;45\nROBAR;2;12\nDENUNCIA;1;2";
        String nuevo = (String) m.invoke(null, log, Map.of(1, 2, 2, 5));
        assertEquals("JUGADORES;0;2,5\nREPARTO;0;7\nJUGAR;2;45\nROBAR;5;12\nDENUNCIA;2;5", nuevo);
    }
}
