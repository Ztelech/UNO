package co.edu.unilibre.vista;

import co.edu.unilibre.dto.Jugador;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * Pantalla entre turnos (RF07): tapa la mano del jugador anterior mientras se pasa el computador.
 * También es el momento para que el jugador anterior declare ¡UNO! (RF16).
 */
class PanelCambioTurno extends FondoPlaya {

    private final VentanaPrincipal ventana;
    private final PanelRedondeado tarjeta = new PanelRedondeado(new GridBagLayout());

    PanelCambioTurno(VentanaPrincipal ventana) {
        super(new GridBagLayout(), Estilo.MESA);
        this.ventana = ventana;
        this.tarjeta.setBorder(BorderFactory.createEmptyBorder(28, 36, 28, 36));
        add(this.tarjeta);
    }

    void mostrar(Jugador anterior, String resumen) {
        this.tarjeta.removeAll();
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 10, 0);

        if (resumen != null && !resumen.isBlank()) {
            JLabel r = TemaUno.centrado(TemaUno.etiqueta("<html><div style='text-align:center;width:460px'>"
                    + escapar(resumen) + "</div></html>", 15, Font.PLAIN, TemaUno.TEXTO));
            this.tarjeta.add(r, c);
        }

        if (anterior != null) {
            this.tarjeta.add(seccionUno(anterior), c);
            JSeparator sep = new JSeparator();
            sep.setForeground(new Color(0xE3ECF3));
            this.tarjeta.add(sep, c);
        }

        Jugador siguiente = this.ventana.getPartida().obtenerJugadorActivo();
        this.tarjeta.add(TemaUno.centrado(TemaUno.etiqueta("Turno de " + siguiente.getNombre(), 36, Font.BOLD, TemaUno.TEXTO)), c);
        this.tarjeta.add(TemaUno.centrado(TemaUno.etiqueta(
                "Pasa el computador a " + siguiente.getNombre() + ". Los demás no deben mirar la pantalla.",
                14, Font.PLAIN, TemaUno.TEXTO_SUAVE)), c);

        BotonRedondeado listo = TemaUno.botonPrincipal("Soy " + siguiente.getNombre() + ": mostrar mi mano");
        listo.setPreferredSize(new Dimension(460, 58));
        listo.addActionListener(e -> this.ventana.mostrarMesa());
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(10, 0, 0, 0);
        this.tarjeta.add(listo, c);

        this.tarjeta.revalidate();
        this.tarjeta.repaint();
        listo.requestFocusInWindow();
    }

    private JPanel seccionUno(Jugador anterior) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        p.setOpaque(false);
        JLabel pregunta = TemaUno.etiqueta(anterior.getNombre() + ", ¿algo que declarar antes de pasar?",
                14, Font.BOLD, TemaUno.TEXTO);
        JLabel respuesta = TemaUno.etiqueta(" ", 13, Font.BOLD, TemaUno.VERDE_OK);
        BotonRedondeado uno = new BotonRedondeado("¡UNO!", TemaUno.AMARILLO_BOTON, TemaUno.ROJO_UNO);
        uno.setFont(TemaUno.titulo(20));
        uno.addActionListener(e -> {
            this.ventana.getPartida().cantarUno(anterior);
            if (anterior.isDijoUno()) {
                respuesta.setForeground(TemaUno.VERDE_OK);
                respuesta.setText(anterior.getNombre() + " cantó ¡UNO!");
                uno.setEnabled(false);
            } else {
                respuesta.setForeground(TemaUno.ROJO_UNO);
                respuesta.setText("Solo se canta UNO cuando te queda una carta.");
            }
        });
        p.add(pregunta);
        p.add(uno);
        p.add(respuesta);
        return p;
    }

    private static String escapar(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
