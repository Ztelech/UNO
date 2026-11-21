package co.edu.unilibre.impOperaciones;

import co.edu.unilibre.dto.Jugador;
import co.edu.unilibre.dto.Partida;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Pone de acuerdo el H2 de este computador con Neon, para que varios computadores compartan
 * perfiles y ranking. Se ejecuta al abrir el juego, en el hilo de sincronización:
 * <ol>
 *   <li>Crea las tablas en Neon si faltan.</li>
 *   <li>Une los perfiles por nombre: si "Brenda" ya existe en Neon con otro id, el perfil local
 *       pasa a usar ese id (también en sus partidas y registros). Los perfiles que solo existen
 *       aquí reciben un id libre en Neon.</li>
 *   <li>Sube las partidas y jugadas que Neon no tenga (cada partida suma su resultado al ranking).</li>
 *   <li>Baja de Neon los perfiles con sus estadísticas globales.</li>
 * </ol>
 */
public class SincronizadorNube {

    /** Resultado de una sincronización, para mostrar en la interfaz. */
    public record Resultado(boolean conectado, int perfilesUnificados, int partidasSubidas, int jugadasSubidas) {
    }

    private final GestorConexion gestorConexion;

    public SincronizadorNube(GestorConexion gestorConexion) {
        this.gestorConexion = gestorConexion;
    }

    /** Sincroniza en el hilo de Neon sin bloquear la interfaz. */
    public CompletableFuture<Resultado> sincronizarEnSegundoPlano() {
        CompletableFuture<Resultado> resultado = new CompletableFuture<>();
        this.gestorConexion.ejecutarEnHiloNube(() -> {
            resultado.complete(sincronizar());
            return true;
        });
        return resultado;
    }

    /** Sincroniza ahora, en el hilo que llama. Nunca lanza excepción: si no hay Neon, conectado=false. */
    public Resultado sincronizar() {
        if (!this.gestorConexion.isModoHibridoConNube()) {
            return new Resultado(false, 0, 0, 0);
        }
        try (Connection nube = this.gestorConexion.obtenerConexionNube();
             Connection local = this.gestorConexion.obtenerConexionLocal()) {
            this.gestorConexion.marcarNubeDisponible(true);
            this.gestorConexion.crearTablasEn(nube);
            int unificados = unificarPerfiles(local, nube);
            int partidas = subirPartidasFaltantes(local, nube);
            int jugadas = subirJugadasFaltantes(local, nube);
            bajarPerfiles(local, nube);
            if (unificados + partidas + jugadas > 0) {
                System.out.println("Sincronización con Neon: " + unificados + " perfil(es) unificado(s), "
                        + partidas + " partida(s) y " + jugadas + " jugada(s) subidas.");
            }
            return new Resultado(true, unificados, partidas, jugadas);
        } catch (SQLException e) {
            this.gestorConexion.marcarNubeDisponible(false);
            System.err.println("No se pudo sincronizar con Neon (se reintentará): " + e.getMessage());
            return new Resultado(false, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------ 1. perfiles

    /** @return cuántos perfiles locales cambiaron de id para coincidir con Neon. */
    int unificarPerfiles(Connection local, Connection nube) throws SQLException {
        List<Jugador> locales = ImpOperacionesJugadorBD.leerTodos(local);
        List<Jugador> enNube = ImpOperacionesJugadorBD.leerTodos(nube);
        Map<String, Integer> idNubePorNombre = new HashMap<>();
        Set<Integer> idsOcupados = new HashSet<>();
        int maximo = 0;
        for (Jugador j : enNube) {
            idNubePorNombre.put(j.getNombre(), j.getId());
            idsOcupados.add(j.getId());
            maximo = Math.max(maximo, j.getId());
        }
        for (Jugador j : locales) {
            maximo = Math.max(maximo, j.getId());
        }

        Map<Integer, Integer> cambios = new LinkedHashMap<>();
        // Primero los que ya existen en Neon con el mismo nombre.
        for (Jugador j : locales) {
            Integer idNube = idNubePorNombre.get(j.getNombre());
            if (idNube != null) {
                if (idNube != j.getId()) {
                    cambios.put(j.getId(), idNube);
                }
            }
        }
        // Luego los que solo existen aquí: conservan su id si está libre en Neon; si no, uno nuevo.
        for (Jugador j : locales) {
            if (idNubePorNombre.containsKey(j.getNombre())) {
                continue;
            }
            if (idsOcupados.contains(j.getId()) || cambios.containsValue(j.getId())) {
                maximo++;
                cambios.put(j.getId(), maximo);
                idsOcupados.add(maximo);
            } else {
                idsOcupados.add(j.getId());
            }
        }
        if (!cambios.isEmpty()) {
            renumerarLocal(local, cambios);
        }
        // Los perfiles que solo están aquí se crean en Neon (con estadísticas en cero:
        // las suman sus partidas al subir).
        for (Jugador j : ImpOperacionesJugadorBD.leerTodos(local)) {
            if (!idNubePorNombre.containsKey(j.getNombre())) {
                ImpOperacionesJugadorBD.asegurarEnNube(local, nube, j.getId());
            }
        }
        return cambios.size();
    }

    /** Cambia ids de jugadores en H2: perfiles, partidas, registros y cola de pendientes, en una transacción. */
    static void renumerarLocal(Connection local, Map<Integer, Integer> cambios) throws SQLException {
        boolean autoCommitPrevio = local.getAutoCommit();
        local.setAutoCommit(false);
        try {
            // Dos pasos (primero a ids temporales negativos) para que un intercambio 1<->2 no choque.
            try (PreparedStatement ps = local.prepareStatement("UPDATE jugadores SET id = ? WHERE id = ?")) {
                for (Map.Entry<Integer, Integer> c : cambios.entrySet()) {
                    ps.setInt(1, -1_000_000 - c.getKey());
                    ps.setInt(2, c.getKey());
                    ps.executeUpdate();
                }
                for (Map.Entry<Integer, Integer> c : cambios.entrySet()) {
                    ps.setInt(1, c.getValue());
                    ps.setInt(2, -1_000_000 - c.getKey());
                    ps.executeUpdate();
                }
            }
            for (Partida p : ImpOperacionesPartidaBD.leerTodas(local)) {
                Partida cambiada = new Partida(p.getIdPartida(), p.getSemilla(), p.getFecha(),
                        cambios.getOrDefault(p.getIdGanador(), p.getIdGanador()), p.getPuntosGanador(),
                        p.isSospechaTrampa(), renumerarRegistro(p.getLogJugadas(), cambios));
                ImpOperacionesPartidaBD.actualizarFila(local, cambiada);
            }
            List<Object[]> jugadas = new ArrayList<>();
            try (PreparedStatement ps = local.prepareStatement("SELECT id_jugada, id_jugador, accion FROM jugadas_tiempo_real");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    jugadas.add(new Object[]{rs.getString(1), rs.getInt(2), rs.getString(3)});
                }
            }
            try (PreparedStatement ps = local.prepareStatement(
                    "UPDATE jugadas_tiempo_real SET id_jugador = ?, accion = ? WHERE id_jugada = ?")) {
                for (Object[] j : jugadas) {
                    int idJugador = (Integer) j[1];
                    ps.setInt(1, cambios.getOrDefault(idJugador, idJugador));
                    ps.setString(2, renumerarRegistro((String) j[2], cambios));
                    ps.setString(3, (String) j[0]);
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement borrar = local.prepareStatement(
                    "DELETE FROM sincronizacion_pendiente WHERE entidad = ?")) {
                // Los perfiles pendientes se vuelven a crear abajo con su id nuevo.
                borrar.setString(1, ImpOperacionesJugadorBD.ENTIDAD);
                borrar.executeUpdate();
            } catch (SQLException e) {
                // Sin tabla de pendientes (modo solo local): nada que limpiar.
            }
            local.commit();
        } catch (SQLException e) {
            local.rollback();
            throw e;
        } finally {
            local.setAutoCommit(autoCommitPrevio);
        }
    }

    /**
     * Cambia los ids de jugador dentro de un registro (ACCION;idJugador;dato), incluida la cabecera
     * JUGADORES y el acusado de una DENUNCIA. El orden y las cartas no cambian, así que la auditoría
     * con la semilla sigue dando el mismo resultado.
     */
    static String renumerarRegistro(String registro, Map<Integer, Integer> cambios) {
        if (registro == null || registro.isEmpty()) {
            return registro;
        }
        String[] lineas = registro.split("\n", -1);
        for (int i = 0; i < lineas.length; i++) {
            String[] p = lineas[i].split(";", -1);
            if (p.length != 3) {
                continue;
            }
            if (p[0].equals("JUGADORES")) {
                String[] ids = p[2].split(",");
                for (int k = 0; k < ids.length; k++) {
                    ids[k] = mapear(ids[k], cambios);
                }
                p[2] = String.join(",", ids);
            } else {
                p[1] = mapear(p[1], cambios);
                if (p[0].equals("DENUNCIA")) {
                    p[2] = mapear(p[2], cambios);
                }
            }
            lineas[i] = String.join(";", p);
        }
        return String.join("\n", lineas);
    }

    private static String mapear(String id, Map<Integer, Integer> cambios) {
        try {
            int valor = Integer.parseInt(id.trim());
            return String.valueOf(cambios.getOrDefault(valor, valor));
        } catch (NumberFormatException e) {
            return id;
        }
    }

    // ------------------------------------------------------------------ 2. partidas y jugadas

    private int subirPartidasFaltantes(Connection local, Connection nube) throws SQLException {
        Set<String> enNube = ImpOperacionesPartidaBD.idsPartidas(nube);
        int subidas = 0;
        for (Partida p : ImpOperacionesPartidaBD.leerTodas(local)) {
            if (!enNube.contains(p.getIdPartida())) {
                ImpOperacionesPartidaBD.subirPartida(local, nube, p);
                subidas++;
            }
        }
        return subidas;
    }

    private int subirJugadasFaltantes(Connection local, Connection nube) throws SQLException {
        Set<String> enNube = ImpOperacionesPartidaBD.idsJugadas(nube);
        int subidas = 0;
        for (String id : ImpOperacionesPartidaBD.idsJugadas(local)) {
            if (!enNube.contains(id)) {
                ImpOperacionesPartidaBD.replicarJugada(local, nube, id);
                subidas++;
            }
        }
        return subidas;
    }

    // ------------------------------------------------------------------ 3. ranking global

    /** Copia a H2 los perfiles de Neon con sus estadísticas globales. */
    private void bajarPerfiles(Connection local, Connection nube) throws SQLException {
        for (Jugador j : ImpOperacionesJugadorBD.leerTodos(nube)) {
            ImpOperacionesJugadorBD.guardarCompleto(local, j);
        }
    }
}
