package co.edu.unilibre.vista;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;

/** Fondo de las pantallas: playa (cielo, mar y arena) o mesa de juego (agua con mesa de arena). */
class FondoPlaya extends JPanel {

    enum Estilo { PLAYA, MESA }

    private final Estilo estilo;

    FondoPlaya(LayoutManager layout, Estilo estilo) {
        super(layout);
        this.estilo = estilo;
        setOpaque(true);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        if (this.estilo == Estilo.MESA) {
            pintarMesa(g2, w, h);
        } else {
            pintarPlaya(g2, w, h);
        }
        g2.dispose();
    }

    private static void pintarPlaya(Graphics2D g2, int w, int h) {
        int horizonte = (int) (h * 0.58);
        g2.setPaint(new GradientPaint(0, 0, TemaUno.CIELO_ARRIBA, 0, horizonte, TemaUno.CIELO_ABAJO));
        g2.fillRect(0, 0, w, horizonte);
        // Sol y nubes
        g2.setColor(new Color(0xFFD166));
        int sol = Math.max(70, h / 8);
        g2.fillOval((int) (w * 0.78), (int) (h * 0.07), sol, sol);
        g2.setColor(new Color(255, 255, 255, 215));
        g2.fillRoundRect((int) (w * 0.08), (int) (h * 0.11), 150, 42, 42, 42);
        g2.fillRoundRect((int) (w * 0.08) + 50, (int) (h * 0.11) - 22, 80, 50, 50, 50);
        // Mar
        int mar = Math.max(50, h / 11);
        g2.setColor(TemaUno.MARINO);
        g2.fillRect(0, horizonte - 12, w, mar);
        g2.setColor(TemaUno.ESPUMA);
        g2.fillRect(0, horizonte - 12, w, 12);
        // Arena
        g2.setColor(TemaUno.ARENA);
        g2.fillRect(0, horizonte - 12 + mar, w, h);
        g2.setColor(new Color(255, 255, 255, 150));
        g2.fillArc(-40, horizonte - 12 + mar - 14, w + 80, 28, 180, 180);
    }

    private static void pintarMesa(Graphics2D g2, int w, int h) {
        float radio = Math.max(w, h) * 0.75f;
        g2.setPaint(new RadialGradientPaint(new Point2D.Float(w / 2f, h * 0.45f), radio,
                new float[]{0f, 0.55f, 1f},
                new Color[]{new Color(0x48CAE4), new Color(0x0096C7), TemaUno.MARINO}));
        g2.fillRect(0, 0, w, h);
        int mx = (int) (w * 0.14);
        int my = (int) (h * 0.13);
        int mw = (int) (w * 0.72);
        int mh = (int) (h * 0.56);
        g2.setColor(new Color(3, 4, 94, 70));
        g2.fillOval(mx + 4, my + 14, mw, mh);
        g2.setColor(TemaUno.ARENA_BORDE);
        g2.fillOval(mx - 10, my - 10, mw + 20, mh + 20);
        g2.setColor(TemaUno.ARENA);
        g2.fillOval(mx, my, mw, mh);
    }
}
