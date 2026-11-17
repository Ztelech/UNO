package co.edu.unilibre.vista;

import co.edu.unilibre.dto.Carta;
import co.edu.unilibre.dto.ColorCarta;
import co.edu.unilibre.modelo.BarajaUno;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Font;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Colores, tipografías y textos comunes de la interfaz (estética playa del prototipo). */
final class TemaUno {

    static final Color MARINO = new Color(0x0077B6);
    static final Color MARINO_OSCURO = new Color(0x03045E);
    static final Color ESPUMA = new Color(0x00B4D8);
    static final Color CIELO_ARRIBA = new Color(0x48B5E8);
    static final Color CIELO_ABAJO = new Color(0xCDEFFA);
    static final Color ARENA = new Color(0xF4E8C1);
    static final Color ARENA_BORDE = new Color(0xE6D29A);
    static final Color ROJO_UNO = new Color(0xD62828);
    static final Color ROJO_SOMBRA = new Color(0x8E1B1B);
    static final Color AMARILLO_BOTON = new Color(0xFFB703);
    static final Color TEXTO = MARINO_OSCURO;
    static final Color TEXTO_SUAVE = new Color(0x4A5A70);
    static final Color VERDE_OK = new Color(0x17643A);
    static final Color FONDO_OK = new Color(0xE7F7EE);
    static final Color FONDO_ALERTA = new Color(0xFDE2E2);
    static final Color TEXTO_ALERTA = new Color(0x7A1414);
    static final Color BLANCO = Color.WHITE;

    /** Todas las cartas por id, para describir el registro de jugadas. */
    private static final Map<Integer, Carta> CARTAS_POR_ID;

    static {
        BarajaUno referencia = new BarajaUno(0);
        referencia.generar108Cartas();
        Map<Integer, Carta> mapa = new HashMap<>();
        for (Carta c : referencia.obtenerCartasRestantes()) {
            mapa.put(c.getId(), c);
        }
        CARTAS_POR_ID = Collections.unmodifiableMap(mapa);
    }

    private TemaUno() {
    }

    static Color colorDe(ColorCarta color) {
        if (color == null) {
            return new Color(0x1B1B1B);
        }
        return switch (color) {
            case ROJO -> new Color(0xD62828);
            case AZUL -> new Color(0x0077B6);
            case VERDE -> new Color(0x2A9D5F);
            case AMARILLO -> new Color(0xF4B400);
            case NEGRO -> new Color(0x1B1B1B);
        };
    }

    /** Color de texto legible sobre el color de la carta. */
    static Color textoSobre(ColorCarta color) {
        return color == ColorCarta.AMARILLO ? new Color(0x3D2A00) : BLANCO;
    }

    static String nombreColor(ColorCarta color) {
        if (color == null) {
            return "sin elegir";
        }
        return switch (color) {
            case ROJO -> "Rojo";
            case AZUL -> "Azul";
            case VERDE -> "Verde";
            case AMARILLO -> "Amarillo";
            case NEGRO -> "Negro";
        };
    }

    /** Nombre corto de la carta, p. ej. "Azul 7", "Salto rojo", "Comodín +4". */
    static String describir(Carta c) {
        if (c == null) {
            return "—";
        }
        String color = nombreColor(c.getColor()).toLowerCase();
        return switch (c.getTipo()) {
            case NUMERO -> nombreColor(c.getColor()) + " " + c.getNumero();
            case SALTO -> "Salto " + color;
            case REVERSA -> "Reversa " + color;
            case TOMA_DOS -> "+2 " + color;
            case COMODIN_COLOR -> "Comodín";
            case COMODIN_TOMA_CUATRO -> "Comodín +4";
        };
    }

    static String describir(int idCarta) {
        Carta c = CARTAS_POR_ID.get(idCarta);
        return c == null ? "carta " + idCarta : describir(c);
    }

    /** Traduce una línea del registro (ACCION;idJugador;dato) a una frase. */
    static String lineaLegible(String linea, Map<Integer, String> nombres) {
        String[] p = linea.split(";");
        if (p.length != 3) {
            return linea;
        }
        String quien = nombres.getOrDefault(parsear(p[1]), "Jugador " + p[1]);
        return switch (p[0]) {
            case "JUGADORES" -> "Jugadores en la mesa: " + p[2].split(",").length;
            case "REPARTO" -> "Se reparten " + p[2] + " cartas a cada jugador";
            case "DESCARTE" -> "Carta inicial: " + describir(parsear(p[2]));
            case "JUGAR" -> quien + " jugó " + describir(parsear(p[2]));
            case "ROBAR" -> quien + " robó una carta";
            case "COLOR" -> quien + " eligió " + p[2].toLowerCase();
            case "UNO" -> quien + " cantó ¡UNO!";
            case "PASAR" -> quien + " pasó el turno";
            case "DENUNCIA" -> quien + " denunció a " + nombres.getOrDefault(parsear(p[2]), "otro jugador");
            default -> linea;
        };
    }

    private static int parsear(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static Font titulo(float tamano) {
        return new Font(Font.SANS_SERIF, Font.BOLD | Font.ITALIC, Math.round(tamano));
    }

    static Font texto(float tamano, int estilo) {
        return new Font(Font.SANS_SERIF, estilo, Math.round(tamano));
    }

    static Font mono(float tamano) {
        return new Font(Font.MONOSPACED, Font.BOLD, Math.round(tamano));
    }

    static JLabel etiqueta(String texto, float tamano, int estilo, Color color) {
        JLabel l = new JLabel(texto);
        l.setFont(texto(tamano, estilo));
        l.setForeground(color);
        return l;
    }

    static JLabel tituloSeccion(String texto) {
        JLabel l = etiqueta(texto, 22, Font.BOLD, TEXTO);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        return l;
    }

    static JLabel centrado(JLabel l) {
        l.setHorizontalAlignment(SwingConstants.CENTER);
        l.setAlignmentX(0.5f);
        return l;
    }

    static BotonRedondeado botonPrincipal(String texto) {
        BotonRedondeado b = new BotonRedondeado(texto, ROJO_UNO, BLANCO);
        b.setFont(titulo(22));
        return b;
    }

    static BotonRedondeado botonAzul(String texto) {
        return new BotonRedondeado(texto, MARINO, BLANCO);
    }

    static BotonRedondeado botonClaro(String texto) {
        return new BotonRedondeado(texto, BLANCO, TEXTO);
    }
}
