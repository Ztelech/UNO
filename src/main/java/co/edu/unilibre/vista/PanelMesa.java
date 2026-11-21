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

/**
 * Mesa de juego. Al robar, la carta aparece en la mano marcada como NUEVA y el jugador decide
 * ahí mismo: jugarla (si se puede) o pasar el turno.
 */
class PanelMesa extends FondoPlaya {

    private static final int LINEAS_REGISTRO = 14;

    /** Qué está haciendo el jugador que se muestra. */
    private enum Estado {
        /** Puede jugar una carta o robar. */
        NORMAL,
        /** Robó una carta que sí se puede jugar: la juega o pasa. */
        ROBADA_JUGABLE,
        /** Robó una carta que no se puede jugar: su turno ya terminó. */
        ROBADA_NO_JUGABLE
    }

    private final VentanaPrincipal ventana;
    private final JLabel infoPartida = etiquetaChip();
    private final JLabel turno = TemaUno.etiqueta(" ", 18, Font.BOLD, TemaUno.TEXTO);
    private final JLabel sentido = etiquetaChip();
    private final JPanel rivales = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
    private final JPanel centro = new JPanel(new GridBagLayout());
    private final JPanel mano = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
    private final JLabel tituloMano = TemaUno.etiqueta(" ", 15, Font.BOLD, TemaUno.BLANCO);
    private final MensajePastilla mensaje = new MensajePastilla();
    private final JPanel botones = new JPanel();
    private final JTextArea registro = new JTextArea();

    private Estado estado = Estado.NORMAL;
    /** Quien robó: se sigue mostrando su mano aunque el turno ya haya pasado. */
    private Jugador robador;
    private Carta cartaRobada;

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
        JPanel filaMensaje = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        filaMensaje.setOpaque(false);
        filaMensaje.add(this.mensaje);
        medio.add(filaMensaje, BorderLayout.SOUTH);
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

        this.botones.setOpaque(false);
        this.botones.setLayout(new BoxLayout(this.botones, BoxLayout.Y_AXIS));
        this.botones.setPreferredSize(new Dimension(190, 150));
        zona.add(this.botones, BorderLayout.EAST);
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

    /** Empieza el turno de un jugador: limpia el estado y los mensajes del turno anterior. */
    void nuevoTurno() {
        this.estado = Estado.NORMAL;
        this.robador = null;
        this.cartaRobada = null;
        this.mensaje.limpiar();
        mostrar();
    }

    void mostrar() {
        GestorPartidaUno partida = this.ventana.getPartida();
        if (partida == null) {
            return;
        }
        Jugador enVista = this.estado == Estado.NORMAL ? partida.obtenerJugadorActivo() : this.robador;

        this.infoPartida.setText("Partida " + partida.getIdPartida() + "  ·  Semilla " + partida.getSemilla()
                + "  ·  " + (partida.isConteoPuntosActivo() ? "con puntos" : "sin puntos"));
        this.turno.setText("Tu turno, " + enVista.getNombre());
        this.sentido.setText(partida.isSentidoHorario() ? "Sentido horario  ↻" : "Sentido antihorario  ↺");

        pintarRivales(partida, enVista);
        pintarCentro(partida);
        pintarMano(partida, enVista);
        pintarBotones();
        pintarRegistro(partida);

        if (this.estado == Estado.NORMAL && !partida.getAlertasTrampa().isEmpty() && this.mensaje.estaVacio()) {
            this.mensaje.avisar("⚠ " + partida.getAlertasTrampa().size() + " alerta(s) de trampa en esta partida");
        }
        revalidate();
        repaint();

        if (this.estado == Estado.NORMAL && partida.isColorInicialPendiente()) {
            SwingUtilities.invokeLater(this::elegirColorInicial);
        }
    }

    private void pintarRivales(GestorPartidaUno partida, Jugador enVista) {
        this.rivales.removeAll();
        for (Jugador rival : partida.getJugadores()) {
            if (rival == enVista) {
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
            if (this.estado == Estado.NORMAL && partida.esDenunciable(rival)) {
                BotonRedondeado denunciar = new BotonRedondeado("Denunciar (+2)", TemaUno.ROJO_UNO, Color.WHITE);
                denunciar.setFont(TemaUno.texto(12, Font.BOLD));
                denunciar.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
                denunciar.setToolTipText(rival.getNombre() + " tiene una carta y no cantó ¡UNO!");
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
        mazo.setToolTipText("Robar una carta");
        mazo.addActionListener(e -> robar());
        mazo.setEnabled(this.estado == Estado.NORMAL);
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

    private void pintarMano(GestorPartidaUno partida, Jugador enVista) {
        this.mano.removeAll();
        Carta descarte = partida.obtenerCartaSuperiorDescarte();
        ColorCarta color = partida.getColorActivo();
        boolean masCuatroPermitido = partida.validarUsoTomaCuatro(enVista);
        int jugables = 0;
        for (Carta carta : enVista.getMano()) {
            ComponenteCarta comp = new ComponenteCarta(carta);
            boolean esRobada = this.cartaRobada != null && carta.getId() == this.cartaRobada.getId();
            boolean esMasCuatro = carta.getTipo() == TipoCarta.COMODIN_TOMA_CUATRO;
            boolean jugable = switch (this.estado) {
                case NORMAL -> carta.esJugableSobre(descarte, color) && (!esMasCuatro || masCuatroPermitido);
                case ROBADA_JUGABLE -> esRobada;
                case ROBADA_NO_JUGABLE -> false;
            };
            comp.marcar(jugable, !jugable && !esRobada, this.estado == Estado.NORMAL && esMasCuatro && !masCuatroPermitido);
            if (esRobada) {
                comp.marcarNueva();
            }
            comp.addActionListener(e -> clicEnCarta(carta));
            this.mano.add(comp);
            if (jugable) {
                jugables++;
            }
        }
        int n = enVista.getMano().size();
        String pista = switch (this.estado) {
            case ROBADA_JUGABLE -> "la carta NUEVA se puede jugar";
            case ROBADA_NO_JUGABLE -> "la carta NUEVA no se puede jugar";
            case NORMAL -> {
                String p = jugables == 0 ? "no tienes cartas jugables: roba del mazo" : "las cartas elevadas se pueden jugar";
                if (!masCuatroPermitido && enVista.getMano().stream().anyMatch(c -> c.getTipo() == TipoCarta.COMODIN_TOMA_CUATRO)) {
                    p += " · el +4 solo se puede jugar si no tienes cartas " + TemaUno.nombreColor(color).toLowerCase();
                }
                yield p;
            }
        };
        this.tituloMano.setText("Tu mano, " + enVista.getNombre() + " · " + n + (n == 1 ? " carta" : " cartas") + " · " + pista);
    }

    private void pintarBotones() {
        this.botones.removeAll();
        switch (this.estado) {
            case NORMAL -> {
                BotonRedondeado uno = new BotonRedondeado("¡UNO!", TemaUno.AMARILLO_BOTON, TemaUno.ROJO_UNO);
                uno.setFont(TemaUno.titulo(26));
                uno.setToolTipText("Declara UNO cuando te quede exactamente una carta");
                uno.addActionListener(e -> cantarUno());
                agregarBoton(uno);
                this.botones.add(Box.createVerticalStrut(10));
                BotonRedondeado robar = TemaUno.botonClaro("Robar carta");
                robar.addActionListener(e -> robar());
                agregarBoton(robar);
            }
            case ROBADA_JUGABLE -> {
                BotonRedondeado jugar = TemaUno.botonPrincipal("Jugar la NUEVA");
                jugar.setFont(TemaUno.titulo(18));
                jugar.addActionListener(e -> jugarRobada());
                agregarBoton(jugar);
                this.botones.add(Box.createVerticalStrut(10));
                BotonRedondeado pasar = TemaUno.botonClaro("Pasar turno");
                pasar.addActionListener(e -> pasarConRobada());
                agregarBoton(pasar);
            }
            case ROBADA_NO_JUGABLE -> {
                BotonRedondeado terminar = TemaUno.botonPrincipal("Terminar turno");
                terminar.setFont(TemaUno.titulo(18));
                terminar.addActionListener(e -> terminarTurnoTrasRobar());
                agregarBoton(terminar);
            }
        }
        this.botones.revalidate();
        this.botones.repaint();
    }

    private void agregarBoton(BotonRedondeado b) {
        b.setAlignmentX(0.5f);
        b.setMaximumSize(new Dimension(190, b.getPreferredSize().height));
        this.botones.add(b);
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

    private void clicEnCarta(Carta carta) {
        switch (this.estado) {
            case NORMAL -> jugar(carta);
            case ROBADA_JUGABLE -> {
                if (carta.getId() == this.cartaRobada.getId()) {
                    jugarRobada();
                } else {
                    this.mensaje.avisar("Después de robar solo puedes jugar la carta NUEVA o pasar el turno.");
                }
            }
            case ROBADA_NO_JUGABLE -> this.mensaje.avisar("Tu turno terminó: pulsa «Terminar turno».");
        }
    }

    private void jugar(Carta carta) {
        GestorPartidaUno partida = this.ventana.getPartida();
        Jugador activo = partida.obtenerJugadorActivo();
        ColorCarta elegido = null;
        if (carta.getColor() == ColorCarta.NEGRO) {
            if (carta.getTipo() == TipoCarta.COMODIN_TOMA_CUATRO && !partida.validarUsoTomaCuatro(activo)) {
                this.mensaje.avisar("No puedes lanzar un +4 si tienes cartas de color "
                        + TemaUno.nombreColor(partida.getColorActivo()).toLowerCase() + ".");
                return;
            }
            elegido = DialogoColor.elegir(this, "Elige el nuevo color");
            if (elegido == null) {
                return;
            }
        }
        if (!partida.jugarTurno(activo, carta, elegido)) {
            this.mensaje.avisar("Esa carta no coincide en color, número ni símbolo con la de la mesa.");
            return;
        }
        String resumen = activo.getNombre() + " jugó " + TemaUno.describir(carta)
                + (elegido != null ? " y eligió " + TemaUno.nombreColor(elegido).toLowerCase() : "") + ".";
        despuesDeActuar(activo, resumen);
    }

    private void robar() {
        if (this.estado != Estado.NORMAL) {
            return;
        }
        GestorPartidaUno partida = this.ventana.getPartida();
        Jugador activo = partida.obtenerJugadorActivo();
        Carta robada;
        try {
            robada = partida.robarCartaUno(activo);
        } catch (IllegalStateException e) {
            this.mensaje.avisar(e.getMessage());
            return;
        }
        if (robada == null) {
            return;
        }
        this.robador = activo;
        this.cartaRobada = robada;
        if (partida.getCartaRecienRobada() != null) {
            this.estado = Estado.ROBADA_JUGABLE;
            mostrar();
            this.mensaje.informar("Robaste " + TemaUno.describir(robada) + ": puedes jugarla ahora o pasar el turno.");
        } else {
            // El modelo ya pasó el turno; se sigue mostrando la mano de quien robó hasta que confirme.
            this.estado = Estado.ROBADA_NO_JUGABLE;
            mostrar();
            this.mensaje.avisar("Robaste " + TemaUno.describir(robada) + ": no coincide con la mesa, así que tu turno termina.");
        }
    }

    private void jugarRobada() {
        GestorPartidaUno partida = this.ventana.getPartida();
        Carta robada = this.cartaRobada;
        ColorCarta elegido = null;
        if (robada.getColor() == ColorCarta.NEGRO) {
            elegido = DialogoColor.elegir(this, "Elige el nuevo color");
            if (elegido == null) {
                return;
            }
        }
        if (!partida.jugarCartaRecienRobada(this.robador, true, elegido)) {
            this.mensaje.avisar("No se pudo jugar la carta robada.");
            return;
        }
        String resumen = this.robador.getNombre() + " robó y jugó " + TemaUno.describir(robada)
                + (elegido != null ? " y eligió " + TemaUno.nombreColor(elegido).toLowerCase() : "") + ".";
        despuesDeActuar(this.robador, resumen);
    }

    private void pasarConRobada() {
        this.ventana.getPartida().jugarCartaRecienRobada(this.robador, false, null);
        despuesDeActuar(this.robador, this.robador.getNombre() + " robó una carta y pasó el turno.");
    }

    private void terminarTurnoTrasRobar() {
        despuesDeActuar(this.robador, this.robador.getNombre() + " robó una carta y pasó el turno.");
    }

    private void cantarUno() {
        Jugador activo = this.ventana.getPartida().obtenerJugadorActivo();
        this.ventana.getPartida().cantarUno(activo);
        mostrar();
        if (activo.isDijoUno()) {
            this.mensaje.informar(activo.getNombre() + " cantó ¡UNO!");
        } else {
            this.mensaje.avisar("Solo se declara UNO cuando te queda exactamente una carta.");
        }
    }

    private void denunciar(Jugador acusado) {
        GestorPartidaUno partida = this.ventana.getPartida();
        int antes = acusado.getMano().size();
        partida.denunciarNoUno(partida.obtenerJugadorActivo(), acusado);
        mostrar();
        if (acusado.getMano().size() > antes) {
            this.mensaje.informar("Denuncia válida: " + acusado.getNombre() + " roba 2 cartas.");
        } else {
            this.mensaje.avisar("La denuncia ya no es válida.");
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
        this.estado = Estado.NORMAL;
        this.robador = null;
        this.cartaRobada = null;
        if (partida.isPartidaTerminada()) {
            this.ventana.terminarRonda();
        } else if (partida.obtenerJugadorActivo() == anterior) {
            mostrar(); // Reversa con dos jugadores: vuelve a jugar el mismo.
            this.mensaje.informar(resumen + " Vuelves a jugar.");
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

    // ------------------------------------------------------------------ componentes

    /** Mensaje sobre fondo oscuro para que se lea encima de la arena; invisible si está vacío. */
    private static class MensajePastilla extends JLabel {

        MensajePastilla() {
            super(" ");
            setFont(TemaUno.texto(15, Font.BOLD));
            setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
            setOpaque(false);
            limpiar();
        }

        void avisar(String texto) {
            setForeground(new Color(0xFFD166));
            setText(texto);
            setVisible(true);
        }

        void informar(String texto) {
            setForeground(Color.WHITE);
            setText(texto);
            setVisible(true);
        }

        void limpiar() {
            setText(" ");
            setVisible(false);
        }

        boolean estaVacio() {
            return !isVisible();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(3, 4, 94, 225));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
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

    // ------------------------------------------------------------------ pruebas

    /** Solo para pruebas: roba como si se pulsara el mazo. */
    void robarParaPrueba() {
        robar();
    }

    /** Solo para pruebas: texto del mensaje visible (vacío si no hay). */
    String textoMensaje() {
        return this.mensaje.estaVacio() ? "" : this.mensaje.getText();
    }

    /** Solo para pruebas: nombre del estado actual. */
    String nombreEstado() {
        return this.estado.name();
    }

    /** Solo para pruebas: pulsa el botón lateral con ese texto. */
    void pulsarBoton(String texto) {
        for (java.awt.Component c : this.botones.getComponents()) {
            if (c instanceof BotonRedondeado b && b.getText().equals(texto)) {
                b.doClick();
                return;
            }
        }
        throw new IllegalArgumentException("No hay botón «" + texto + "»: " + textosBotones());
    }

    List<String> textosBotones() {
        return java.util.Arrays.stream(this.botones.getComponents())
                .filter(c -> c instanceof BotonRedondeado)
                .map(c -> ((BotonRedondeado) c).getText())
                .toList();
    }
}
