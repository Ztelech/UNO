package co.edu.unilibre.vista;

import co.edu.unilibre.dto.Carta;
import co.edu.unilibre.dto.ColorCarta;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;

/**
 * Carta dibujada con Java2D. Es un botón (se puede usar con teclado y lector de pantalla).
 * Con carta null se dibuja el dorso.
 */
class ComponenteCarta extends JButton {

    static final int ANCHO = 84;
    static final int ALTO = 126;
    private static final int ELEVACION = 16;

    private final Carta carta;
    private final float escala;
    private boolean jugable;
    private boolean atenuada;
    private boolean bloqueada;
    private boolean nueva;

    ComponenteCarta(Carta carta, float escala) {
        this.carta = carta;
        this.escala = escala;
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(true);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder());
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        String nombre = carta == null ? "Mazo de robo" : TemaUno.describir(carta);
        setToolTipText(nombre);
        getAccessibleContext().setAccessibleName(nombre);
        Dimension d = new Dimension(Math.round(ANCHO * escala), Math.round((ALTO + ELEVACION) * escala));
        setPreferredSize(d);
        setMinimumSize(d);
        setMaximumSize(d);
    }

    ComponenteCarta(Carta carta) {
        this(carta, 1f);
    }

    Carta getCarta() {
        return this.carta;
    }

    /** Las jugables se elevan; las demás se atenúan; el +4 ilegal lleva candado (RF09). */
    void marcar(boolean jugable, boolean atenuada, boolean bloqueada) {
        this.jugable = jugable;
        this.atenuada = atenuada;
        this.bloqueada = bloqueada;
        repaint();
    }

    /** Señala la carta que el jugador acaba de robar. */
    void marcarNueva() {
        this.nueva = true;
        getAccessibleContext().setAccessibleName(TemaUno.describir(this.carta) + " (recién robada)");
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.scale(this.escala, this.escala);
        int y = this.jugable ? 0 : ELEVACION;
        if (this.atenuada) {
            g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.55f));
        }
        pintarCarta(g2, 0, y);
        if (this.bloqueada) {
            pintarCandado(g2, ANCHO - 26, y + 2);
        }
        if (this.nueva) {
            g2.setComposite(java.awt.AlphaComposite.SrcOver);
            g2.setColor(TemaUno.AMARILLO_BOTON);
            g2.fillRoundRect(6, y + ALTO - 30, ANCHO - 14, 20, 10, 10);
            g2.setColor(TemaUno.MARINO_OSCURO);
            g2.setFont(TemaUno.texto(11, java.awt.Font.BOLD));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString("NUEVA", (ANCHO - 2 - fm.stringWidth("NUEVA")) / 2, y + ALTO - 16);
            g2.setStroke(new BasicStroke(3f));
            g2.drawRoundRect(1, y + 1, ANCHO - 4, ALTO - 4, 14, 14);
        }
        if (hasFocus()) {
            g2.setComposite(java.awt.AlphaComposite.SrcOver);
            g2.setColor(TemaUno.AMARILLO_BOTON);
            g2.setStroke(new BasicStroke(3f));
            g2.drawRoundRect(1, y + 1, ANCHO - 3, ALTO - 3, 16, 16);
        }
        g2.dispose();
    }

    private void pintarCarta(Graphics2D g2, int x, int y) {
        g2.setColor(new Color(0, 0, 0, 60));
        g2.fillRoundRect(x + 2, y + 4, ANCHO - 2, ALTO - 2, 14, 14);
        g2.setColor(Color.WHITE);
        g2.fillRoundRect(x, y, ANCHO - 2, ALTO - 2, 14, 14);

        int borde = 5;
        int iw = ANCHO - 2 - borde * 2;
        int ih = ALTO - 2 - borde * 2;
        ColorCarta color = this.carta == null ? ColorCarta.NEGRO : this.carta.getColor();
        g2.setColor(TemaUno.colorDe(color));
        g2.fillRoundRect(x + borde, y + borde, iw, ih, 10, 10);

        // Óvalo central inclinado
        Shape ovalo = AffineTransform.getRotateInstance(Math.toRadians(25), x + ANCHO / 2.0 - 1, y + ALTO / 2.0 - 1)
                .createTransformedShape(new Ellipse2D.Double(x + 13, y + 17, ANCHO - 28, ALTO - 36));
        if (this.carta == null) {
            g2.setColor(TemaUno.ROJO_UNO);
            g2.fill(ovalo);
            textoCentrado(g2, "UNO", x, y, TemaUno.titulo(20), new Color(0xFFD166));
            return;
        }
        if (color == ColorCarta.NEGRO) {
            pintarOvaloComodin(g2, ovalo);
        } else {
            g2.setColor(Color.WHITE);
            g2.fill(ovalo);
        }

        String etiqueta = etiqueta();
        String esquina = etiquetaEsquina();
        Color colorCentro = color == ColorCarta.NEGRO ? Color.WHITE
                : (color == ColorCarta.AMARILLO ? new Color(0x9A6B00) : TemaUno.colorDe(color));
        Font fuente = TemaUno.titulo(etiqueta.length() > 1 ? 30 : 42);
        if (color == ColorCarta.NEGRO) {
            textoCentrado(g2, etiqueta, x + 1, y + 2, fuente, new Color(0, 0, 0, 160));
        }
        textoCentrado(g2, etiqueta, x, y, fuente, colorCentro);
        if (etiqueta.equals("6") || etiqueta.equals("9")) {
            // Como en las cartas reales: el 6 y el 9 llevan subrayado para no confundirlos.
            FontMetrics fm = g2.getFontMetrics(fuente);
            int ancho = fm.stringWidth(etiqueta);
            g2.setColor(colorCentro);
            g2.fillRect(x + (ANCHO - 2 - ancho) / 2, y + (ALTO - 2 + fm.getAscent() - fm.getDescent()) / 2 + 4, ancho, 3);
        }

        g2.setFont(TemaUno.titulo(15));
        g2.setColor(color == ColorCarta.NEGRO ? Color.WHITE : TemaUno.textoSobre(color));
        g2.drawString(esquina, x + borde + 4, y + borde + 16);
        FontMetrics fm = g2.getFontMetrics();
        AffineTransform previa = g2.getTransform();
        g2.rotate(Math.PI, x + ANCHO - borde - 6 - fm.stringWidth(esquina) / 2.0, y + ALTO - borde - 12);
        g2.drawString(esquina, x + ANCHO - borde - 6 - fm.stringWidth(esquina), y + ALTO - borde - 7);
        g2.setTransform(previa);
    }

    private static void pintarOvaloComodin(Graphics2D g2, Shape ovalo) {
        java.awt.Rectangle r = ovalo.getBounds();
        Color[] colores = {TemaUno.colorDe(ColorCarta.ROJO), TemaUno.colorDe(ColorCarta.AZUL),
                TemaUno.colorDe(ColorCarta.AMARILLO), TemaUno.colorDe(ColorCarta.VERDE)};
        Shape clipPrevio = g2.getClip();
        g2.clip(ovalo);
        for (int i = 0; i < 4; i++) {
            g2.setColor(colores[i]);
            g2.fill(new Arc2D.Double(r.x - 10, r.y - 10, r.width + 20, r.height + 20, 90 * i, 90, Arc2D.PIE));
        }
        g2.setClip(clipPrevio);
    }

    private String etiqueta() {
        return switch (this.carta.getTipo()) {
            case NUMERO -> String.valueOf(this.carta.getNumero());
            case SALTO -> "Ø";
            case REVERSA -> "↔";
            case TOMA_DOS -> "+2";
            case COMODIN_COLOR -> "";
            case COMODIN_TOMA_CUATRO -> "+4";
        };
    }

    private String etiquetaEsquina() {
        return switch (this.carta.getTipo()) {
            case COMODIN_COLOR -> "C";
            default -> etiqueta();
        };
    }

    private static void textoCentrado(Graphics2D g2, String texto, int x, int y, Font fuente, Color color) {
        if (texto.isEmpty()) {
            return;
        }
        g2.setFont(fuente);
        FontMetrics fm = g2.getFontMetrics();
        int tx = x + (ANCHO - 2 - fm.stringWidth(texto)) / 2;
        int ty = y + (ALTO - 2 + fm.getAscent() - fm.getDescent()) / 2;
        g2.setColor(color);
        g2.drawString(texto, tx, ty);
    }

    private static void pintarCandado(Graphics2D g2, int x, int y) {
        g2.setComposite(java.awt.AlphaComposite.SrcOver);
        g2.setColor(Color.WHITE);
        g2.fillOval(x, y, 24, 24);
        g2.setColor(new Color(0x9B1C1C));
        g2.setStroke(new BasicStroke(2.2f));
        g2.drawRoundRect(x + 6, y + 10, 12, 9, 3, 3);
        g2.drawArc(x + 8, y + 4, 8, 10, 0, 180);
    }
}
