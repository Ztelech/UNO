package co.edu.unilibre.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class JugadorTest {

    private Jugador jugador;

    @BeforeEach
    public void setUp() {
        this.jugador = new Jugador(1, "Jeff", false, new ArrayList<>(), false, 0, 0, 0);
        this.jugador.agregarCarta(new Carta(1, ColorCarta.ROJO, TipoCarta.NUMERO, 5, 5));
        this.jugador.agregarCarta(new Carta(2, ColorCarta.AZUL, TipoCarta.SALTO, -1, 20));
        this.jugador.agregarCarta(new Carta(3, ColorCarta.NEGRO, TipoCarta.COMODIN_TOMA_CUATRO, -1, 50));
    }

    @Test
    @DisplayName("RQ19: Los puntos de la mano suman el valor de cada carta")
    public void testCalcularPuntosMano() {
        assertEquals(75, this.jugador.calcularPuntosMano());
    }

    @Test
    @DisplayName("tieneColor detecta si hay cartas de un color en la mano")
    public void testTieneColor() {
        assertTrue(this.jugador.tieneColor(ColorCarta.AZUL));
        assertFalse(this.jugador.tieneColor(ColorCarta.VERDE));
    }

    @Test
    @DisplayName("jugarCarta saca la carta indicada de la mano")
    public void testJugarCartaLaSacaDeLaMano() {
        Carta jugada = this.jugador.jugarCarta(1);
        assertEquals(2, jugada.getId());
        assertEquals(2, this.jugador.getMano().size());
        assertFalse(this.jugador.tieneColor(ColorCarta.AZUL));
    }

    @Test
    @DisplayName("Los DTO son Serializable: un Jugador sobrevive a la serialización binaria")
    public void testJugadorEsSerializable() throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(salida)) {
            oos.writeObject(this.jugador);
        }
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(salida.toByteArray()))) {
            Jugador copia = (Jugador) ois.readObject();
            assertEquals("Jeff", copia.getNombre());
            assertEquals(3, copia.getMano().size());
        }
    }

    @Test
    @DisplayName("Un jugador nuevo nunca tiene la mano en null")
    public void testManoNuncaNull() {
        assertNotNull(new Jugador().getMano());
        assertNotNull(new Jugador(1, "A", false, null, false, 0, 0, 0).getMano());
    }
}
