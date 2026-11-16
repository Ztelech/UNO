package co.edu.unilibre.impOperaciones;

import co.edu.unilibre.dto.Jugador;
import co.edu.unilibre.operaciones.GenericDao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Persistencia de jugadores. Solo guarda estadísticas; la mano vive en memoria. */
public class ImpOperacionesJugadorBD implements GenericDao<Jugador> {

    public static final int TAMANO_RANKING = 10;

    private static final String COLUMNAS = "id, nombre, es_bot, puntos_acumulados, partidas_jugadas, partidas_ganadas";
    private static final String SQL_INSERTAR =
            "INSERT INTO jugadores (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?, ?)";
    private static final String SQL_ACTUALIZAR =
            "UPDATE jugadores SET nombre = ?, es_bot = ?, puntos_acumulados = ?, partidas_jugadas = ?, "
                    + "partidas_ganadas = ? WHERE id = ?";
    private static final String SQL_TODOS = "SELECT " + COLUMNAS + " FROM jugadores ORDER BY id";
    private static final String SQL_POR_ID = "SELECT " + COLUMNAS + " FROM jugadores WHERE id = ?";
    private static final String SQL_POR_NOMBRE = "SELECT " + COLUMNAS + " FROM jugadores WHERE nombre = ?";
    private static final String SQL_RANKING = "SELECT " + COLUMNAS + " FROM jugadores "
            + "ORDER BY puntos_acumulados DESC, partidas_ganadas DESC, nombre ASC LIMIT " + TAMANO_RANKING;

    /** Nombre de la entidad en la cola de reintentos. */
    public static final String ENTIDAD = "JUGADOR";

    private final GestorConexion gestorConexion;

    public ImpOperacionesJugadorBD(GestorConexion gestorConexion) {
        this.gestorConexion = gestorConexion;
        this.gestorConexion.registrarReplicador(ENTIDAD, ImpOperacionesJugadorBD::replicarDesdeLocal);
    }

    /** Inserta en la base principal; la réplica en Neon inserta o actualiza para tolerar reintentos. */
    @Override
    public void crear(Jugador dato) {
        try {
            this.gestorConexion.ejecutarEscritura(con -> insertar(con, dato), con -> guardarIdempotente(con, dato),
                    ENTIDAD, String.valueOf(dato.getId()));
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo crear el jugador " + dato.getNombre() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public List<Jugador> obtenerTodos() {
        return consultarLista(SQL_TODOS, null);
    }

    @Override
    public void actualizar(Jugador dato) {
        try {
            this.gestorConexion.ejecutarEscritura(con -> {
                if (actualizarFila(con, dato) == 0) {
                    throw new SQLException("No existe el jugador con id " + dato.getId());
                }
            }, con -> guardarIdempotente(con, dato), ENTIDAD, String.valueOf(dato.getId()));
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo actualizar el jugador " + dato.getId() + ": " + e.getMessage(), e);
        }
    }

    /** Top 10 histórico: más puntos primero; en empate, más partidas ganadas. */
    public List<Jugador> obtenerRankingOrdenadoPorPuntos() {
        return consultarLista(SQL_RANKING, null);
    }

    /** Devuelve null si no existe. */
    public Jugador buscarPorId(int id) {
        List<Jugador> resultado = consultarLista(SQL_POR_ID, ps -> ps.setInt(1, id));
        return resultado.isEmpty() ? null : resultado.get(0);
    }

    /** Inicio de sesión. Devuelve null si no existe. */
    public Jugador buscarPorNombre(String nombre) {
        List<Jugador> resultado = consultarLista(SQL_POR_NOMBRE, ps -> ps.setString(1, nombre));
        return resultado.isEmpty() ? null : resultado.get(0);
    }

    // ------------------------------------------------------------------ JDBC

    private interface Parametros {
        void asignar(PreparedStatement ps) throws SQLException;
    }

    private List<Jugador> consultarLista(String sql, Parametros parametros) {
        List<Jugador> lista = new ArrayList<>();
        try (Connection con = this.gestorConexion.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (parametros != null) {
                parametros.asignar(ps);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error consultando jugadores: " + e.getMessage(), e);
        }
        return lista;
    }

    private static Jugador mapear(ResultSet rs) throws SQLException {
        return new Jugador(
                rs.getInt("id"),
                rs.getString("nombre"),
                rs.getBoolean("es_bot"),
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
            ps.setBoolean(3, j.isEsBot());
            ps.setInt(4, j.getPuntosAcumulados());
            ps.setInt(5, j.getPartidasJugadas());
            ps.setInt(6, j.getPartidasGanadas());
            ps.executeUpdate();
        }
    }

    private static int actualizarFila(Connection con, Jugador j) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(SQL_ACTUALIZAR)) {
            ps.setString(1, j.getNombre());
            ps.setBoolean(2, j.isEsBot());
            ps.setInt(3, j.getPuntosAcumulados());
            ps.setInt(4, j.getPartidasJugadas());
            ps.setInt(5, j.getPartidasGanadas());
            ps.setInt(6, j.getId());
            return ps.executeUpdate();
        }
    }

    /** Reintento: copia a la nube el estado actual en H2 del jugador con ese id. */
    private static void replicarDesdeLocal(Connection local, Connection nube, String clave) throws SQLException {
        try (PreparedStatement ps = local.prepareStatement(SQL_POR_ID)) {
            ps.setInt(1, Integer.parseInt(clave));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    guardarIdempotente(nube, mapear(rs));
                }
            }
        }
    }

    /** Actualiza y, si la fila no existía (p. ej. se perdió la réplica de crear), la inserta. */
    private static void guardarIdempotente(Connection con, Jugador j) throws SQLException {
        if (actualizarFila(con, j) == 0) {
            insertar(con, j);
        }
    }
}
