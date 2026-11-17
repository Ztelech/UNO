package co.edu.unilibre.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Jugador humano de la partida. La mano vive solo en memoria; en la base se guardan sus estadísticas. */
public class Jugador implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private String nombre;
    private List<Carta> mano = new ArrayList<>();
    private boolean dijoUno;
    private int puntosAcumulados;
    private int partidasJugadas;
    private int partidasGanadas;

    public Jugador() {
    }

    public Jugador(int id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }

    public Jugador(int id, String nombre, List<Carta> mano, boolean dijoUno,
                   int puntosAcumulados, int partidasJugadas, int partidasGanadas) {
        this.id = id;
        this.nombre = nombre;
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

    /** RF10: indica si alguna carta de la mano se puede jugar sobre el descarte. */
    public boolean tieneCartaJugable(Carta cartaDescarte, ColorCarta colorActivo) {
        return this.mano.stream().anyMatch(c -> c.esJugableSobre(cartaDescarte, colorActivo));
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
        return "Jugador{id=" + id + ", nombre='" + nombre + "', cartasEnMano=" + mano.size()
                + ", dijoUno=" + dijoUno + ", puntosAcumulados=" + puntosAcumulados
                + ", partidasJugadas=" + partidasJugadas + ", partidasGanadas=" + partidasGanadas + "}";
    }
}
