package co.edu.unilibre.dto;

import java.io.Serializable;
import java.time.LocalDate;

/** Partida cerrada: lo único que se guarda al terminar (persistencia en frío). */
public class Partida implements Serializable {

    private static final long serialVersionUID = 1L;

    private String idPartida;
    private long semilla;
    private LocalDate fecha;
    private int idGanador;
    private int puntosGanador;
    private boolean sospechaTrampa;
    /** Una acción por línea con el formato ACCION;idJugador;dato. */
    private String logJugadas;

    public Partida() {
    }

    public Partida(String idPartida, long semilla, LocalDate fecha, int idGanador, int puntosGanador,
                   boolean sospechaTrampa, String logJugadas) {
        this.idPartida = idPartida;
        this.semilla = semilla;
        this.fecha = fecha;
        this.idGanador = idGanador;
        this.puntosGanador = puntosGanador;
        this.sospechaTrampa = sospechaTrampa;
        this.logJugadas = logJugadas;
    }

    public String getIdPartida() {
        return idPartida;
    }

    public void setIdPartida(String idPartida) {
        this.idPartida = idPartida;
    }

    public long getSemilla() {
        return semilla;
    }

    public void setSemilla(long semilla) {
        this.semilla = semilla;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public int getIdGanador() {
        return idGanador;
    }

    public void setIdGanador(int idGanador) {
        this.idGanador = idGanador;
    }

    public int getPuntosGanador() {
        return puntosGanador;
    }

    public void setPuntosGanador(int puntosGanador) {
        this.puntosGanador = puntosGanador;
    }

    public boolean isSospechaTrampa() {
        return sospechaTrampa;
    }

    public void setSospechaTrampa(boolean sospechaTrampa) {
        this.sospechaTrampa = sospechaTrampa;
    }

    public String getLogJugadas() {
        return logJugadas;
    }

    public void setLogJugadas(String logJugadas) {
        this.logJugadas = logJugadas;
    }

    @Override
    public String toString() {
        return "Partida{idPartida='" + idPartida + "', semilla=" + semilla + ", fecha=" + fecha
                + ", idGanador=" + idGanador + ", puntosGanador=" + puntosGanador
                + ", sospechaTrampa=" + sospechaTrampa + "}";
    }
}
