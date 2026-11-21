package co.edu.unilibre.vista;

import co.edu.unilibre.dto.Carta;
import co.edu.unilibre.dto.Jugador;
import co.edu.unilibre.impOperaciones.GestorConexion;
import co.edu.unilibre.impOperaciones.ImpOperacionesJugadorBD;
import co.edu.unilibre.impOperaciones.ImpOperacionesPartidaBD;
import co.edu.unilibre.impOperaciones.SincronizadorNube;
import co.edu.unilibre.modelo.GestorPartidaUno;
import co.edu.unilibre.modelo.GestorRanking;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ventana única del juego. Cambia entre pantallas con un CardLayout y coordina la partida
 * local (varias personas por turnos en el mismo computador). - Uso de Gemini para funciones del menú
 */
public class VentanaPrincipal extends JFrame {

    static final String MENU = "menu";
    static final String TURNO = "turno";
    static final String MESA = "mesa";
    static final String RESULTADOS = "resultados";
    static final String AUDITORIA = "auditoria";

    private final GestorConexion conexion;
    private final ImpOperacionesJugadorBD daoJugadores;
    private final ImpOperacionesPartidaBD daoPartidas;
    private final GestorRanking ranking;

    private final CardLayout tarjetas = new CardLayout();
    private final JPanel contenedor = new JPanel(this.tarjetas);
    private final PanelMenu panelMenu;
    private final PanelCambioTurno panelCambioTurno;
    private final PanelMesa panelMesa;
    private final PanelResultados panelResultados;
    private final PanelAuditoria panelAuditoria;

    private GestorPartidaUno partida;
    private List<Jugador> jugadoresPartida = new ArrayList<>();
    private boolean conteoPuntos = true;
    private String pantallaActual = MENU;

    public VentanaPrincipal(GestorConexion conexion) {
        super("UNO · Edición Playa");
        this.conexion = conexion;
        this.daoJugadores = new ImpOperacionesJugadorBD(conexion);
        this.daoPartidas = new ImpOperacionesPartidaBD(conexion);
        this.ranking = new GestorRanking(this.daoJugadores, conexion::isNubeDisponible);

        this.panelMenu = new PanelMenu(this);
        this.panelCambioTurno = new PanelCambioTurno(this);
        this.panelMesa = new PanelMesa(this);
        this.panelResultados = new PanelResultados(this);
        this.panelAuditoria = new PanelAuditoria(this);
        this.contenedor.add(this.panelMenu, MENU);
        this.contenedor.add(this.panelCambioTurno, TURNO);
        this.contenedor.add(this.panelMesa, MESA);
        this.contenedor.add(this.panelResultados, RESULTADOS);
        this.contenedor.add(this.panelAuditoria, AUDITORIA);
        setContentPane(this.contenedor);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Espera a que terminen las réplicas hacia Neon (lo pendiente queda en la cola de H2).
                VentanaPrincipal.this.conexion.cerrar();
                dispose();
                System.exit(0);
            }
        });
        setMinimumSize(new Dimension(1024, 700));
        setSize(1280, 800);
        setLocationRelativeTo(null);
        mostrarMenu();
        sincronizarConNube();
    }

    // ------------------------------------------------------------------ Neon

    /**
     * Al abrir: une perfiles con otros computadores, sube lo que falte y baja el ranking global.
     * Corre en segundo plano; al terminar se refresca el menú.
     */
    private void sincronizarConNube() {
        if (!this.conexion.isModoHibridoConNube()) {
            return;
        }
        new SincronizadorNube(this.conexion).sincronizarEnSegundoPlano()
                .thenAccept(r -> SwingUtilities.invokeLater(() -> {
                    if (MENU.equals(this.pantallaActual)) {
                        this.panelMenu.refrescar();
                    }
                }));
    }

    /** Texto de aviso para el menú cuando las partidas no están llegando a Neon (null si todo va bien). */
    String avisoNube() {
        if (!this.conexion.isNubeConfigurada()) {
            return "Sin conexión a Neon: las partidas solo se guardan en este computador "
                    + "(falta el archivo config.local.properties).";
        }
        if (!this.conexion.isModoHibridoConNube()) {
            return null;
        }
        int pendientes = this.conexion.getPendientesDeSincronizar();
        if (!this.conexion.isNubeDisponible()) {
            return "Neon no responde: lo jugado se subirá cuando vuelva la conexión"
                    + (pendientes > 0 ? " (" + pendientes + " pendientes)." : ".");
        }
        return pendientes > 0 ? pendientes + " registros pendientes por subir a Neon." : null;
    }

    // ------------------------------------------------------------------ navegación

    void mostrarMenu() {
        this.panelMenu.refrescar();
        mostrar(MENU);
        if (this.conexion.isModoHibridoConNube()) {
            // Cuando terminen de subir los resultados de la última partida, se refresca el ranking global.
            this.conexion.ejecutarEnHiloNube(() -> true)
                    .thenRun(() -> SwingUtilities.invokeLater(() -> {
                        if (MENU.equals(this.pantallaActual)) {
                            this.panelMenu.refrescar();
                        }
                    }));
        }
    }

    void mostrarAuditoria() {
        this.panelAuditoria.refrescar();
        mostrar(AUDITORIA);
    }

    void mostrarCambioTurno(Jugador anterior, String resumen) {
        this.panelCambioTurno.mostrar(anterior, resumen);
        mostrar(TURNO);
    }

    void mostrarMesa() {
        mostrar(MESA);
        this.panelMesa.nuevoTurno();
    }

    private void mostrar(String pantalla) {
        this.pantallaActual = pantalla;
        this.tarjetas.show(this.contenedor, pantalla);
    }

    String getPantallaActual() {
        return this.pantallaActual;
    }

    // ------------------------------------------------------------------ partida

    /** Empieza una ronda nueva con los perfiles elegidos (RF01, RF03, RF04). */
    void iniciarPartida(List<Jugador> jugadores, long semilla, boolean conteoPuntos) {
        for (Jugador j : jugadores) {
            j.getMano().clear();
            j.setDijoUno(false);
        }
        this.jugadoresPartida = new ArrayList<>(jugadores);
        this.conteoPuntos = conteoPuntos;
        this.partida = new GestorPartidaUno(this.daoPartidas);
        this.partida.setOyenteAlertas(mensaje -> SwingUtilities.invokeLater(() -> mostrarAlertaTrampa(mensaje)));
        this.partida.configurarPartida(this.jugadoresPartida, semilla, conteoPuntos);
        this.partida.repartirManosIniciales();
        this.partida.iniciarDescarte();

        Carta inicial = this.partida.obtenerCartaSuperiorDescarte();
        String resumen = "Comienza la partida. Carta inicial: " + TemaUno.describir(inicial) + "."
                + switch (inicial.getTipo()) {
                    case SALTO -> " El primer jugador pierde su turno.";
                    case REVERSA -> " Se juega en sentido antihorario.";
                    case TOMA_DOS -> " " + this.jugadoresPartida.get(0).getNombre() + " roba 2 y pierde su turno.";
                    case COMODIN_COLOR -> " El primer jugador elige el color.";
                    default -> "";
                };
        mostrarCambioTurno(null, resumen);
    }

    /** Otra ronda con los mismos jugadores y una semilla nueva. */
    void nuevaRondaMismosJugadores() {
        iniciarPartida(this.jugadoresPartida, semillaAleatoria(), this.conteoPuntos);
    }

    /** RF18-RF22: cierra la ronda, guarda la partida y actualiza el ranking. */
    void terminarRonda() {
        int puntos = this.partida.finalizarRonda();
        String aviso = null;
        try {
            this.ranking.procesarResultadosPartida(this.partida.verificarGanador(), puntos, this.jugadoresPartida);
        } catch (RuntimeException e) {
            aviso = "No se pudo actualizar el ranking: " + e.getMessage();
        }
        this.panelResultados.mostrar(this.partida, puntos, aviso);
        mostrar(RESULTADOS);
    }

    void mostrarAlertaTrampa(String mensaje) {
        // El modelo antepone el número de requisito (p. ej. "RF26: "); al jugador no le sirve.
        String texto = mensaje.replaceFirst("^RF\\d+:\\s*", "");
        JOptionPane.showMessageDialog(this, texto, "Alerta de trampa", JOptionPane.WARNING_MESSAGE);
        if (MESA.equals(this.pantallaActual)) {
            this.panelMesa.mostrar();
        }
    }

    static long semillaAleatoria() {
        return ThreadLocalRandom.current().nextLong(100_000_000L, 1_000_000_000L);
    }

    // ------------------------------------------------------------------ acceso para los paneles

    PanelMenu getPanelMenu() {
        return this.panelMenu;
    }

    PanelMesa getPanelMesa() {
        return this.panelMesa;
    }

    PanelAuditoria getPanelAuditoria() {
        return this.panelAuditoria;
    }

    GestorPartidaUno getPartida() {
        return this.partida;
    }

    List<Jugador> getJugadoresPartida() {
        return this.jugadoresPartida;
    }

    GestorRanking getRanking() {
        return this.ranking;
    }

    ImpOperacionesJugadorBD getDaoJugadores() {
        return this.daoJugadores;
    }

    ImpOperacionesPartidaBD getDaoPartidas() {
        return this.daoPartidas;
    }

    GestorConexion getConexion() {
        return this.conexion;
    }

    Map<Integer, String> nombresPartida() {
        Map<Integer, String> nombres = new HashMap<>();
        for (Jugador j : this.jugadoresPartida) {
            nombres.put(j.getId(), j.getNombre());
        }
        return nombres;
    }
}
