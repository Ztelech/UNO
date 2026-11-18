package co.edu.unilibre.modelo;

import co.edu.unilibre.dto.Jugador;
import co.edu.unilibre.impOperaciones.ImpOperacionesJugadorBD;

import java.util.List;
import java.util.Locale;

/** Procesa el fin de ronda y mantiene el ranking histórico a través del DAO de jugadores. */
public class GestorRanking {

    private final ImpOperacionesJugadorBD daoJugadores;

    public GestorRanking(ImpOperacionesJugadorBD daoJugadores) {
        this.daoJugadores = daoJugadores;
    }

    /**
     * Todos los participantes suman una partida jugada; el ganador suma la victoria y los puntos.
     * Parte de los valores guardados en la base y registra a quien todavía no exista.
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

    /** Top 10 histórico ordenado por puntos acumulados. */
    public List<Jugador> generarTablaPosicionesTop10() {
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
