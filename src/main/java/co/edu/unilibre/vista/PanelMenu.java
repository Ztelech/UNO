package co.edu.unilibre.vista;

import co.edu.unilibre.dto.Jugador;
import co.edu.unilibre.modelo.GestorPartidaUno;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

/** Menú principal: perfiles de los jugadores (RF20), semilla, puntos opcionales (RF19) y Top 10 (RF23). */
class PanelMenu extends FondoPlaya {

    private final VentanaPrincipal ventana;
    private final DefaultListModel<Jugador> modeloJugadores = new DefaultListModel<>();
    private final JList<Jugador> listaJugadores = new JList<>(this.modeloJugadores);
    private final JTextField campoNombre = campoTexto(16);
    private final JTextField campoSemilla = campoTexto(16);
    private final JCheckBox conteoPuntos = new JCheckBox("Sumar al ganador los puntos de las cartas rivales (RF19)", true);
    private final JLabel mensaje = TemaUno.etiqueta(" ", 13, Font.BOLD, TemaUno.ROJO_UNO);
    private final BotonRedondeado botonJugar = TemaUno.botonPrincipal("JUGAR PARTIDA  ▶");
    private final DefaultTableModel modeloRanking =
            new DefaultTableModel(new Object[]{"#", "Jugador", "Jugadas", "Ganadas", "Puntos"}, 0) {
                @Override
                public boolean isCellEditable(int fila, int columna) {
                    return false;
                }
            };
    private final JLabel estadoNube = TemaUno.etiqueta(" ", 12, Font.PLAIN, TemaUno.TEXTO);

    PanelMenu(VentanaPrincipal ventana) {
        super(new BorderLayout(), Estilo.PLAYA);
        this.ventana = ventana;
        setBorder(BorderFactory.createEmptyBorder(16, 40, 16, 40));

        add(cabecera(), BorderLayout.NORTH);

        JPanel centro = new JPanel(new GridBagLayout());
        centro.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.insets = new Insets(0, 0, 0, 18);
        c.weightx = 0.58;
        centro.add(formulario(), c);
        c.insets = new Insets(0, 0, 0, 0);
        c.weightx = 0.42;
        centro.add(tarjetaRanking(), c);
        add(centro, BorderLayout.CENTER);

        add(pie(), BorderLayout.SOUTH);
        this.campoSemilla.setText(String.valueOf(VentanaPrincipal.semillaAleatoria()));
    }

    // ------------------------------------------------------------------ construcción

    private JComponent cabecera() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        TituloUno titulo = new TituloUno(96);
        titulo.setAlignmentX(0.5f);
        p.add(titulo);
        JLabel subtitulo = TemaUno.centrado(TemaUno.etiqueta("Por Jeff y Brenda", 15, Font.BOLD, TemaUno.BLANCO));
        subtitulo.setOpaque(true);
        subtitulo.setBackground(TemaUno.MARINO_OSCURO);
        subtitulo.setBorder(BorderFactory.createEmptyBorder(4, 14, 4, 14));
        p.add(subtitulo);
        p.add(Box.createVerticalStrut(12));
        return p;
    }

    private JComponent formulario() {
        PanelRedondeado tarjeta = new PanelRedondeado(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 6, 0);

        tarjeta.add(TemaUno.tituloSeccion("Nueva partida local"), c);
        tarjeta.add(TemaUno.etiqueta("Jugadores (2 a 10). Si el nombre ya existe se usa su perfil.",
                13, Font.PLAIN, TemaUno.TEXTO_SUAVE), c);

        JLabel etiquetaNombre = TemaUno.etiqueta("Nombre del jugador", 13, Font.BOLD, TemaUno.TEXTO);
        etiquetaNombre.setLabelFor(this.campoNombre);
        tarjeta.add(etiquetaNombre, c);
        BotonRedondeado agregar = TemaUno.botonAzul("Agregar");
        agregar.addActionListener(e -> agregarJugador());
        this.campoNombre.addActionListener(e -> agregarJugador());
        tarjeta.add(fila(this.campoNombre, agregar), c);

        this.listaJugadores.setVisibleRowCount(5);
        this.listaJugadores.setFont(TemaUno.texto(15, Font.PLAIN));
        this.listaJugadores.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.listaJugadores.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> lista, Object valor, int indice,
                                                          boolean seleccionado, boolean foco) {
                Jugador j = (Jugador) valor;
                String texto = (indice + 1) + ".  " + j.getNombre() + "   ·   " + j.getPuntosAcumulados() + " pts";
                return super.getListCellRendererComponent(lista, texto, indice, seleccionado, foco);
            }
        });
        this.listaJugadores.getAccessibleContext().setAccessibleName("Jugadores de la partida");
        JScrollPane scroll = new JScrollPane(this.listaJugadores);
        scroll.setPreferredSize(new Dimension(300, 130));
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        tarjeta.add(scroll, c);
        c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        BotonRedondeado quitar = TemaUno.botonClaro("Quitar seleccionado");
        quitar.addActionListener(e -> quitarJugador());
        JPanel filaQuitar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        filaQuitar.setOpaque(false);
        filaQuitar.add(quitar);
        tarjeta.add(filaQuitar, c);

        JLabel etiquetaSemilla = TemaUno.etiqueta("Semilla de la partida", 13, Font.BOLD, TemaUno.TEXTO);
        etiquetaSemilla.setLabelFor(this.campoSemilla);
        tarjeta.add(etiquetaSemilla, c);
        this.campoSemilla.setFont(TemaUno.mono(16));
        BotonRedondeado aleatoria = TemaUno.botonAzul("Semilla aleatoria");
        aleatoria.addActionListener(e -> this.campoSemilla.setText(String.valueOf(VentanaPrincipal.semillaAleatoria())));
        tarjeta.add(fila(this.campoSemilla, aleatoria), c);
        tarjeta.add(TemaUno.etiqueta("La misma semilla reproduce la misma baraja; se guarda para auditar la partida.",
                12, Font.PLAIN, TemaUno.TEXTO_SUAVE), c);

        this.conteoPuntos.setOpaque(false);
        this.conteoPuntos.setFont(TemaUno.texto(13, Font.PLAIN));
        this.conteoPuntos.setForeground(TemaUno.TEXTO);
        tarjeta.add(this.conteoPuntos, c);
        tarjeta.add(this.mensaje, c);

        this.botonJugar.addActionListener(e -> jugar());
        c.insets = new Insets(4, 0, 0, 0);
        tarjeta.add(this.botonJugar, c);
        return tarjeta;
    }

    private JComponent tarjetaRanking() {
        PanelRedondeado tarjeta = new PanelRedondeado(new BorderLayout(0, 8), new Color(255, 255, 255, 200), 28);
        JPanel titulo = new JPanel();
        titulo.setOpaque(false);
        titulo.setLayout(new BoxLayout(titulo, BoxLayout.Y_AXIS));
        titulo.add(TemaUno.tituloSeccion("Top 10 histórico"));
        titulo.add(TemaUno.etiqueta("Ordenado por puntos acumulados (RF23)", 13, Font.PLAIN, TemaUno.TEXTO_SUAVE));
        tarjeta.add(titulo, BorderLayout.NORTH);

        JTable tabla = new JTable(this.modeloRanking);
        tabla.setRowHeight(28);
        tabla.setFont(TemaUno.texto(14, Font.PLAIN));
        tabla.getTableHeader().setFont(TemaUno.texto(12, Font.BOLD));
        tabla.setFillsViewportHeight(true);
        tabla.getColumnModel().getColumn(0).setMaxWidth(36);
        tabla.getAccessibleContext().setAccessibleName("Ranking de los 10 mejores jugadores");
        tarjeta.add(new JScrollPane(tabla), BorderLayout.CENTER);
        return tarjeta;
    }

    private JComponent pie() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        BotonRedondeado auditar = TemaUno.botonClaro("Auditar partidas guardadas");
        auditar.addActionListener(e -> this.ventana.mostrarAuditoria());
        JPanel izquierda = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        izquierda.setOpaque(false);
        izquierda.add(auditar);
        p.add(izquierda, BorderLayout.WEST);
        p.add(this.estadoNube, BorderLayout.EAST);
        return p;
    }

    private static JPanel fila(JComponent principal, JComponent boton) {
        JPanel f = new JPanel(new BorderLayout(10, 0));
        f.setOpaque(false);
        f.add(principal, BorderLayout.CENTER);
        f.add(boton, BorderLayout.EAST);
        return f;
    }

    private static JTextField campoTexto(int columnas) {
        JTextField campo = new JTextField(columnas);
        campo.setFont(TemaUno.texto(16, Font.PLAIN));
        campo.setForeground(TemaUno.TEXTO);
        campo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xB9D9EB), 2, true),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        return campo;
    }

    // ------------------------------------------------------------------ acciones

    void refrescar() {
        this.modeloRanking.setRowCount(0);
        try {
            List<Jugador> top = this.ventana.getRanking().generarTablaPosicionesTop10();
            for (int i = 0; i < top.size(); i++) {
                Jugador j = top.get(i);
                this.modeloRanking.addRow(new Object[]{i + 1, j.getNombre(), j.getPartidasJugadas(),
                        j.getPartidasGanadas(), j.getPuntosAcumulados()});
            }
            // Los puntos de la lista pueden haber cambiado tras una ronda.
            for (int i = 0; i < this.modeloJugadores.size(); i++) {
                Jugador actualizado = this.ventana.getDaoJugadores().buscarPorId(this.modeloJugadores.get(i).getId());
                if (actualizado != null) {
                    this.modeloJugadores.set(i, actualizado);
                }
            }
        } catch (RuntimeException e) {
            mostrarMensaje("No se pudo leer el ranking: " + e.getMessage(), true);
        }
        actualizarEstadoNube();
        actualizarBotonJugar();
    }

    private void actualizarEstadoNube() {
        var conexion = this.ventana.getConexion();
        String texto;
        if (!conexion.isNubeConfigurada()) {
            texto = "Base local H2 · sin conexión a Neon configurada";
        } else {
            int pendientes = conexion.getPendientesDeSincronizar();
            texto = "Base local H2 + Neon · " + (pendientes == 0 ? "todo sincronizado" : pendientes + " pendientes por subir");
        }
        this.estadoNube.setText(texto);
    }

    private void agregarJugador() {
        String nombre = this.campoNombre.getText();
        if (this.modeloJugadores.size() >= GestorPartidaUno.MAXIMO_JUGADORES) {
            mostrarMensaje("Máximo " + GestorPartidaUno.MAXIMO_JUGADORES + " jugadores por partida.", true);
            return;
        }
        try {
            Jugador perfil = this.ventana.getRanking().registrarOSeleccionarPerfil(nombre);
            for (int i = 0; i < this.modeloJugadores.size(); i++) {
                if (this.modeloJugadores.get(i).getId() == perfil.getId()) {
                    mostrarMensaje(perfil.getNombre() + " ya está en la partida.", true);
                    return;
                }
            }
            this.modeloJugadores.addElement(perfil);
            this.campoNombre.setText("");
            mostrarMensaje("Perfil de " + perfil.getNombre() + " listo.", false);
        } catch (IllegalArgumentException e) {
            mostrarMensaje(e.getMessage(), true);
        } catch (RuntimeException e) {
            mostrarMensaje("No se pudo guardar el perfil: " + e.getMessage(), true);
        }
        actualizarBotonJugar();
        this.campoNombre.requestFocusInWindow();
    }

    private void quitarJugador() {
        int indice = this.listaJugadores.getSelectedIndex();
        if (indice >= 0) {
            this.modeloJugadores.remove(indice);
        }
        actualizarBotonJugar();
    }

    private void actualizarBotonJugar() {
        this.botonJugar.setEnabled(this.modeloJugadores.size() >= GestorPartidaUno.MINIMO_JUGADORES);
    }

    private void jugar() {
        long semilla;
        try {
            semilla = Long.parseLong(this.campoSemilla.getText().strip());
        } catch (NumberFormatException e) {
            mostrarMensaje("La semilla debe ser un número entero (ej. 123456789).", true);
            return;
        }
        List<Jugador> jugadores = new ArrayList<>();
        for (int i = 0; i < this.modeloJugadores.size(); i++) {
            jugadores.add(this.modeloJugadores.get(i));
        }
        if (jugadores.size() < GestorPartidaUno.MINIMO_JUGADORES) {
            mostrarMensaje("Agrega al menos 2 jugadores.", true);
            return;
        }
        mostrarMensaje(" ", false);
        this.ventana.iniciarPartida(jugadores, semilla, this.conteoPuntos.isSelected());
    }

    private void mostrarMensaje(String texto, boolean error) {
        this.mensaje.setForeground(error ? TemaUno.ROJO_UNO : TemaUno.VERDE_OK);
        this.mensaje.setText(texto);
    }

    /** Solo para pruebas y capturas: agrega perfiles sin escribirlos a mano. */
    void agregarPerfilesDePrueba(String... nombres) {
        for (String n : nombres) {
            this.campoNombre.setText(n);
            agregarJugador();
        }
    }
}
