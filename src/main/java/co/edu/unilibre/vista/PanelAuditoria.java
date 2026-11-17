package co.edu.unilibre.vista;

import co.edu.unilibre.dto.Jugador;
import co.edu.unilibre.dto.Partida;
import co.edu.unilibre.modelo.AuditorSemilla;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Auditoría de partidas guardadas: repite el registro con la semilla (RF24-RF27). */
class PanelAuditoria extends FondoPlaya {

    private final VentanaPrincipal ventana;
    private final DefaultTableModel modelo =
            new DefaultTableModel(new Object[]{"Partida", "Fecha", "Semilla", "Ganador", "Puntos", "Sospecha guardada"}, 0) {
                @Override
                public boolean isCellEditable(int fila, int columna) {
                    return false;
                }
            };
    private final JTable tabla = new JTable(this.modelo);
    private final JLabel resultado = TemaUno.etiqueta("Elige una partida y pulsa «Auditar con la semilla».",
            15, Font.BOLD, TemaUno.TEXTO);
    private final PanelRedondeado bannerResultado = new PanelRedondeado(new BorderLayout(), Color.WHITE, 18);
    private final JTextArea registro = new JTextArea();
    private List<Partida> partidas = new ArrayList<>();

    PanelAuditoria(VentanaPrincipal ventana) {
        super(new BorderLayout(0, 14), Estilo.PLAYA);
        this.ventana = ventana;
        setBorder(BorderFactory.createEmptyBorder(18, 40, 24, 40));

        JPanel cabecera = new JPanel(new BorderLayout());
        cabecera.setOpaque(false);
        BotonRedondeado volver = TemaUno.botonClaro("‹ Volver al menú");
        volver.addActionListener(e -> this.ventana.mostrarMenu());
        JPanel izquierda = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        izquierda.setOpaque(false);
        izquierda.add(volver);
        izquierda.add(TemaUno.etiqueta("Auditoría anti-trampas", 32, Font.BOLD, TemaUno.MARINO_OSCURO));
        cabecera.add(izquierda, BorderLayout.WEST);
        add(cabecera, BorderLayout.NORTH);

        JPanel cuerpo = new JPanel(new GridBagLayout());
        cuerpo.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.weightx = 0.55;
        c.insets = new Insets(0, 0, 0, 16);
        cuerpo.add(tarjetaPartidas(), c);
        c.weightx = 0.45;
        c.insets = new Insets(0, 0, 0, 0);
        cuerpo.add(tarjetaDetalle(), c);
        add(cuerpo, BorderLayout.CENTER);
    }

    private JPanel tarjetaPartidas() {
        PanelRedondeado p = new PanelRedondeado(new BorderLayout(0, 10));
        p.add(TemaUno.tituloSeccion("Partidas guardadas"), BorderLayout.NORTH);
        this.tabla.setRowHeight(28);
        this.tabla.setFont(TemaUno.texto(13, Font.PLAIN));
        this.tabla.getTableHeader().setFont(TemaUno.texto(12, Font.BOLD));
        this.tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.tabla.setFillsViewportHeight(true);
        this.tabla.getAccessibleContext().setAccessibleName("Partidas guardadas");
        p.add(new JScrollPane(this.tabla), BorderLayout.CENTER);
        BotonRedondeado auditar = TemaUno.botonPrincipal("Auditar con la semilla");
        auditar.addActionListener(e -> auditarSeleccionada());
        JPanel fila = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        fila.setOpaque(false);
        fila.add(auditar);
        p.add(fila, BorderLayout.SOUTH);
        return p;
    }

    private JPanel tarjetaDetalle() {
        PanelRedondeado p = new PanelRedondeado(new BorderLayout(0, 10));
        this.bannerResultado.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        this.bannerResultado.add(this.resultado);
        p.add(this.bannerResultado, BorderLayout.NORTH);
        this.registro.setEditable(false);
        this.registro.setFont(TemaUno.texto(13, Font.PLAIN));
        this.registro.setForeground(TemaUno.TEXTO);
        this.registro.setLineWrap(true);
        this.registro.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(this.registro);
        scroll.setPreferredSize(new Dimension(360, 300));
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    void refrescar() {
        this.modelo.setRowCount(0);
        try {
            this.partidas = new ArrayList<>(this.ventana.getDaoPartidas().obtenerTodos());
            this.partidas.sort((a, b) -> b.getFecha().compareTo(a.getFecha()));
            for (Partida p : this.partidas) {
                Jugador ganador = this.ventana.getDaoJugadores().buscarPorId(p.getIdGanador());
                this.modelo.addRow(new Object[]{p.getIdPartida(), p.getFecha(), p.getSemilla(),
                        ganador == null ? "—" : ganador.getNombre(), p.getPuntosGanador(),
                        p.isSospechaTrampa() ? "Sí" : "No"});
            }
        } catch (RuntimeException e) {
            mostrarResultado("No se pudieron leer las partidas: " + e.getMessage(), true);
        }
        if (!this.partidas.isEmpty()) {
            this.tabla.setRowSelectionInterval(0, 0);
        }
    }

    void auditarSeleccionada() {
        int fila = this.tabla.getSelectedRow();
        if (fila < 0 || fila >= this.partidas.size()) {
            mostrarResultado("Elige una partida de la lista.", true);
            return;
        }
        Partida p = this.partidas.get(fila);
        boolean sospecha = new AuditorSemilla().auditarPartidaCompleta(p.getSemilla(), p.getLogJugadas());
        if (sospecha) {
            mostrarResultado("⚠ Sospecha de trampa: el registro no coincide con la semilla " + p.getSemilla() + ".", true);
            if (!p.isSospechaTrampa()) {
                p.setSospechaTrampa(true);
                this.ventana.getDaoPartidas().actualizar(p);
                this.modelo.setValueAt("Sí", fila, 5);
            }
        } else if (p.isSospechaTrampa()) {
            mostrarResultado("El registro cuadra con la semilla, pero hubo alertas durante la partida.", true);
        } else {
            mostrarResultado("✓ Partida limpia: cada carta jugada y robada coincide con la semilla.", false);
        }

        Map<Integer, String> nombres = new HashMap<>();
        StringBuilder sb = new StringBuilder();
        String[] lineas = p.getLogJugadas() == null ? new String[0] : p.getLogJugadas().split("\n");
        if (lineas.length > 0 && lineas[0].startsWith("JUGADORES;")) {
            for (String id : lineas[0].split(";")[2].split(",")) {
                try {
                    Jugador j = this.ventana.getDaoJugadores().buscarPorId(Integer.parseInt(id.trim()));
                    if (j != null) {
                        nombres.put(j.getId(), j.getNombre());
                    }
                } catch (NumberFormatException e) {
                    // Registro alterado: se muestra la línea tal cual.
                }
            }
        }
        for (int i = 0; i < lineas.length; i++) {
            sb.append(i + 1).append(". ").append(TemaUno.lineaLegible(lineas[i], nombres)).append('\n');
        }
        this.registro.setText(sb.toString());
        this.registro.setCaretPosition(0);
    }

    private void mostrarResultado(String texto, boolean alerta) {
        this.resultado.setText("<html><div style='width:360px'>" + texto + "</div></html>");
        this.resultado.setForeground(alerta ? TemaUno.TEXTO_ALERTA : TemaUno.VERDE_OK);
        this.bannerResultado.setFondo(alerta ? TemaUno.FONDO_ALERTA : TemaUno.FONDO_OK);
    }
}
