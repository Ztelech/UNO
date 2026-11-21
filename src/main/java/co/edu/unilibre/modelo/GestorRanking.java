package co.edu.unilibre.modelo;

import co.edu.unilibre.dto.Jugador;
import co.edu.unilibre.impOperaciones.ImpOperacionesJugadorBD;

import java.util.List;
import java.util.Locale;

/** Perfiles de jugador y ranking histórico (RF20-RF23), a través del DAO de jugadores. */
public class GestorRanking {

    public static final int LONGITUD_MAXIMA_NOMBRE = 100;

    private final ImpOperacionesJugadorBD daoJugadores;
    /** Indica si Neon respondió hace poco (para no esperar a la red cuando no hay internet). */
    private final java.util.function.BooleanSupplier disponibilidadNube;

    /** Ranking solo con la base principal. */
    public GestorRanking(ImpOperacionesJugadorBD daoJugadores) {
        this(daoJugadores, () -> false);
    }

    /** Ranking que consulta Neon (perfiles y Top 10 globales) cuando está disponible. */
    public GestorRanking(ImpOperacionesJugadorBD daoJugadores, java.util.function.BooleanSupplier disponibilidadNube) {
        this.daoJugadores = daoJugadores;
        this.disponibilidadNube = disponibilidadNube;
    }

    /**
     * RF20: devuelve el perfil con ese nombre o lo crea si no existe. El nombre es único
     * (se comparan los nombres sin espacios al inicio ni al final).
     */
    public Jugador registrarOSeleccionarPerfil(String nombre) {
        String limpio = nombre == null ? "" : nombre.strip();
        if (limpio.isEmpty()) {
            throw new IllegalArgumentException("El nombre del jugador no puede estar vacío.");
        }
        if (limpio.length() > LONGITUD_MAXIMA_NOMBRE) {
            throw new IllegalArgumentException("El nombre no puede tener más de " + LONGITUD_MAXIMA_NOMBRE + " caracteres.");
        }
        Jugador existente = this.daoJugadores.buscarPorNombre(limpio);
        if (existente != null) {
            return existente;
        }
        int siguienteId = this.daoJugadores.obtenerSiguienteId();
        if (this.disponibilidadNube.getAsBoolean()) {
            // Con Neon disponible, el perfil puede venir de otro computador: se usa el mismo id.
            try {
                Jugador deOtroComputador = this.daoJugadores.buscarPorNombreEnNube(limpio);
                if (deOtroComputador != null) {
                    this.daoJugadores.guardarSoloLocal(deOtroComputador);
                    return deOtroComputador;
                }
                siguienteId = Math.max(siguienteId, this.daoJugadores.maximoIdEnNube() + 1);
            } catch (java.sql.SQLException e) {
                // Sin conexión: se crea solo aquí; al sincronizar se le asigna un id libre en Neon.
            }
        }
        Jugador nuevo = new Jugador(siguienteId, limpio);
        this.daoJugadores.crear(nuevo);
        return nuevo;
    }

    /**
     * RF21 y RF22: todos los participantes suman una partida jugada; el ganador suma la victoria
     * y los puntos. Parte de los valores guardados en la base y registra a quien no exista.
     */
    public void procesarResultadosPartida(Jugador ganador, int puntosNuevos, List<Jugador> todosLosJugadores) {
        for (Jugador jugador : todosLosJugadores) {
            Jugador guardado = this.daoJugadores.buscarPorId(jugador.getId());
            if (guardado != null) {
                jugador.setPuntosAcumulados(guardado.getPuntosAcumulados());
                jugador.setPartidasJugadas(guardado.getPartidasJugadas());
                jugador.setPartidasGanadas(guardado.getPartidasGanadas());
            }
            jugador.setPartidasJugadas(jugador.getPartidasJugadas() + 1);
            if (ganador != null && jugador.getId() == ganador.getId()) {
                jugador.setPartidasGanadas(jugador.getPartidasGanadas() + 1);
                jugador.setPuntosAcumulados(jugador.getPuntosAcumulados() + puntosNuevos);
            }
            if (guardado == null) {
                this.daoJugadores.crear(jugador);
            } else {
                this.daoJugadores.actualizar(jugador);
            }
        }
    }

    /** RF23: Top 10 histórico ordenado por puntos acumulados. */
    public List<Jugador> generarTablaPosicionesTop10() {
        if (this.disponibilidadNube.getAsBoolean()) {
            try {
                return this.daoJugadores.obtenerRankingDeNube(); // ranking de todos los computadores
            } catch (java.sql.SQLException e) {
                // Sin conexión: se muestra el ranking guardado en este computador.
            }
        }
        return this.daoJugadores.obtenerRankingOrdenadoPorPuntos();
    }

    public String obtenerEstadisticasJugador(int idJugador) {
        Jugador j = this.daoJugadores.buscarPorId(idJugador);
        if (j == null) {
            return "Jugador no encontrado";
        }
        double efectividad = j.getPartidasJugadas() == 0 ? 0 : 100.0 * j.getPartidasGanadas() / j.getPartidasJugadas();
        return String.format(Locale.ROOT, "%s: %d puntos, %d partidas jugadas, %d ganadas (%.1f %%)",
                j.getNombre(), j.getPuntosAcumulados(), j.getPartidasJugadas(), j.getPartidasGanadas(), efectividad);
    }
}
