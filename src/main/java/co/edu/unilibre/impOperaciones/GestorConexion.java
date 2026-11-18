package co.edu.unilibre.impOperaciones;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Suministra las conexiones y aplica la estrategia de persistencia:
 * <ul>
 *   <li>Conexión caliente: H2 embebido (respuesta inmediata al jugador).</li>
 *   <li>Conexión fría: Neon PostgreSQL en la nube.</li>
 * </ul>
 * En modo {@link ModoPersistencia#HIBRIDO_ASINCRONO} cada escritura se hace primero en H2 de forma
 * síncrona y luego se replica en Neon con {@link CompletableFuture#runAsync} sobre un hilo dedicado.
 * <p>
 * Si la réplica falla (por ejemplo, sin internet), la entidad afectada se anota en la tabla local
 * {@code sincronizacion_pendiente}. Cada cierto tiempo el mismo hilo reintenta: lee el estado actual
 * de esa fila en H2 y lo copia a Neon. La cola vive en H2, así que también sobrevive a un reinicio.
 */
public class GestorConexion {

    public static final String DRIVER_H2 = "org.h2.Driver";
    public static final String DRIVER_POSTGRESQL = "org.postgresql.Driver";
    public static final String URL_H2_LOCAL =
            "jdbc:h2:./db_unogame;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE";
    public static final String USUARIO_H2_LOCAL = "sa";
    public static final String CLAVE_H2_LOCAL = "";

    /** Archivo de respaldo para desarrollo local; está en .gitignore y nunca se sube al repositorio. */
    public static final String ARCHIVO_CONFIGURACION_LOCAL = "config.local.properties";

    /** Segundos entre reintentos automáticos si no se define UNO_REINTENTO_SEGUNDOS. */
    public static final int INTERVALO_REINTENTO_POR_DEFECTO = 30;

    /** Entidad de la cola que representa las tablas de la nube (se crean antes que cualquier dato). */
    public static final String ENTIDAD_ESQUEMA = "ESQUEMA";

    private static final String[] SENTENCIAS_DDL = {
        "CREATE TABLE IF NOT EXISTS jugadores ("
            + "id INT PRIMARY KEY, "
            + "nombre VARCHAR(100) NOT NULL, "
            + "es_bot BOOLEAN NOT NULL, "
            + "puntos_acumulados INT DEFAULT 0, "
            + "partidas_jugadas INT DEFAULT 0, "
            + "partidas_ganadas INT DEFAULT 0)",
        "CREATE TABLE IF NOT EXISTS partidas ("
            + "id_partida VARCHAR(50) PRIMARY KEY, "
            + "semilla BIGINT NOT NULL, "
            + "fecha DATE NOT NULL, "
            + "id_ganador INT, "
            + "puntos_ganador INT DEFAULT 0, "
            + "sospecha_trampa BOOLEAN DEFAULT FALSE, "
            + "log_jugadas TEXT)",
        "CREATE TABLE IF NOT EXISTS jugadas_tiempo_real ("
            + "id_jugada VARCHAR(60) PRIMARY KEY, "
            + "id_partida VARCHAR(50) NOT NULL, "
            + "turno INT NOT NULL, "
            + "id_jugador INT NOT NULL, "
            + "accion VARCHAR(100) NOT NULL, "
            + "marca_tiempo TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
    };

    /** Cola de réplicas fallidas. Solo existe en H2 local. */
    private static final String DDL_PENDIENTES =
        "CREATE TABLE IF NOT EXISTS sincronizacion_pendiente ("
            + "entidad VARCHAR(20) NOT NULL, "
            + "clave VARCHAR(60) NOT NULL, "
            + "intentos INT DEFAULT 0, "
            + "ultimo_error VARCHAR(500), "
            + "marca_tiempo TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
            + "PRIMARY KEY (entidad, clave))";

    /** Una operación JDBC que se puede ejecutar contra cualquiera de las dos bases. */
    @FunctionalInterface
    public interface OperacionSql {
        void ejecutar(Connection conexion) throws SQLException;
    }

    /** Copia a la nube el estado actual en H2 de una entidad identificada por su clave. */
    @FunctionalInterface
    public interface Replicador {
        void replicar(Connection local, Connection nube, String clave) throws SQLException;
    }

    static {
        cargarDriver(DRIVER_H2);
        cargarDriver(DRIVER_POSTGRESQL);
    }

    private final ModoPersistencia modo;

    // Conexión caliente (H2)
    private final String urlLocal;
    private final String usuarioLocal;
    private final String claveLocal;

    // Conexión fría (Neon)
    private final String urlConexion;
    private final String usuario;
    private final String clave;
    private final boolean nubeConfigurada;

    /** Un solo hilo para réplicas y reintentos: Neon recibe las escrituras en el mismo orden que H2. */
    private final ScheduledExecutorService ejecutorNube;
    private final Map<String, Replicador> replicadores = new ConcurrentHashMap<>();
    private final Queue<CompletableFuture<?>> sincronizacionesPendientes = new ConcurrentLinkedQueue<>();
    private final AtomicInteger sincronizacionesExitosas = new AtomicInteger();
    private final AtomicInteger sincronizacionesFallidas = new AtomicInteger();

    /** Modo leído de UNO_MODO_PERSISTENCIA (por defecto HIBRIDO_ASINCRONO). */
    public GestorConexion() {
        this(leerModoDesdeEntorno());
    }

    /** H2 en ./db_unogame y Neon desde DB_URL/DB_USER/DB_PASSWORD o, si faltan, desde config.local.properties. */
    public GestorConexion(ModoPersistencia modo) {
        this(modo, URL_H2_LOCAL, USUARIO_H2_LOCAL, CLAVE_H2_LOCAL);
    }

    /** H2 indicado y Neon desde DB_URL/DB_USER/DB_PASSWORD o config.local.properties. */
    public GestorConexion(ModoPersistencia modo, String urlLocal, String usuarioLocal, String claveLocal) {
        this(modo, urlLocal, usuarioLocal, claveLocal,
                leerValorNube("DB_URL", "nube.url"),
                leerValorNube("DB_USER", "nube.usuario"),
                leerValorNube("DB_PASSWORD", "nube.clave"));
    }

    /** Conexiones explícitas; el intervalo de reintento sale de UNO_REINTENTO_SEGUNDOS (por defecto 30 s). */
    public GestorConexion(ModoPersistencia modo,
                          String urlLocal, String usuarioLocal, String claveLocal,
                          String urlNube, String usuarioNube, String claveNube) {
        this(modo, urlLocal, usuarioLocal, claveLocal, urlNube, usuarioNube, claveNube, leerIntervaloDesdeEntorno());
    }

    /**
     * Conexiones explícitas e intervalo de reintento automático.
     *
     * @param intervaloReintentoSegundos 0 desactiva los reintentos automáticos (se pueden pedir con
     *                                   {@link #reintentarPendientes()}).
     */
    public GestorConexion(ModoPersistencia modo,
                          String urlLocal, String usuarioLocal, String claveLocal,
                          String urlNube, String usuarioNube, String claveNube,
                          int intervaloReintentoSegundos) {
        this.modo = modo;
        this.urlLocal = urlLocal;
        this.usuarioLocal = usuarioLocal;
        this.claveLocal = claveLocal;
        this.urlConexion = urlNube;
        this.usuario = usuarioNube;
        this.clave = claveNube;
        this.nubeConfigurada = urlNube != null && !urlNube.isBlank();

        if (modo == ModoPersistencia.NEON_NUBE && !this.nubeConfigurada) {
            throw new IllegalStateException("Modo NEON_NUBE sin conexión configurada: defina DB_URL o "
                    + ARCHIVO_CONFIGURACION_LOCAL + ".");
        }
        if (modo == ModoPersistencia.HIBRIDO_ASINCRONO && !this.nubeConfigurada) {
            System.err.println("Aviso: no hay conexión a Neon configurada; el modo híbrido solo escribirá en H2.");
        }

        this.ejecutorNube = Executors.newSingleThreadScheduledExecutor(tarea -> {
            Thread hilo = new Thread(tarea, "uno-sincronizador-neon");
            hilo.setDaemon(true);
            return hilo;
        });
        registrarReplicador(ENTIDAD_ESQUEMA, (local, nube, claveEntidad) -> crearTablas(nube));
        if (usaReplica() && intervaloReintentoSegundos > 0) {
            this.ejecutorNube.scheduleWithFixedDelay(this::reintentarEnHiloActual,
                    intervaloReintentoSegundos, intervaloReintentoSegundos, TimeUnit.SECONDS);
        }
    }

    // ------------------------------------------------------------------ conexiones

    /** Conexión principal para lecturas: H2 salvo en modo NEON_NUBE. */
    public Connection obtenerConexion() throws SQLException {
        return this.modo == ModoPersistencia.NEON_NUBE ? obtenerConexionNube() : obtenerConexionLocal();
    }

    public Connection obtenerConexionLocal() throws SQLException {
        return DriverManager.getConnection(this.urlLocal, this.usuarioLocal, this.claveLocal);
    }

    public Connection obtenerConexionNube() throws SQLException {
        if (!this.nubeConfigurada) {
            throw new SQLException("No hay conexión a Neon configurada.");
        }
        Properties propiedades = new Properties();
        if (this.usuario != null) propiedades.setProperty("user", this.usuario);
        if (this.clave != null) propiedades.setProperty("password", this.clave);
        if (this.urlConexion.startsWith("jdbc:postgresql:")) {
            // Que una red lenta no deje el hilo de sincronización colgado indefinidamente.
            propiedades.setProperty("connectTimeout", "5");
            propiedades.setProperty("socketTimeout", "15");
        }
        return DriverManager.getConnection(this.urlConexion, propiedades);
    }

    // ------------------------------------------------------------------ escritura

    /** Escritura con la misma operación en las dos bases y sin reintento. */
    public CompletableFuture<Void> ejecutarEscritura(OperacionSql operacion) throws SQLException {
        return ejecutarEscritura(operacion, operacion);
    }

    /** Escritura sin reintento (la réplica fallida solo se cuenta). */
    public CompletableFuture<Void> ejecutarEscritura(OperacionSql principal, OperacionSql replica) throws SQLException {
        return ejecutarEscritura(principal, replica, null, null);
    }

    /**
     * Escribe según el modo. {@code principal} corre de forma síncrona en la base principal;
     * {@code replica} es la versión tolerante a reintentos que se envía a Neon en modo híbrido.
     * Si la réplica falla, ({@code entidad}, {@code clave}) queda en la cola de reintentos.
     *
     * @return futuro de la réplica en la nube (ya completado si no hay réplica).
     */
    public CompletableFuture<Void> ejecutarEscritura(OperacionSql principal, OperacionSql replica,
                                                     String entidad, String clave) throws SQLException {
        switch (this.modo) {
            case H2_LOCAL -> {
                ejecutarEn(obtenerConexionLocal(), principal);
                return CompletableFuture.completedFuture(null);
            }
            case NEON_NUBE -> {
                ejecutarEn(obtenerConexionNube(), principal);
                return CompletableFuture.completedFuture(null);
            }
            default -> {
                ejecutarEn(obtenerConexionLocal(), principal);
                return despacharANube(replica, entidad, clave);
            }
        }
    }

    /** Envía una operación a Neon en paralelo, sin reintento. Nunca lanza excepción al llamador. */
    public CompletableFuture<Void> despacharANube(OperacionSql operacion) {
        return despacharANube(operacion, null, null);
    }

    /**
     * Envía una operación a Neon en paralelo. Nunca lanza excepción al llamador.
     * Si falla y se indicó la entidad, queda anotada para reintentar.
     */
    public CompletableFuture<Void> despacharANube(OperacionSql operacion, String entidad, String clave) {
        if (!usaReplica()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> futuro = CompletableFuture
                .runAsync(() -> {
                    try (Connection conexion = obtenerConexionNube()) {
                        operacion.ejecutar(conexion);
                    } catch (SQLException e) {
                        throw new CompletionException(e);
                    }
                }, this.ejecutorNube)
                .handle((resultado, error) -> {
                    if (error == null) {
                        this.sincronizacionesExitosas.incrementAndGet();
                        if (entidad != null) {
                            borrarPendiente(entidad, clave); // Ya está al día: si estaba en cola, sale.
                        }
                    } else {
                        this.sincronizacionesFallidas.incrementAndGet();
                        Throwable causa = error.getCause() != null ? error.getCause() : error;
                        System.err.println("Sincronización con Neon fallida (la partida continúa): " + causa.getMessage());
                        if (entidad != null) {
                            registrarPendiente(entidad, clave, causa.getMessage());
                        }
                    }
                    return (Void) null;
                });
        registrarFuturo(futuro);
        return futuro;
    }

    // ------------------------------------------------------------------ reintentos

    /** Los DAO registran aquí cómo copiar cada tipo de entidad de H2 a Neon. */
    public void registrarReplicador(String entidad, Replicador replicador) {
        this.replicadores.put(entidad, replicador);
    }

    /**
     * Pide un reintento inmediato de todo lo pendiente (en el hilo de sincronización).
     *
     * @return futuro con la cantidad de entidades que quedaron sincronizadas.
     */
    public CompletableFuture<Integer> reintentarPendientes() {
        if (!usaReplica()) {
            return CompletableFuture.completedFuture(0);
        }
        CompletableFuture<Integer> futuro = CompletableFuture.supplyAsync(this::reintentarEnHiloActual, this.ejecutorNube);
        registrarFuturo(futuro);
        return futuro;
    }

    /** Cantidad de entidades que esperan llegar a Neon. */
    public int getPendientesDeSincronizar() {
        if (!usaReplica()) {
            return 0;
        }
        try (Connection c = obtenerConexionLocal();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM sincronizacion_pendiente")) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            return 0;
        }
    }

    /** Solo se ejecuta en el hilo de sincronización. */
    private int reintentarEnHiloActual() {
        try {
            List<String[]> pendientes = leerPendientes();
            if (pendientes.isEmpty()) {
                return 0;
            }
            int sincronizadas = 0;
            try (Connection nube = obtenerConexionNube()) {
                for (String[] pendiente : pendientes) {
                    Replicador replicador = this.replicadores.get(pendiente[0]);
                    if (replicador == null) {
                        continue; // Aún no se creó el DAO que sabe copiar esta entidad.
                    }
                    try (Connection local = obtenerConexionLocal()) {
                        replicador.replicar(local, nube, pendiente[1]);
                        borrarPendiente(pendiente[0], pendiente[1]);
                        this.sincronizacionesExitosas.incrementAndGet();
                        sincronizadas++;
                    } catch (SQLException e) {
                        registrarPendiente(pendiente[0], pendiente[1], e.getMessage());
                        if (!nube.isValid(2)) {
                            break; // Se volvió a caer la red: el resto espera al próximo intento.
                        }
                    }
                }
            } catch (SQLException e) {
                // La nube sigue sin responder: se anota el intento en todos los pendientes.
                marcarIntentoFallidoEnTodos(e.getMessage());
            }
            if (sincronizadas > 0) {
                System.out.println("Reintento con Neon: " + sincronizadas + " registro(s) sincronizado(s).");
            }
            return sincronizadas;
        } catch (RuntimeException e) {
            // Un error inesperado no debe cancelar los reintentos programados.
            System.err.println("Error en el reintento de sincronización: " + e.getMessage());
            return 0;
        }
    }

    private List<String[]> leerPendientes() {
        List<String[]> lista = new ArrayList<>();
        try (Connection c = obtenerConexionLocal();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT entidad, clave FROM sincronizacion_pendiente ORDER BY "
                             + "CASE WHEN entidad = '" + ENTIDAD_ESQUEMA + "' THEN 0 ELSE 1 END, marca_tiempo, entidad, clave")) {
            while (rs.next()) {
                lista.add(new String[]{rs.getString(1), rs.getString(2)});
            }
        } catch (SQLException e) {
            // Sin tabla de pendientes (todavía no se inicializó la base): nada que reintentar.
        }
        return lista;
    }

    private void registrarPendiente(String entidad, String claveEntidad, String error) {
        String mensaje = error == null ? "" : (error.length() > 500 ? error.substring(0, 500) : error);
        try (Connection c = obtenerConexionLocal()) {
            int filas;
            try (PreparedStatement ps = c.prepareStatement("UPDATE sincronizacion_pendiente "
                    + "SET intentos = intentos + 1, ultimo_error = ? WHERE entidad = ? AND clave = ?")) {
                ps.setString(1, mensaje);
                ps.setString(2, entidad);
                ps.setString(3, claveEntidad);
                filas = ps.executeUpdate();
            }
            if (filas == 0) {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO sincronizacion_pendiente "
                        + "(entidad, clave, intentos, ultimo_error) VALUES (?, ?, 1, ?)")) {
                    ps.setString(1, entidad);
                    ps.setString(2, claveEntidad);
                    ps.setString(3, mensaje);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.err.println("No se pudo anotar el pendiente " + entidad + "/" + claveEntidad + ": " + e.getMessage());
        }
    }

    private void borrarPendiente(String entidad, String claveEntidad) {
        try (Connection c = obtenerConexionLocal();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM sincronizacion_pendiente WHERE entidad = ? AND clave = ?")) {
            ps.setString(1, entidad);
            ps.setString(2, claveEntidad);
            ps.executeUpdate();
        } catch (SQLException e) {
            // Si no se puede borrar, el siguiente reintento la vuelve a copiar (es idempotente).
        }
    }

    private void marcarIntentoFallidoEnTodos(String error) {
        String mensaje = error == null ? "" : (error.length() > 500 ? error.substring(0, 500) : error);
        try (Connection c = obtenerConexionLocal();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE sincronizacion_pendiente SET intentos = intentos + 1, ultimo_error = ?")) {
            ps.setString(1, mensaje);
            ps.executeUpdate();
        } catch (SQLException e) {
            // Nada más que hacer: el próximo intento lo vuelve a probar.
        }
    }

    /** Espera a que terminen las réplicas y reintentos pendientes. Devuelve false si se agotó el tiempo. */
    public boolean esperarSincronizacion(long milisegundos) {
        CompletableFuture<?>[] pendientes = this.sincronizacionesPendientes.toArray(new CompletableFuture<?>[0]);
        try {
            CompletableFuture.allOf(pendientes).get(milisegundos, TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Espera las réplicas pendientes (máx. 10 s) y libera el hilo de sincronización. */
    public void cerrar() {
        esperarSincronizacion(10_000);
        this.ejecutorNube.shutdown();
    }

    // ------------------------------------------------------------------ DDL

    /** Crea las tablas si no existen en las bases que use el modo actual. */
    public void inicializarBaseDeDatos() {
        OperacionSql principal = conexion -> {
            crearTablas(conexion);
            if (this.modo == ModoPersistencia.HIBRIDO_ASINCRONO) {
                try (Statement stmt = conexion.createStatement()) {
                    stmt.execute(DDL_PENDIENTES);
                }
            }
        };
        try {
            // Si la nube no responde, el esquema queda en la cola y se crea antes que todo lo demás.
            ejecutarEscritura(principal, GestorConexion::crearTablas, ENTIDAD_ESQUEMA, "tablas");
            System.out.println("Tablas de base de datos inicializadas correctamente (modo " + this.modo + ").");
        } catch (SQLException e) {
            throw new IllegalStateException("Error al inicializar tablas DDL: " + e.getMessage(), e);
        }
    }

    private static void crearTablas(Connection conexion) throws SQLException {
        try (Statement stmt = conexion.createStatement()) {
            for (String sql : SENTENCIAS_DDL) {
                stmt.execute(sql);
            }
        }
    }

    // ------------------------------------------------------------------ utilitarios

    private boolean usaReplica() {
        return this.modo == ModoPersistencia.HIBRIDO_ASINCRONO && this.nubeConfigurada;
    }

    private void registrarFuturo(CompletableFuture<?> futuro) {
        this.sincronizacionesPendientes.removeIf(CompletableFuture::isDone);
        this.sincronizacionesPendientes.add(futuro);
    }

    private static void ejecutarEn(Connection conexion, OperacionSql operacion) throws SQLException {
        try (Connection c = conexion) {
            operacion.ejecutar(c);
        }
    }

    private static ModoPersistencia leerModoDesdeEntorno() {
        String valor = System.getenv("UNO_MODO_PERSISTENCIA");
        if (valor == null || valor.isBlank()) {
            return ModoPersistencia.HIBRIDO_ASINCRONO;
        }
        return ModoPersistencia.valueOf(valor.trim().toUpperCase());
    }

    private static int leerIntervaloDesdeEntorno() {
        String valor = System.getenv("UNO_REINTENTO_SEGUNDOS");
        if (valor == null || valor.isBlank()) {
            return INTERVALO_REINTENTO_POR_DEFECTO;
        }
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            return INTERVALO_REINTENTO_POR_DEFECTO;
        }
    }

    private static String leerValorNube(String variableEntorno, String claveArchivo) {
        String valor = System.getenv(variableEntorno);
        if (valor != null && !valor.isBlank()) {
            return valor;
        }
        Path archivo = Paths.get(ARCHIVO_CONFIGURACION_LOCAL);
        if (!Files.exists(archivo)) {
            return null;
        }
        Properties propiedades = new Properties();
        try (InputStream entrada = Files.newInputStream(archivo)) {
            propiedades.load(entrada);
        } catch (IOException e) {
            System.err.println("No se pudo leer " + ARCHIVO_CONFIGURACION_LOCAL + ": " + e.getMessage());
            return null;
        }
        // Acepta el mismo nombre de la variable de entorno (DB_URL...) o el alternativo (nube.url...).
        String deArchivo = propiedades.getProperty(variableEntorno, propiedades.getProperty(claveArchivo));
        return deArchivo == null || deArchivo.isBlank() ? null : deArchivo.trim();
    }

    private static void cargarDriver(String driver) {
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            System.err.println("Driver no encontrado en el classpath: " + driver);
        }
    }

    // ------------------------------------------------------------------ consultas de estado

    public ModoPersistencia getModo() {
        return this.modo;
    }

    public boolean isNubeConfigurada() {
        return this.nubeConfigurada;
    }

    public int getSincronizacionesExitosas() {
        return this.sincronizacionesExitosas.get();
    }

    public int getSincronizacionesFallidas() {
        return this.sincronizacionesFallidas.get();
    }
}
