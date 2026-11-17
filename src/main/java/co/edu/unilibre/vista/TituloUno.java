package co.edu.unilibre.vista;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

/** Logotipo "UNO": letras blancas inclinadas con trazo rojo y sombra (volumen). */
class TituloUno extends JComponent {

    private final float tamano;

    TituloUno(float tamano) {
        this.tamano = tamano;
        setPreferredSize(new Dimension(Math.round(tamano * 2.6f), Math.round(tamano * 1.35f)));
        setToolTipText("UNO");
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        Font fuente = new Font(Font.SANS_SERIF, Font.BOLD | Font.ITALIC, Math.round(this.tamano));
        GlyphVector glifos = fuente.createGlyphVector(g2.getFontRenderContext(), "UNO");
        Shape forma = glifos.getOutline();
        Rectangle2D caja = forma.getBounds2D();

        AffineTransform t = new AffineTransform();
        t.translate(getWidth() / 2.0, getHeight() / 2.0);
        t.rotate(Math.toRadians(-7));
        t.translate(-caja.getCenterX(), -caja.getCenterY());
        Shape centrada = t.createTransformedShape(forma);

        float grosor = this.tamano / 11f;
        // Sombra (volumen)
        Shape sombra = AffineTransform.getTranslateInstance(0, this.tamano / 14f).createTransformedShape(centrada);
        g2.setStroke(new BasicStroke(grosor * 2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(TemaUno.ROJO_SOMBRA);
        g2.draw(sombra);
        g2.fill(sombra);
        // Trazo rojo y relleno blanco
        g2.setColor(TemaUno.ROJO_UNO);
        g2.draw(centrada);
        g2.setColor(Color.WHITE);
        g2.fill(centrada);
        g2.dispose();
    }
}
