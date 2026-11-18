package co.edu.unilibre.impOperaciones;

import co.edu.unilibre.dto.Partida;
import co.edu.unilibre.operaciones.GenericDao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Persistencia de partidas cerradas y del registro de jugadas en tiempo real. */
public class ImpOperacionesPartidaBD implements GenericDao<Partida> {

    private static final String COLUMNAS =
            "id_partida, semilla, fecha, id_ganador, puntos_ganador, sospecha_trampa, log_jugadas";
    private static final String SQL_INSERTAR =
            "INSERT INTO partidas (" + COLUMNAS + ") VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String SQL_ACTUALIZAR =
            "UPDATE partidas SET semilla = ?, fecha = ?, id_ganador = ?, puntos_ganador = ?, "
                    + "sospecha_trampa = ?, log_jugadas = ? WHERE id_partida = ?";
    private static final String SQL_TODAS = "SELECT " + COLUMNAS + " FROM partidas ORDER BY fecha, id_partida";

    private static final String SQL_INSERTAR_JUGADA =
            "INSERT INTO jugadas_tiempo_real (id_jugada, id_partida, turno, id_jugador, accion) VALUES (?, ?, ?, ?, ?)";
    private static final String SQL_EXISTE_JUGADA = "SELECT COUNT(*) FROM jugadas_tiempo_real WHERE id_jugada = ?";
    private static final String SQL_JUGADAS_DE_PARTIDA =
            "SELECT accion FROM jugadas_tiempo_real WHERE id_partida = ? ORDER BY turno";

    private static final String SQL_PARTIDA_POR_ID = "SELECT " + COLUMNAS + " FROM partidas WHERE id_partida = ?";
    private static final String SQL_JUGADA_POR_ID =
            "SELECT id_partida, turno, id_jugador, accion FROM jugadas_tiempo_real WHERE id_jugada = ?";

    /** Nombres de las entidades en la cola de reintentos. */
    public static final String ENTIDAD_PARTIDA = "PARTIDA";
    public static final String ENTIDAD_JUGADA = "JUGADA";

    private final GestorConexion gestorConexion;

    public ImpOperacionesPartidaBD(GestorConexion gestorConexion) {
        this.gestorConexion = gestorConexion;
        this.gestorConexion.registrarReplicador(ENTIDAD_PARTIDA, ImpOperacionesPartidaBD::replicarPartida);
        this.gestorConexion.registrarReplicador(ENTIDAD_JUGADA, ImpOperacionesPartidaBD::replicarJugada);
    }

    @Override
    public void crear(Partida dato) {
        try {
            this.gestorConexion.ejecutarEscritura(con -> insertar(con, dato), con -> guardarIdempotente(con, dato),
                    ENTIDAD_PARTIDA, dato.getIdPartida());
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo crear la partida " + dato.getIdPartida() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public List<Partida> obtenerTodos() {
        List<Partida> lista = new ArrayList<>();
        try (Connection con = this.gestorConexion.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(SQL_TODAS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new Partida(
                        rs.getString("id_partida"),
                        rs.getLong("semilla"),
                        rs.getDate("fecha").toLocalDate(),
                        rs.getInt("id_ganador"),
                        rs.getInt("puntos_ganador"),
                        rs.getBoolean("sospecha_trampa"),
                        rs.getString("log_jugadas")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error consultando partidas: " + e.getMessage(), e);
        }
        return lista;
    }

    @Override
    public void actualizar(Partida dato) {
        try {
            this.gestorConexion.ejecutarEscritura(con -> {
                if (actualizarFila(con, dato) == 0) {
                    throw new SQLException("No existe la partida " + dato.getIdPartida());
                }
            }, con -> guardarIdempotente(con, dato), ENTIDAD_PARTIDA, dato.getIdPartida());
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo actualizar la partida " + dato.getIdPartida() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Registra un movimiento mientras la partida está en curso. Se escribe al instante en H2 y,
     * en modo híbrido, se replica en Neon sin esperar la respuesta de la red.
     */
    public void registrarJugadaInstantanea(String idPartida, int turno, int idJugador, String accion) {
        String idJugada = idPartida + "-" + turno;
        try {
            this.gestorConexion.ejecutarEscritura(
                    con -> insertarJugada(con, idJugada, idPartida, turno, idJugador, accion),
                    con -> {
                        if (!existeJugada(con, idJugada)) {
                            insertarJugada(con, idJugada, idPartida, turno, idJugador, accion);
                        }
                    },
                    ENTIDAD_JUGADA, idJugada);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo registrar la jugada " + idJugada + ": " + e.getMessage(), e);
        }
    }

    /** Acciones registradas de una partida, en orden de turno. */
    public List<String> obtenerJugadasDePartida(String idPartida) {
        List<String> acciones = new ArrayList<>();
        try (Connection con = this.gestorConexion.obtenerConexion();
             PreparedStatement ps = con.prepareStatement(SQL_JUGADAS_DE_PARTIDA)) {
            ps.setString(1, idPartida);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    acciones.add(rs.getString("accion"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error consultando jugadas: " + e.getMessage(), e);
        }
        return acciones;
    }

    // ------------------------------------------------------------------ reintentos

    /** Reintento: copia a la nube el estado actual en H2 de la partida. */
    private static void replicarPartida(Connection local, Connection nube, String idPartida) throws SQLException {
        try (PreparedStatement ps = local.prepareStatement(SQL_PARTIDA_POR_ID)) {
            ps.setString(1, idPartida);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    guardarIdempotente(nube, new Partida(
                            rs.getString("id_partida"),
                            rs.getLong("semilla"),
                            rs.getDate("fecha").toLocalDate(),
                            rs.getInt("id_ganador"),
                            rs.getInt("puntos_ganador"),
                            rs.getBoolean("sospecha_trampa"),
                            rs.getString("log_jugadas")));
                }
            }
        }
    }

    /** Reintento: copia a la nube una jugada en tiempo real si todavía no está allá. */
    private static void replicarJugada(Connection local, Connection nube, String idJugada) throws SQLException {
        try (PreparedStatement ps = local.prepareStatement(SQL_JUGADA_POR_ID)) {
            ps.setString(1, idJugada);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && !existeJugada(nube, idJugada)) {
                    insertarJugada(nube, idJugada, rs.getString("id_partida"), rs.getInt("turno"),
                            rs.getInt("id_jugador"), rs.getString("accion"));
                }
            }
        }
    }

    // ------------------------------------------------------------------ JDBC

    private static void insertar(Connection con, Partida p) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(SQL_INSERTAR)) {
            ps.setString(1, p.getIdPartida());
            ps.setLong(2, p.getSemilla());
            ps.setDate(3, Date.valueOf(p.getFecha()));
            ps.setInt(4, p.getIdGanador());
            ps.setInt(5, p.getPuntosGanador());
            ps.setBoolean(6, p.isSospechaTrampa());
            ps.setString(7, p.getLogJugadas());
            ps.executeUpdate();
        }
    }

    private static int actualizarFila(Connection con, Partida p) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(SQL_ACTUALIZAR)) {
            ps.setLong(1, p.getSemilla());
            ps.setDate(2, Date.valueOf(p.getFecha()));
            ps.setInt(3, p.getIdGanador());
            ps.setInt(4, p.getPuntosGanador());
            ps.setBoolean(5, p.isSospechaTrampa());
            ps.setString(6, p.getLogJugadas());
            ps.setString(7, p.getIdPartida());
            return ps.executeUpdate();
        }
    }

    private static void guardarIdempotente(Connection con, Partida p) throws SQLException {
        if (actualizarFila(con, p) == 0) {
            insertar(con, p);
        }
    }

    private static void insertarJugada(Connection con, String idJugada, String idPartida, int turno,
                                       int idJugador, String accion) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(SQL_INSERTAR_JUGADA)) {
            ps.setString(1, idJugada);
            ps.setString(2, idPartida);
            ps.setInt(3, turno);
            ps.setInt(4, idJugador);
            ps.setString(5, accion);
            ps.executeUpdate();
        }
    }

    private static boolean existeJugada(Connection con, String idJugada) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(SQL_EXISTE_JUGADA)) {
            ps.setString(1, idJugada);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
}
