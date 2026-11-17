package co.edu.unilibre.vista;

import co.edu.unilibre.dto.ColorCarta;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;

/** RF15: ventana modal para elegir el color activo tras jugar un comodín. */
final class DialogoColor {

    private DialogoColor() {
    }

    /** @return el color elegido, o null si se cerró la ventana sin elegir. */
    static ColorCarta elegir(Component padre, String titulo) {
        Window ventana = padre == null ? null : javax.swing.SwingUtilities.getWindowAncestor(padre);
        JDialog dialogo = new JDialog(ventana, "Elige el color", JDialog.ModalityType.APPLICATION_MODAL);
        ColorCarta[] elegido = new ColorCarta[1];

        PanelRedondeado contenido = new PanelRedondeado(new BorderLayout(0, 16));
        contenido.add(TemaUno.centrado(TemaUno.etiqueta(titulo, 22, Font.BOLD, TemaUno.TEXTO)), BorderLayout.NORTH);

        JPanel botones = new JPanel(new GridLayout(2, 2, 12, 12));
        botones.setOpaque(false);
        for (ColorCarta color : new ColorCarta[]{ColorCarta.ROJO, ColorCarta.AZUL, ColorCarta.AMARILLO, ColorCarta.VERDE}) {
            BotonRedondeado b = new BotonRedondeado(TemaUno.nombreColor(color).toUpperCase(),
                    TemaUno.colorDe(color), TemaUno.textoSobre(color));
            b.setFont(TemaUno.titulo(20));
            b.setPreferredSize(new Dimension(160, 90));
            b.addActionListener(e -> {
                elegido[0] = color;
                dialogo.dispose();
            });
            botones.add(b);
        }
        contenido.add(botones, BorderLayout.CENTER);

        JPanel fondo = new FondoPlaya(new BorderLayout(), FondoPlaya.Estilo.MESA);
        fondo.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        fondo.add(contenido);
        dialogo.setContentPane(fondo);
        dialogo.pack();
        dialogo.setLocationRelativeTo(padre);
        dialogo.setVisible(true);
        return elegido[0];
    }
}
