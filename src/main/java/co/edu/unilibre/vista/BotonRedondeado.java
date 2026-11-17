package co.edu.unilibre.vista;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Botón plano de esquinas redondeadas (estilo del prototipo). */
class BotonRedondeado extends JButton {

    private Color fondo;

    BotonRedondeado(String texto, Color fondo, Color textoColor) {
        super(texto);
        this.fondo = fondo;
        setForeground(textoColor);
        setFont(TemaUno.texto(15, Font.BOLD));
        setContentAreaFilled(false);
        setFocusPainted(true);
        setBorderPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(BorderFactory.createEmptyBorder(10, 22, 10, 22));
    }

    void setFondo(Color fondo) {
        this.fondo = fondo;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color base = isEnabled() ? this.fondo : mezclar(this.fondo, Color.LIGHT_GRAY, 0.6f);
        if (getModel().isPressed()) {
            base = base.darker();
        } else if (getModel().isRollover() && isEnabled()) {
            base = mezclar(base, Color.WHITE, 0.12f);
        }
        int arco = Math.min(getHeight(), 40);
        g2.setColor(new Color(0, 0, 0, 40));
        g2.fillRoundRect(0, 3, getWidth(), getHeight() - 3, arco, arco);
        g2.setColor(base);
        g2.fillRoundRect(0, 0, getWidth(), getHeight() - 3, arco, arco);
        if (Color.WHITE.equals(this.fondo)) {
            g2.setColor(new Color(0xB9D9EB));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 4, arco, arco);
        }
        g2.dispose();
        super.paintComponent(g);
    }

    private static Color mezclar(Color a, Color b, float t) {
        return new Color(
                Math.round(a.getRed() * (1 - t) + b.getRed() * t),
                Math.round(a.getGreen() * (1 - t) + b.getGreen() * t),
                Math.round(a.getBlue() * (1 - t) + b.getBlue() * t));
    }
}
