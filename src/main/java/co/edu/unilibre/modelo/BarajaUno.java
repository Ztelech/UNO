package co.edu.unilibre.modelo;

import co.edu.unilibre.dto.Carta;
import co.edu.unilibre.dto.ColorCarta;
import co.edu.unilibre.dto.TipoCarta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Mazo de robo. Todo el azar sale de un único Random(semilla), así que la misma semilla
 * produce siempre el mismo orden, incluso al reciclar el descarte.
 */
public class BarajaUno {

    private static final ColorCarta[] COLORES = {ColorCarta.ROJO, ColorCarta.AZUL, ColorCarta.VERDE, ColorCarta.AMARILLO};
    private static final TipoCarta[] ACCIONES = {TipoCarta.SALTO, TipoCarta.REVERSA, TipoCarta.TOMA_DOS};

    private final List<Carta> cartas = new ArrayList<>();
    private final Random random;
    private final long semilla;

    public BarajaUno(long semilla) {
        this.semilla = semilla;
        this.random = new Random(semilla);
    }

    /** RF02: construye las 108 cartas reglamentarias con ids 1..108 en un orden fijo. */
    public void generar108Cartas() {
        this.cartas.clear();
        int id = 1;
        for (ColorCarta color : COLORES) {
            this.cartas.add(new Carta(id++, color, TipoCarta.NUMERO, 0, 0));
            for (int numero = 1; numero <= 9; numero++) {
                for (int copia = 0; copia < 2; copia++) {
                    this.cartas.add(new Carta(id++, color, TipoCarta.NUMERO, numero, numero));
                }
            }
            for (TipoCarta accion : ACCIONES) {
                for (int copia = 0; copia < 2; copia++) {
                    this.cartas.add(new Carta(id++, color, accion, -1, 20));
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            this.cartas.add(new Carta(id++, ColorCarta.NEGRO, TipoCarta.COMODIN_COLOR, -1, 50));
        }
        for (int i = 0; i < 4; i++) {
            this.cartas.add(new Carta(id++, ColorCarta.NEGRO, TipoCarta.COMODIN_TOMA_CUATRO, -1, 50));
        }
    }

    public void barajar() {
        Collections.shuffle(this.cartas, this.random);
    }

    /** Saca la carta superior del mazo. */
    public Carta robar() {
        if (this.cartas.isEmpty()) {
            throw new IllegalStateException("El mazo de robo está vacío.");
        }
        return this.cartas.remove(0);
    }

    /** RF04: devuelve una carta al fondo del mazo (p. ej. un +4 que salió como carta inicial). */
    public void devolverAlMazo(Carta carta) {
        this.cartas.add(carta);
    }

    /** Cuando el mazo se agota, las cartas del descarte (menos la superior) vuelven barajadas. */
    public void reciclarDescarte(List<Carta> descartes) {
        this.cartas.addAll(descartes);
        Collections.shuffle(this.cartas, this.random);
    }

    /** Cartas que quedan en el mazo, de arriba hacia abajo (solo lectura). */
    public List<Carta> obtenerCartasRestantes() {
        return Collections.unmodifiableList(this.cartas);
    }

    public int cartasRestantes() {
        return this.cartas.size();
    }

    public long getSemilla() {
        return this.semilla;
    }
}
