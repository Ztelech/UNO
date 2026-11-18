package co.edu.unilibre.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Jugador de la partida. La mano vive solo en memoria; en la base se guardan sus estadísticas. */
public class Jugador implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private String nombre;
    private boolean esBot;
    private List<Carta> mano = new ArrayList<>();
    private boolean dijoUno;
    private int puntosAcumulados;
    private int partidasJugadas;
    private int partidasGanadas;

    public Jugador() {
    }

    public Jugador(int id, String nombre, boolean esBot) {
        this.id = id;
        this.nombre = nombre;
        this.esBot = esBot;
    }

    public Jugador(int id, String nombre, boolean esBot, List<Carta> mano, boolean dijoUno,
                   int puntosAcumulados, int partidasJugadas, int partidasGanadas) {
        this.id = id;
        this.nombre = nombre;
        this.esBot = esBot;
        this.mano = mano != null ? mano : new ArrayList<>();
        this.dijoUno = dijoUno;
        this.puntosAcumulados = puntosAcumulados;
        this.partidasJugadas = partidasJugadas;
        this.partidasGanadas = partidasGanadas;
    }

    public void agregarCarta(Carta c) {
        this.mano.add(c);
    }

    public Carta jugarCarta(int indice) {
        return this.mano.remove(indice);
    }

    public boolean tieneColor(ColorCarta color) {
        return this.mano.stream().anyMatch(c -> c.getColor() == color);
    }

    public int calcularPuntosMano() {
        return this.mano.stream().mapToInt(Carta::getValorPuntos).sum();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public boolean isEsBot() {
        return esBot;
    }

    public void setEsBot(boolean esBot) {
        this.esBot = esBot;
    }

    public List<Carta> getMano() {
        return mano;
    }

    public void setMano(List<Carta> mano) {
        this.mano = mano != null ? mano : new ArrayList<>();
    }

    public boolean isDijoUno() {
        return dijoUno;
    }

    public void setDijoUno(boolean dijoUno) {
        this.dijoUno = dijoUno;
    }

    public int getPuntosAcumulados() {
        return puntosAcumulados;
    }

    public void setPuntosAcumulados(int puntosAcumulados) {
        this.puntosAcumulados = puntosAcumulados;
    }

    public int getPartidasJugadas() {
        return partidasJugadas;
    }

    public void setPartidasJugadas(int partidasJugadas) {
        this.partidasJugadas = partidasJugadas;
    }

    public int getPartidasGanadas() {
        return partidasGanadas;
    }

    public void setPartidasGanadas(int partidasGanadas) {
        this.partidasGanadas = partidasGanadas;
    }

    @Override
    public String toString() {
        return "Jugador{id=" + id + ", nombre='" + nombre + "', esBot=" + esBot
                + ", cartasEnMano=" + mano.size() + ", dijoUno=" + dijoUno
                + ", puntosAcumulados=" + puntosAcumulados + ", partidasJugadas=" + partidasJugadas
                + ", partidasGanadas=" + partidasGanadas + "}";
    }
}
