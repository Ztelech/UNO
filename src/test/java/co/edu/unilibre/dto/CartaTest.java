package co.edu.unilibre.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CartaTest {

    private final Carta azul7 = new Carta(1, ColorCarta.AZUL, TipoCarta.NUMERO, 7, 7);

    @Test
    @DisplayName("RQ08: Se puede jugar una carta del mismo color activo")
    public void testMismoColorEsJugable() {
        Carta azul3 = new Carta(2, ColorCarta.AZUL, TipoCarta.NUMERO, 3, 3);
        assertTrue(azul3.esJugableSobre(this.azul7, ColorCarta.AZUL));
    }

    @Test
    @DisplayName("RQ08: Se puede jugar el mismo número aunque cambie el color")
    public void testMismoNumeroEsJugable() {
        Carta rojo7 = new Carta(2, ColorCarta.ROJO, TipoCarta.NUMERO, 7, 7);
        assertTrue(rojo7.esJugableSobre(this.azul7, ColorCarta.AZUL));
    }

    @Test
    @DisplayName("RQ08: No se puede jugar distinto número y distinto color")
    public void testDistintoNumeroYColorNoEsJugable() {
        Carta rojo5 = new Carta(2, ColorCarta.ROJO, TipoCarta.NUMERO, 5, 5);
        assertFalse(rojo5.esJugableSobre(this.azul7, ColorCarta.AZUL));
    }

    @Test
    @DisplayName("RQ08: Una carta especial se puede jugar sobre otra del mismo tipo")
    public void testMismoTipoEspecialEsJugable() {
        Carta saltoAzul = new Carta(2, ColorCarta.AZUL, TipoCarta.SALTO, -1, 20);
        Carta saltoRojo = new Carta(3, ColorCarta.ROJO, TipoCarta.SALTO, -1, 20);
        assertTrue(saltoRojo.esJugableSobre(saltoAzul, ColorCarta.AZUL));
    }

    @Test
    @DisplayName("RQ08: Una especial de otro color no se puede jugar sobre un número")
    public void testEspecialDeOtroColorSobreNumeroNoEsJugable() {
        Carta tomaDosRojo = new Carta(2, ColorCarta.ROJO, TipoCarta.TOMA_DOS, -1, 20);
        assertFalse(tomaDosRojo.esJugableSobre(this.azul7, ColorCarta.AZUL));
    }

    @Test
    @DisplayName("RQ08: Los comodines (NEGRO) siempre se pueden jugar")
    public void testComodinSiempreEsJugable() {
        Carta comodin = new Carta(2, ColorCarta.NEGRO, TipoCarta.COMODIN_COLOR, -1, 50);
        assertTrue(comodin.esJugableSobre(this.azul7, ColorCarta.AZUL));
    }

    @Test
    @DisplayName("RQ08: Sobre un comodín manda el color activo elegido, no el color NEGRO")
    public void testSobreComodinMandaColorActivo() {
        Carta comodin = new Carta(2, ColorCarta.NEGRO, TipoCarta.COMODIN_COLOR, -1, 50);
        Carta verde4 = new Carta(3, ColorCarta.VERDE, TipoCarta.NUMERO, 4, 4);
        Carta rojo4 = new Carta(4, ColorCarta.ROJO, TipoCarta.NUMERO, 4, 4);
        assertTrue(verde4.esJugableSobre(comodin, ColorCarta.VERDE));
        assertFalse(rojo4.esJugableSobre(comodin, ColorCarta.VERDE));
    }
}
