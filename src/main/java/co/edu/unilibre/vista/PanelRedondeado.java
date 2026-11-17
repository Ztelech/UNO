package co.edu.unilibre.vista;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

/** Tarjeta de esquinas redondeadas (blanca o semitransparente). */
class PanelRedondeado extends JPanel {

    private Color fondo;
    private final int radio;

    PanelRedondeado(LayoutManager layout, Color fondo, int radio) {
        super(layout);
        this.fondo = fondo;
        this.radio = radio;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));
    }

    PanelRedondeado(LayoutManager layout) {
        this(layout, new Color(255, 255, 255, 240), 28);
    }

    void setFondo(Color fondo) {
        this.fondo = fondo;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(3, 4, 94, 35));
        g2.fillRoundRect(2, 6, getWidth() - 4, getHeight() - 6, this.radio, this.radio);
        g2.setColor(this.fondo);
        g2.fillRoundRect(0, 0, getWidth(), getHeight() - 4, this.radio, this.radio);
        g2.dispose();
        super.paintComponent(g);
    }
}
