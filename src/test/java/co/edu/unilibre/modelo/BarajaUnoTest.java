package co.edu.unilibre.modelo;

import co.edu.unilibre.dto.Carta;
import co.edu.unilibre.dto.ColorCarta;
import co.edu.unilibre.dto.TipoCarta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class BarajaUnoTest {

    private static final long SEMILLA_PRUEBA = 987654321L;
    private static final ColorCarta[] COLORES = {ColorCarta.ROJO, ColorCarta.AZUL, ColorCarta.VERDE, ColorCarta.AMARILLO};

    private BarajaUno baraja;

    @BeforeEach
    public void setUp() {
        this.baraja = new BarajaUno(SEMILLA_PRUEBA);
        this.baraja.generar108Cartas();
    }

    private long contar(ColorCarta color, TipoCarta tipo) {
        return this.baraja.getCartas().stream()
                .filter(c -> c.getColor() == color && c.getTipo() == tipo)
                .count();
    }

    private static List<Integer> ordenTrasBarajar(long semilla) {
        BarajaUno b = new BarajaUno(semilla);
        b.generar108Cartas();
        b.barajar();
        List<Integer> orden = new ArrayList<>();
        while (b.cartasRestantes() > 0) {
            orden.add(b.robar().getId());
        }
        return orden;
    }

    @Test
    @DisplayName("RQ02: La baraja tiene exactamente 108 cartas")
    public void testGenera108Cartas() {
        assertEquals(108, this.baraja.getCartas().size());
        assertEquals(108, this.baraja.cartasRestantes());
    }

    @Test
    @DisplayName("RQ02: Cada color tiene un 0, dos de cada número del 1 al 9 y dos de cada especial")
    public void testComposicionPorColor() {
        for (ColorCarta color : COLORES) {
            for (int n = 0; n <= 9; n++) {
                final int numero = n;
                long cantidad = this.baraja.getCartas().stream()
                        .filter(c -> c.getColor() == color && c.getTipo() == TipoCarta.NUMERO && c.getNumero() == numero)
                        .count();
                assertEquals(numero == 0 ? 1 : 2, cantidad, "Cantidad de " + color + " " + numero);
            }
            assertEquals(2, contar(color, TipoCarta.SALTO), "Saltos " + color);
            assertEquals(2, contar(color, TipoCarta.REVERSA), "Reversas " + color);
            assertEquals(2, contar(color, TipoCarta.TOMA_DOS), "Toma dos " + color);
        }
    }

    @Test
    @DisplayName("RQ02: Hay 4 comodines de color y 4 comodines +4, todos de color NEGRO")
    public void testComodines() {
        assertEquals(4, contar(ColorCarta.NEGRO, TipoCarta.COMODIN_COLOR));
        assertEquals(4, contar(ColorCarta.NEGRO, TipoCarta.COMODIN_TOMA_CUATRO));
        assertEquals(8, this.baraja.getCartas().stream().filter(c -> c.getColor() == ColorCarta.NEGRO).count());
    }

    @Test
    @DisplayName("RQ19: Valor en puntos: números su cara, acción 20, comodines 50 (total 1240)")
    public void testValorPuntos() {
        for (Carta c : this.baraja.getCartas()) {
            switch (c.getTipo()) {
                case NUMERO -> assertEquals(c.getNumero(), c.getValorPuntos(), c.toString());
                case SALTO, REVERSA, TOMA_DOS -> {
                    assertEquals(20, c.getValorPuntos(), c.toString());
                    assertEquals(-1, c.getNumero(), c.toString());
                }
                case COMODIN_COLOR, COMODIN_TOMA_CUATRO -> {
                    assertEquals(50, c.getValorPuntos(), c.toString());
                    assertEquals(-1, c.getNumero(), c.toString());
                }
            }
        }
        assertEquals(1240, this.baraja.getCartas().stream().mapToInt(Carta::getValorPuntos).sum());
    }

    @Test
    @DisplayName("Auditoría: Cada carta tiene un id único del 1 al 108")
    public void testIdsUnicos() {
        Set<Integer> ids = new HashSet<>();
        for (Carta c : this.baraja.getCartas()) {
            assertTrue(c.getId() >= 1 && c.getId() <= 108, "Id fuera de rango: " + c.getId());
            ids.add(c.getId());
        }
        assertEquals(108, ids.size());
    }

    @Test
    @DisplayName("Determinismo: Dos barajas con la misma semilla generan exactamente la misma secuencia")
    public void testSemillasDeterministasIdenticas() {
        BarajaUno baraja1 = new BarajaUno(SEMILLA_PRUEBA);
        BarajaUno baraja2 = new BarajaUno(SEMILLA_PRUEBA);

        baraja1.generar108Cartas();
        baraja2.generar108Cartas();

        baraja1.barajar();
        baraja2.barajar();

        for (int i = 0; i < 108; i++) {
            Carta c1 = baraja1.robar();
            Carta c2 = baraja2.robar();
            assertEquals(c1.getId(), c2.getId(), "El id en la posicion " + i + " debe ser identico.");
            assertEquals(c1.getColor(), c2.getColor(), "El color en la posicion " + i + " debe ser identico.");
            assertEquals(c1.getTipo(), c2.getTipo(), "El tipo en la posicion " + i + " debe ser identico.");
            assertEquals(c1.getNumero(), c2.getNumero(), "El numero en la posicion " + i + " debe ser identico.");
        }
    }

    @Test
    @DisplayName("Determinismo: Semillas distintas producen un orden distinto")
    public void testSemillasDistintasDifieren() {
        assertNotEquals(ordenTrasBarajar(SEMILLA_PRUEBA), ordenTrasBarajar(SEMILLA_PRUEBA + 1));
    }

    @Test
    @DisplayName("Determinismo: Recargar el mazo con el descarte también es reproducible")
    public void testRecargaDeterminista() {
        List<List<Integer>> resultados = new ArrayList<>();
        for (int intento = 0; intento < 2; intento++) {
            BarajaUno b = new BarajaUno(SEMILLA_PRUEBA);
            b.generar108Cartas();
            b.barajar();
            List<Carta> descarte = new ArrayList<>();
            while (b.cartasRestantes() > 0) {
                descarte.add(b.robar());
            }
            b.recargarDescarte(descarte);
            List<Integer> orden = new ArrayList<>();
            while (b.cartasRestantes() > 0) {
                orden.add(b.robar().getId());
            }
            resultados.add(orden);
        }
        assertEquals(108, resultados.get(0).size());
        assertEquals(resultados.get(0), resultados.get(1));
    }

    @Test
    @DisplayName("Robar saca una carta y reduce las restantes")
    public void testRobarReduceMazo() {
        this.baraja.barajar();
        assertNotNull(this.baraja.robar());
        assertEquals(107, this.baraja.cartasRestantes());
    }

    @Test
    @DisplayName("Robar de un mazo vacío lanza IllegalStateException")
    public void testRobarMazoVacio() {
        for (int i = 0; i < 108; i++) {
            this.baraja.robar();
        }
        assertThrows(IllegalStateException.class, () -> this.baraja.robar());
    }

    @Test
    @DisplayName("RQ04: colocarAlFondo deja la carta como la última del mazo")
    public void testColocarAlFondo() {
        Carta primera = this.baraja.robar();
        this.baraja.colocarAlFondo(primera);
        List<Carta> cartas = this.baraja.getCartas();
        assertEquals(primera.getId(), cartas.get(cartas.size() - 1).getId());
        assertEquals(108, this.baraja.cartasRestantes());
    }
}
