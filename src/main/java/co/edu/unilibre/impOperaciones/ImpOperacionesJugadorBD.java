package co.edu.unilibre.impOperaciones;

import co.edu.unilibre.dto.Jugador;
import co.edu.unilibre.operaciones.GenericDao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistencia de perfiles de jugador (RF20-RF22). Solo guarda estadísticas; la mano vive en memoria.
 * <p>
 * En Neon las estadísticas no se sobrescriben desde un computador: las suma cada partida al subir
 * (ver {@link ImpOperacionesPartidaBD}). Así varios computadores comparten el mismo ranking sin
 * pisarse. Desde aquí a Neon solo se crean los perfiles que falten.
 */
public class ImpOperacionesJugadorBD implements GenericDao<Jugador> {

    public static final int TAMANO_RANKING = 10;

    private static final String COLUMNAS = "id, nombre, puntos_acumulados, partidas_jugadas, partidas_ganadas";
    private static final String SQL_INSERTAR =
            "INSERT INTO jugadores (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?)";
    private static final String SQL_ACTUALIZAR =
            "UPDATE jugadores SET nombre = ?, puntos_acumulados = ?, partidas_jugadas = ?, "
                    + "partidas_ganadas = ? WHERE id = ?";
    private static final String SQL_TODOS = "SELECT " + COLUMNAS + " FROM jugadores ORDER BY id";
    private static final String SQL_POR_ID = "SELECT " + COLUMNAS + " FROM jugadores WHERE id = ?";
    private static final String SQL_POR_NOMBRE = "SELECT " + COLUMNAS + " FROM jugadores WHERE nombre = ?";
    private static final String SQL_MAXIMO_ID = "SELECT COALESCE(MAX(id), 0) FROM jugadores";
    private static final String SQL_RANKING = "SELECT " + COLUMNAS + " FROM jugadores "
            + "ORDER BY puntos_acumulados DESC, partidas_ganadas DESC, nombre ASC LIMIT " + TAMANO_RANKING;

    /** Nombre de la entidad en la cola de reintentos. */
    public static final String ENTIDAD = "JUGADOR";

    private final GestorConexion gestorConexion;

    public ImpOperacionesJugadorBD(GestorConexion gestorConexion) {
        this.gestorConexion = gestorConexion;
        this.gestorConexion.registrarReplicador(ENTIDAD, ImpOperacionesJugadorBD::replicarDesdeLocal);
    }

    /** Inserta en la base principal; en Neon solo crea el perfil si todavía no existe. */
    @Override
    public void crear(Jugador dato) {
        try {
            this.gestorConexion.ejecutarEscritura(con -> insertar(con, dato), con -> crearSiNoExiste(con, dato),
                    ENTIDAD, String.valueOf(dato.getId()));
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo crear el jugador " + dato.getNombre() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public List<Jugador> obtenerTodos() {
        return consultarLista(SQL_TODOS, null);
    }

    /** Actualiza las estadísticas en la base principal; en Neon solo asegura que el perfil exista. */
    @Override
    public void actualizar(Jugador dato) {
        try {
            this.gestorConexion.ejecutarEscritura(con -> {
                if (actualizarFila(con, dato) == 0) {
                    throw new SQLException("No existe el jugador con id " + dato.getId());
                }
            }, con -> crearSiNoExiste(con, dato), ENTIDAD, String.valueOf(dato.getId()));
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo actualizar el jugador " + dato.getId() + ": " + e.getMessage(), e);
        }
    }

    /** RF23: Top 10 histórico; más puntos primero y, en empate, más partidas ganadas. */
    public List<Jugador> obtenerRankingOrdenadoPorPuntos() {
        return consultarLista(SQL_RANKING, null);
    }

    /** Devuelve null si no existe. */
    public Jugador buscarPorId(int id) {
        List<Jugador> resultado = consultarLista(SQL_POR_ID, ps -> ps.setInt(1, id));
        return resultado.isEmpty() ? null : resultado.get(0);
    }

    /** RF20: selección de perfil por nombre único. Devuelve null si no existe. */
    public Jugador buscarPorNombre(String nombre) {
        List<Jugador> resultado = consultarLista(SQL_POR_NOMBRE, ps -> ps.setString(1, nombre));
        return resultado.isEmpty() ? null : resultado.get(0);
    }

    /** RF20: siguiente id libre en la base principal para un perfil nuevo. */
    public int obtenerSiguienteId() {
        try (Connection con = this.gestorConexion.obtenerConexion()) {
            return maximoId(con) + 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Error calculando el siguiente id de jugador: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ Neon (perfiles compartidos)

    /** Busca el perfil por nombre directamente en Neon (lanza SQLException si no hay conexión). */
    public Jugador buscarPorNombreEnNube(String nombre) throws SQLException {
        try (Connection nube = this.gestorConexion.obtenerConexionNube()) {
            List<Jugador> lista = consultar(nube, SQL_POR_NOMBRE, ps -> ps.setString(1, nombre));
            return lista.isEmpty() ? null : lista.get(0);
        }
    }

    /** Mayor id usado en Neon (lanza SQLException si no hay conexión). */
    public int maximoIdEnNube() throws SQLException {
        try (Connection nube = this.gestorConexion.obtenerConexionNube()) {
            return maximoId(nube);
        }
    }

    /** Top 10 global, leído de Neon (lanza SQLException si no hay conexión). */
    public List<Jugador> obtenerRankingDeNube() throws SQLException {
        try (Connection nube = this.gestorConexion.obtenerConexionNube()) {
            return consultar(nube, SQL_RANKING, null);
        }
    }

    /** Guarda en H2 un perfil traído de Neon (sin volver a replicarlo). */
    public void guardarSoloLocal(Jugador j) {
        try (Connection local = this.gestorConexion.obtenerConexionLocal()) {
            guardarCompleto(local, j);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo guardar el perfil de " + j.getNombre() + ": " + e.getMessage(), e);
        }
    }

    /** Crea en Neon el jugador de H2 con ese id si no existe (con estadísticas en cero: las suman sus partidas). */
    static void asegurarEnNube(Connection local, Connection nube, int idJugador) throws SQLException {
        if (!consultar(nube, SQL_POR_ID, ps -> ps.setInt(1, idJugador)).isEmpty()) {
            return;
        }
        List<Jugador> deLocal = consultar(local, SQL_POR_ID, ps -> ps.setInt(1, idJugador));
        String nombre = deLocal.isEmpty() ? "Jugador " + idJugador : deLocal.get(0).getNombre();
        insertar(nube, new Jugador(idJugador, nombre));
    }

    static List<Jugador> leerTodos(Connection con) throws SQLException {
        return consultar(con, SQL_TODOS, null);
    }

    /** Inserta o reemplaza el perfil completo (nombre y estadísticas) en esa base. */
    static void guardarCompleto(Connection con, Jugador j) throws SQLException {
        if (actualizarFila(con, j) == 0) {
            insertar(con, j);
        }
    }

    // ------------------------------------------------------------------ JDBC

    interface Parametros {
        void asignar(PreparedStatement ps) throws SQLException;
    }

    private List<Jugador> consultarLista(String sql, Parametros parametros) {
        try (Connection con = this.gestorConexion.obtenerConexion()) {
            return consultar(con, sql, parametros);
        } catch (SQLException e) {
            throw new IllegalStateException("Error consultando jugadores: " + e.getMessage(), e);
        }
    }

    private static List<Jugador> consultar(Connection con, String sql, Parametros parametros) throws SQLException {
        List<Jugador> lista = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            if (parametros != null) {
                parametros.asignar(ps);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        }
        return lista;
    }

    private static int maximoId(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(SQL_MAXIMO_ID);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static Jugador mapear(ResultSet rs) throws SQLException {
        return new Jugador(
                rs.getInt("id"),
                rs.getString("nombre"),
                new ArrayList<>(),
                false,
                rs.getInt("puntos_acumulados"),
                rs.getInt("partidas_jugadas"),
                rs.getInt("partidas_ganadas"));
    }

    private static void insertar(Connection con, Jugador j) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(SQL_INSERTAR)) {
            ps.setInt(1, j.getId());
            ps.setString(2, j.getNombre());
            ps.setInt(3, j.getPuntosAcumulados());
            ps.setInt(4, j.getPartidasJugadas());
            ps.setInt(5, j.getPartidasGanadas());
            ps.executeUpdate();
        }
    }

    private static int actualizarFila(Connection con, Jugador j) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(SQL_ACTUALIZAR)) {
            ps.setString(1, j.getNombre());
            ps.setInt(2, j.getPuntosAcumulados());
            ps.setInt(3, j.getPartidasJugadas());
            ps.setInt(4, j.getPartidasGanadas());
            ps.setInt(5, j.getId());
            return ps.executeUpdate();
        }
    }

    /** Réplica en Neon: crea el perfil con estadísticas en cero si falta; nunca pisa las existentes. */
    private static void crearSiNoExiste(Connection nube, Jugador j) throws SQLException {
        if (consultar(nube, SQL_POR_ID, ps -> ps.setInt(1, j.getId())).isEmpty()) {
            insertar(nube, new Jugador(j.getId(), j.getNombre()));
        }
    }

    /** Reintento: crea en Neon el perfil de H2 si todavía no está. */
    private static void replicarDesdeLocal(Connection local, Connection nube, String clave) throws SQLException {
        asegurarEnNube(local, nube, Integer.parseInt(clave));
    }
}
