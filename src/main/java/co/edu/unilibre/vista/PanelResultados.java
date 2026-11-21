package co.edu.unilibre.vista;

import co.edu.unilibre.dto.Jugador;
import co.edu.unilibre.modelo.GestorPartidaUno;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.stream.Collectors;

/** Fin de ronda: ganador (RF18), liquidación de puntos (RF19/RF21) y resultado de la auditoría. */
class PanelResultados extends FondoPlaya {

    private final VentanaPrincipal ventana;
    private final PanelRedondeado tarjeta = new PanelRedondeado(new GridBagLayout());

    PanelResultados(VentanaPrincipal ventana) {
        super(new GridBagLayout(), Estilo.PLAYA);
        this.ventana = ventana;
        this.tarjeta.setBorder(BorderFactory.createEmptyBorder(26, 32, 26, 32));
        add(this.tarjeta);
    }

    void mostrar(GestorPartidaUno partida, int puntos, String aviso) {
        this.tarjeta.removeAll();
        Jugador ganador = partida.verificarGanador();
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 10, 0);

        this.tarjeta.add(TemaUno.centrado(TemaUno.etiqueta("¡" + ganador.getNombre() + " gana la ronda!",
                34, Font.BOLD, TemaUno.ROJO_UNO)), c);
        String subtitulo = partida.isConteoPuntosActivo()
                ? "+" + puntos + " puntos para " + ganador.getNombre()
                : "Partida sin puntos: el ganador no suma puntos al ranking";
        this.tarjeta.add(TemaUno.centrado(TemaUno.etiqueta(subtitulo, 17, Font.BOLD, TemaUno.TEXTO)), c);

        DefaultTableModel modelo = new DefaultTableModel(new Object[]{"Jugador", "Cartas que le quedaron", "Puntos"}, 0) {
            @Override
            public boolean isCellEditable(int fila, int columna) {
                return false;
            }
        };
        for (Jugador j : partida.getJugadores()) {
            if (j == ganador) {
                continue;
            }
            String cartas = j.getMano().stream().map(TemaUno::describir).collect(Collectors.joining(", "));
            modelo.addRow(new Object[]{j.getNombre(), cartas, j.calcularPuntosMano()});
        }
        JTable tabla = new JTable(modelo);
        tabla.setRowHeight(28);
        tabla.setFont(TemaUno.texto(14, Font.PLAIN));
        tabla.getTableHeader().setFont(TemaUno.texto(12, Font.BOLD));
        tabla.getColumnModel().getColumn(1).setPreferredWidth(420);
        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setPreferredSize(new Dimension(680, Math.min(240, 32 + 28 * modelo.getRowCount())));
        this.tarjeta.add(scroll, c);

        this.tarjeta.add(estadoAuditoria(partida), c);
        if (aviso != null) {
            this.tarjeta.add(TemaUno.etiqueta(aviso, 13, Font.BOLD, TemaUno.ROJO_UNO), c);
        }

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        botones.setOpaque(false);
        BotonRedondeado nueva = TemaUno.botonPrincipal("NUEVA RONDA");
        nueva.addActionListener(e -> this.ventana.nuevaRondaMismosJugadores());
        BotonRedondeado auditoria = TemaUno.botonAzul("Ver auditoría");
        auditoria.addActionListener(e -> this.ventana.mostrarAuditoria());
        BotonRedondeado menu = TemaUno.botonClaro("Menú principal");
        menu.addActionListener(e -> this.ventana.mostrarMenu());
        botones.add(nueva);
        botones.add(auditoria);
        botones.add(menu);
        c.insets = new Insets(8, 0, 0, 0);
        this.tarjeta.add(botones, c);

        this.tarjeta.revalidate();
        this.tarjeta.repaint();
    }

    private JPanel estadoAuditoria(GestorPartidaUno partida) {
        boolean sospecha = partida.getPartidaFinalizada().isSospechaTrampa();
        PanelRedondeado p = new PanelRedondeado(new BorderLayout(0, 4),
                sospecha ? TemaUno.FONDO_ALERTA : TemaUno.FONDO_OK, 18);
        p.setBorder(BorderFactory.createEmptyBorder(10, 16, 12, 16));
        JLabel titulo = TemaUno.etiqueta(sospecha ? "⚠ Sospecha de trampa" : "✓ Auditoría de semilla limpia",
                16, Font.BOLD, sospecha ? TemaUno.TEXTO_ALERTA : TemaUno.VERDE_OK);
        p.add(titulo, BorderLayout.NORTH);
        List<String> alertas = partida.getAlertasTrampa();
        String detalle = "Partida " + partida.getIdPartida() + " · semilla " + partida.getSemilla()
                + (alertas.isEmpty() ? " · resultado guardado"
                : " · " + alertas.stream().map(a -> a.replaceFirst("^RF\\d+:\\s*", ""))
                        .collect(Collectors.joining(" | ")));
        JLabel texto = TemaUno.etiqueta("<html><div style='width:620px'>" + detalle + "</div></html>",
                12, Font.PLAIN, new Color(0x3A4A60));
        p.add(texto, BorderLayout.CENTER);
        return p;
    }
}
