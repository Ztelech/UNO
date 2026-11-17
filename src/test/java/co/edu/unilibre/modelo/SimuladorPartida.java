package co.edu.unilibre.modelo;

import co.edu.unilibre.dto.Carta;
import co.edu.unilibre.dto.ColorCarta;
import co.edu.unilibre.dto.Jugador;
import co.edu.unilibre.dto.TipoCarta;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Utilidades de prueba: partidas honestas automáticas y búsqueda de semillas con cierta carta inicial. */
final class SimuladorPartida {

    private SimuladorPartida() {
    }

    static List<Jugador> tresJugadores() {
        List<Jugador> lista = new ArrayList<>();
        lista.add(new Jugador(1, "Jeff"));
        lista.add(new Jugador(2, "Brenda"));
        lista.add(new Jugador(3, "Camila"));
        return lista;
    }

    /** Primera carta legal del jugador activo (respeta RF09), o null. */
    static Carta primeraJugable(GestorPartidaUno g, Jugador jugador) {
        for (Carta c : jugador.getMano()) {
            boolean masCuatroIlegal = c.getTipo() == TipoCarta.COMODIN_TOMA_CUATRO && !g.validarUsoTomaCuatro(jugador);
            if (!masCuatroIlegal && c.esJugableSobre(g.obtenerCartaSuperiorDescarte(), g.getColorActivo())) {
                return c;
            }
        }
        return null;
    }

    private static ColorCarta colorPara(Carta c) {
        return c.getColor() == ColorCarta.NEGRO ? ColorCarta.ROJO : null;
    }

    /** Juega con la primera carta legal; si no hay, roba y juega la robada si se puede. Siempre canta ¡UNO! */
    static void jugarHastaElFinal(GestorPartidaUno g, int maximoAcciones) {
        for (int paso = 0; paso < maximoAcciones && !g.isPartidaTerminada(); paso++) {
            Jugador actual = g.obtenerJugadorActivo();
            if (g.isColorInicialPendiente()) {
                assertTrue(g.elegirColorInicial(actual, ColorCarta.ROJO));
                continue;
            }
            Carta c = primeraJugable(g, actual);
            if (c == null) {
                Carta robada = g.robarCartaUno(actual);
                if (g.getCartaRecienRobada() != null) {
                    assertTrue(g.jugarCartaRecienRobada(actual, true, colorPara(robada)), "Robada jugable rechazada: " + robada);
                }
            } else {
                assertTrue(g.jugarTurno(actual, c, colorPara(c)), "Jugada legal rechazada: " + c);
            }
            if (actual.getMano().size() == 1) {
                g.cantarUno(actual);
            }
        }
    }

    /** Partida honesta completa: configurar, repartir, descarte y jugar. */
    static GestorPartidaUno partidaHonesta(long semilla, List<Jugador> jugadores) {
        GestorPartidaUno g = new GestorPartidaUno();
        g.configurarPartida(jugadores, semilla);
        g.repartirManosIniciales();
        g.iniciarDescarte();
        jugarHastaElFinal(g, 3_000);
        return g;
    }

    /** Mazo recién barajado con la semilla. */
    static BarajaUno barajaBarajada(long semilla) {
        BarajaUno b = new BarajaUno(semilla);
        b.generar108Cartas();
        b.barajar();
        return b;
    }

    /** Carta en bruto que sale tras repartir a n jugadores (antes de aplicar RF04). */
    static Carta cartaTrasReparto(long semilla, int jugadores) {
        return barajaBarajada(semilla).obtenerCartasRestantes().get(7 * jugadores);
    }

    /** Carta inicial del descarte según RF04 (los +4 vuelven al mazo). */
    static Carta cartaInicial(long semilla, int jugadores) {
        BarajaUno b = barajaBarajada(semilla);
        for (int i = 0; i < 7 * jugadores; i++) {
            b.robar();
        }
        Carta c = b.robar();
        while (c.getTipo() == TipoCarta.COMODIN_TOMA_CUATRO) {
            b.devolverAlMazo(c);
            c = b.robar();
        }
        return c;
    }

    /** Primera semilla (desde 1) cuya carta inicial con 3 jugadores es del tipo pedido. */
    static long semillaConCartaInicial(TipoCarta tipo) {
        for (long s = 1; s < 200_000; s++) {
            if (cartaInicial(s, 3).getTipo() == tipo) {
                return s;
            }
        }
        throw new IllegalStateException("No se encontró semilla con carta inicial " + tipo);
    }

    /** Primera semilla cuya carta tras el reparto (3 jugadores) es un +4 seguido de una carta válida. */
    static long semillaConMasCuatroInicial() {
        for (long s = 1; s < 200_000; s++) {
            List<Carta> cartas = barajaBarajada(s).obtenerCartasRestantes();
            if (cartas.get(21).getTipo() == TipoCarta.COMODIN_TOMA_CUATRO
                    && cartas.get(22).getTipo() == TipoCarta.NUMERO) {
                return s;
            }
        }
        throw new IllegalStateException("No se encontró semilla con +4 inicial");
    }

    /** Primera semilla cuya primera carta del mazo (sin repartir) es un número. */
    static long semillaConPrimeraCartaNumero() {
        for (long s = 1; s < 200_000; s++) {
            if (barajaBarajada(s).obtenerCartasRestantes().get(0).getTipo() == TipoCarta.NUMERO) {
                return s;
            }
        }
        throw new IllegalStateException("No se encontró semilla");
    }
}
