package co.edu.unilibre.vista;

import co.edu.unilibre.dto.Carta;
import co.edu.unilibre.dto.ColorCarta;
import co.edu.unilibre.dto.Jugador;
import co.edu.unilibre.dto.TipoCarta;
import co.edu.unilibre.modelo.GestorPartidaUno;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.List;
import java.util.Map;

/** Mesa de juego del jugador activo (RF05-RF17). */
class PanelMesa extends FondoPlaya {

    private static final int LINEAS_REGISTRO = 14;

    private final VentanaPrincipal ventana;
    private final JLabel infoPartida = etiquetaChip();
    private final JLabel turno = TemaUno.etiqueta(" ", 18, Font.BOLD, TemaUno.TEXTO);
    private final JLabel sentido = etiquetaChip();
    private final JPanel rivales = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
    private final JPanel centro = new JPanel(new GridBagLayout());
    private final JPanel mano = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
    private final JLabel tituloMano = TemaUno.etiqueta(" ", 15, Font.BOLD, TemaUno.BLANCO);
    private final JLabel mensaje = TemaUno.etiqueta(" ", 14, Font.BOLD, TemaUno.BLANCO);
    private final JTextArea registro = new JTextArea();

    PanelMesa(VentanaPrincipal ventana) {
        super(new BorderLayout(0, 8), Estilo.MESA);
        this.ventana = ventana;
        setBorder(BorderFactory.createEmptyBorder(14, 18, 12, 18));

        add(barraSuperior(), BorderLayout.NORTH);

        JPanel medio = new JPanel(new BorderLayout(12, 8));
        medio.setOpaque(false);
        this.rivales.setOpaque(false);
        medio.add(this.rivales, BorderLayout.NORTH);
        this.centro.setOpaque(false);
        medio.add(this.centro, BorderLayout.CENTER);
        medio.add(panelRegistro(), BorderLayout.EAST);
        this.mensaje.setHorizontalAlignment(JLabel.CENTER);
        medio.add(this.mensaje, BorderLayout.SOUTH);
        add(medio, BorderLayout.CENTER);

        add(zonaMano(), BorderLayout.SOUTH);
    }

    // ------------------------------------------------------------------ construcción

    private JComponent barraSuperior() {
        JPanel barra = new JPanel(new BorderLayout(12, 0));
        barra.setOpaque(false);
        JPanel izquierda = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        izquierda.setOpaque(false);
        BotonRedondeado abandonar = TemaUno.botonClaro("Menú");
        abandonar.addActionListener(e -> abandonar());
        izquierda.add(abandonar);
        izquierda.add(this.infoPartida);
        barra.add(izquierda, BorderLayout.WEST);

        PanelRedondeado pastilla = new PanelRedondeado(new FlowLayout(FlowLayout.CENTER, 0, 0), Color.WHITE, 40);
        pastilla.setBorder(BorderFactory.createEmptyBorder(8, 22, 10, 22));
        pastilla.add(this.turno);
        JPanel medio = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        medio.setOpaque(false);
        medio.add(pastilla);
        barra.add(medio, BorderLayout.CENTER);
        barra.add(this.sentido, BorderLayout.EAST);
        return barra;
    }

    private JComponent panelRegistro() {
        PanelRedondeado p = new PanelRedondeado(new BorderLayout(0, 6), new Color(3, 4, 94, 175), 20);
        p.setBorder(BorderFactory.createEmptyBorder(12, 14, 14, 14));
        p.add(TemaUno.etiqueta("REGISTRO DE JUGADAS", 12, Font.BOLD, TemaUno.CIELO_ABAJO), BorderLayout.NORTH);
        this.registro.setEditable(false);
        this.registro.setFocusable(false);
        this.registro.setOpaque(false);
        this.registro.setForeground(Color.WHITE);
        this.registro.setFont(TemaUno.texto(12, Font.PLAIN));
        this.registro.setLineWrap(true);
        this.registro.setWrapStyleWord(true);
        p.add(this.registro, BorderLayout.CENTER);
        p.setPreferredSize(new Dimension(250, 300));
        return p;
    }

    private JComponent zonaMano() {
        JPanel zona = new JPanel(new BorderLayout(12, 4));
        zona.setOpaque(false);
        zona.add(this.tituloMano, BorderLayout.NORTH);

        this.mano.setOpaque(false);
        JScrollPane scroll = new JScrollPane(this.mano,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getHorizontalScrollBar().setUnitIncrement(24);
        scroll.setPreferredSize(new Dimension(600, ComponenteCarta.ALTO + 16 + 30));
        zona.add(scroll, BorderLayout.CENTER);

        JPanel botones = new JPanel();
        botones.setOpaque(false);
        botones.setLayout(new BoxLayout(botones, BoxLayout.Y_AXIS));
        BotonRedondeado uno = new BotonRedondeado("¡UNO!", TemaUno.AMARILLO_BOTON, TemaUno.ROJO_UNO);
        uno.setFont(TemaUno.titulo(26));
        uno.setAlignmentX(0.5f);
        uno.setToolTipText("Declara UNO cuando te quede exactamente una carta (RF16)");
        uno.addActionListener(e -> cantarUno());
        BotonRedondeado robar = TemaUno.botonClaro("Robar carta");
        robar.setAlignmentX(0.5f);
        robar.addActionListener(e -> robar());
        botones.add(uno);
        botones.add(Box.createVerticalStrut(10));
        botones.add(robar);
        zona.add(botones, BorderLayout.EAST);
        return zona;
    }

    private static JLabel etiquetaChip() {
        JLabel l = TemaUno.etiqueta(" ", 13, Font.BOLD, Color.WHITE);
        l.setOpaque(true);
        l.setBackground(new Color(3, 4, 94));
        l.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        return l;
    }

    // ------------------------------------------------------------------ pintar el estado

    void mostrar() {
        GestorPartidaUno partida = this.ventana.getPartida();
        if (partida == null) {
            return;
        }
        Jugador activo = partida.obtenerJugadorActivo();

        this.infoPartida.setText("Partida " + partida.getIdPartida() + "  ·  Semilla " + partida.getSemilla());
        this.turno.setText("Tu turno, " + activo.getNombre());
        this.sentido.setText(partida.isSentidoHorario() ? "Sentido horario  ↻" : "Sentido antihorario  ↺");

        pintarRivales(partida, activo);
        pintarCentro(partida);
        pintarMano(partida, activo);
        pintarRegistro(partida);

        List<String> alertas = partida.getAlertasTrampa();
        if (!alertas.isEmpty()) {
            this.mensaje.setForeground(new Color(0xFFD166));
            this.mensaje.setText("⚠ " + alertas.size() + " alerta(s) de trampa en esta partida");
        }
        revalidate();
        repaint();

        if (partida.isColorInicialPendiente()) {
            SwingUtilities.invokeLater(this::elegirColorInicial);
        }
    }

    private void pintarRivales(GestorPartidaUno partida, Jugador activo) {
        this.rivales.removeAll();
        for (Jugador rival : partida.getJugadores()) {
            if (rival == activo) {
                continue;
            }
            PanelRedondeado chip = new PanelRedondeado(new GridBagLayout(), Color.WHITE, 22);
            chip.setBorder(BorderFactory.createEmptyBorder(8, 14, 12, 14));
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.anchor = GridBagConstraints.CENTER;
            chip.add(TemaUno.etiqueta(rival.getNombre(), 15, Font.BOLD, TemaUno.TEXTO), c);
            int n = rival.getMano().size();
            chip.add(TemaUno.etiqueta(n + (n == 1 ? " carta" : " cartas"), 13, Font.PLAIN, TemaUno.TEXTO_SUAVE), c);
            if (rival.isDijoUno()) {
                chip.add(TemaUno.etiqueta("¡UNO!", 13, Font.BOLD, TemaUno.ROJO_UNO), c);
            }
            if (partida.esDenunciable(rival)) {
                BotonRedondeado denunciar = new BotonRedondeado("Denunciar (+2)", TemaUno.ROJO_UNO, Color.WHITE);
                denunciar.setFont(TemaUno.texto(12, Font.BOLD));
                denunciar.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
                denunciar.setToolTipText(rival.getNombre() + " tiene una carta y no cantó ¡UNO! (RF17)");
                denunciar.addActionListener(e -> denunciar(rival));
                c.insets = new Insets(4, 0, 0, 0);
                chip.add(denunciar, c);
            }
            this.rivales.add(chip);
        }
    }

    private void pintarCentro(GestorPartidaUno partida) {
        this.centro.removeAll();
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(0, 24, 0, 24);

        ComponenteCarta mazo = new ComponenteCarta(null, 1.05f);
        mazo.getAccessibleContext().setAccessibleName("Mazo de robo: robar una carta");
        mazo.setToolTipText("Robar una carta (RF10)");
        mazo.addActionListener(e -> robar());
        c.gridx = 0;
        c.gridy = 0;
        this.centro.add(mazo, c);

        ComponenteCarta descarte = new ComponenteCarta(partida.obtenerCartaSuperiorDescarte(), 1.25f);
        descarte.setFocusable(false);
        descarte.setCursor(null);
        c.gridx = 1;
        this.centro.add(descarte, c);

        c.gridy = 1;
        c.gridx = 0;
        c.insets = new Insets(4, 24, 0, 24);
        this.centro.add(TemaUno.etiqueta("MAZO · " + partida.cartasEnMazo() + " CARTAS", 13, Font.BOLD, new Color(0x5A4A1E)), c);
        c.gridx = 1;
        this.centro.add(new IndicadorColor(partida.getColorActivo()), c);
    }

    private void pintarMano(GestorPartidaUno partida, Jugador activo) {
        this.mano.removeAll();
        Carta descarte = partida.obtenerCartaSuperiorDescarte();
        ColorCarta color = partida.getColorActivo();
        boolean masCuatroPermitido = partida.validarUsoTomaCuatro(activo);
        int jugables = 0;
        for (Carta carta : activo.getMano()) {
            ComponenteCarta comp = new ComponenteCarta(carta);
            boolean esMasCuatro = carta.getTipo() == TipoCarta.COMODIN_TOMA_CUATRO;
            boolean jugable = carta.esJugableSobre(descarte, color) && (!esMasCuatro || masCuatroPermitido);
            comp.marcar(jugable, !jugable, esMasCuatro && !masCuatroPermitido);
            comp.addActionListener(e -> jugar(carta));
            this.mano.add(comp);
            if (jugable) {
                jugables++;
            }
        }
        int n = activo.getMano().size();
        String pista = jugables == 0 ? "no tienes cartas jugables: roba del mazo"
                : "las cartas elevadas se pueden jugar";
        if (!masCuatroPermitido && activo.getMano().stream().anyMatch(c -> c.getTipo() == TipoCarta.COMODIN_TOMA_CUATRO)) {
            pista += " · +4 bloqueado: tienes cartas " + TemaUno.nombreColor(color).toLowerCase();
        }
        this.tituloMano.setText("Tu mano, " + activo.getNombre() + " · " + n + (n == 1 ? " carta" : " cartas") + " · " + pista);
    }

    private void pintarRegistro(GestorPartidaUno partida) {
        String[] lineas = partida.getLogAcciones().split("\n");
        Map<Integer, String> nombres = this.ventana.nombresPartida();
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, lineas.length - LINEAS_REGISTRO); i < lineas.length; i++) {
            sb.append("• ").append(TemaUno.lineaLegible(lineas[i], nombres)).append('\n');
        }
        this.registro.setText(sb.toString());
    }

    // ------------------------------------------------------------------ acciones

    private void jugar(Carta carta) {
        GestorPartidaUno partida = this.ventana.getPartida();
        Jugador activo = partida.obtenerJugadorActivo();
        ColorCarta elegido = null;
        if (carta.getColor() == ColorCarta.NEGRO) {
            if (carta.getTipo() == TipoCarta.COMODIN_TOMA_CUATRO && !partida.validarUsoTomaCuatro(activo)) {
                avisar("RF09: no puedes lanzar un +4 si tienes cartas de color "
                        + TemaUno.nombreColor(partida.getColorActivo()).toLowerCase() + ".");
                return;
            }
            elegido = DialogoColor.elegir(this, "Elige el nuevo color");
            if (elegido == null) {
                return;
            }
        }
        if (!partida.jugarTurno(activo, carta, elegido)) {
            avisar("Esa carta no coincide en color, número ni símbolo con la de la mesa (RF08).");
            return;
        }
        String resumen = activo.getNombre() + " jugó " + TemaUno.describir(carta)
                + (elegido != null ? " y eligió " + TemaUno.nombreColor(elegido).toLowerCase() : "") + ".";
        despuesDeActuar(activo, resumen);
    }

    private void robar() {
        GestorPartidaUno partida = this.ventana.getPartida();
        Jugador activo = partida.obtenerJugadorActivo();
        Carta robada;
        try {
            robada = partida.robarCartaUno(activo);
        } catch (IllegalStateException e) {
            avisar(e.getMessage());
            return;
        }
        if (robada == null) {
            return;
        }
        if (partida.getCartaRecienRobada() != null) {
            int r = JOptionPane.showConfirmDialog(this,
                    "Robaste " + TemaUno.describir(robada) + " y se puede jugar. ¿Quieres jugarla ahora? (RF11)",
                    "Carta robada", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (r == JOptionPane.YES_OPTION) {
                ColorCarta elegido = robada.getColor() == ColorCarta.NEGRO
                        ? DialogoColor.elegir(this, "Elige el nuevo color") : null;
                if (robada.getColor() != ColorCarta.NEGRO || elegido != null) {
                    partida.jugarCartaRecienRobada(activo, true, elegido);
                    despuesDeActuar(activo, activo.getNombre() + " robó y jugó " + TemaUno.describir(robada) + ".");
                    return;
                }
            }
            partida.jugarCartaRecienRobada(activo, false, null);
            despuesDeActuar(activo, activo.getNombre() + " robó una carta y pasó el turno.");
        } else {
            JOptionPane.showMessageDialog(this, "Robaste " + TemaUno.describir(robada)
                    + ". No se puede jugar, así que tu turno termina (RF11).", "Carta robada", JOptionPane.INFORMATION_MESSAGE);
            despuesDeActuar(activo, activo.getNombre() + " robó una carta y pasó el turno.");
        }
    }

    private void cantarUno() {
        Jugador activo = this.ventana.getPartida().obtenerJugadorActivo();
        this.ventana.getPartida().cantarUno(activo);
        if (activo.isDijoUno()) {
            informar(activo.getNombre() + " cantó ¡UNO!");
        } else {
            avisar("Solo se declara UNO con exactamente una carta (RF16).");
        }
        mostrar();
    }

    private void denunciar(Jugador acusado) {
        GestorPartidaUno partida = this.ventana.getPartida();
        int antes = acusado.getMano().size();
        partida.denunciarNoUno(partida.obtenerJugadorActivo(), acusado);
        mostrar();
        if (acusado.getMano().size() > antes) {
            informar("Denuncia válida: " + acusado.getNombre() + " roba 2 cartas (RF17).");
        } else {
            avisar("La denuncia ya no es válida.");
        }
    }

    private void elegirColorInicial() {
        GestorPartidaUno partida = this.ventana.getPartida();
        if (!partida.isColorInicialPendiente() || !VentanaPrincipal.MESA.equals(this.ventana.getPantallaActual())) {
            return;
        }
        ColorCarta color = DialogoColor.elegir(this, "La carta inicial es un comodín: elige el color");
        if (color != null) {
            partida.elegirColorInicial(partida.obtenerJugadorActivo(), color);
        }
        mostrar();
    }

    private void despuesDeActuar(Jugador anterior, String resumen) {
        GestorPartidaUno partida = this.ventana.getPartida();
        if (partida.isPartidaTerminada()) {
            this.ventana.terminarRonda();
        } else if (partida.obtenerJugadorActivo() == anterior) {
            mostrar(); // Reversa con dos jugadores: vuelve a jugar el mismo.
            informar(resumen + " Vuelves a jugar.");
        } else {
            this.ventana.mostrarCambioTurno(anterior, resumen);
        }
    }

    private void abandonar() {
        int r = JOptionPane.showConfirmDialog(this, "¿Abandonar la partida? No se guardará el resultado.",
                "Abandonar partida", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r == JOptionPane.YES_OPTION) {
            this.ventana.mostrarMenu();
        }
    }

    private void avisar(String texto) {
        this.mensaje.setForeground(new Color(0xFFD166));
        this.mensaje.setText(texto);
    }

    private void informar(String texto) {
        this.mensaje.setForeground(Color.WHITE);
        this.mensaje.setText(texto);
    }

    /** Pastilla "Color activo: Azul" con un círculo del color. */
    private static class IndicadorColor extends JLabel {
        private final ColorCarta color;

        IndicadorColor(ColorCarta color) {
            super("COLOR ACTIVO: " + TemaUno.nombreColor(color).toUpperCase());
            this.color = color;
            setFont(TemaUno.texto(13, Font.BOLD));
            setForeground(TemaUno.TEXTO);
            setBorder(BorderFactory.createEmptyBorder(6, 34, 6, 14));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.setColor(TemaUno.colorDe(this.color));
            g2.fillOval(12, (getHeight() - 14) / 2, 14, 14);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
