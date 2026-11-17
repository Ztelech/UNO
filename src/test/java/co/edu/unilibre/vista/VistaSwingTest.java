package co.edu.unilibre.vista;

import co.edu.unilibre.dto.Carta;
import co.edu.unilibre.dto.ColorCarta;
import co.edu.unilibre.dto.Jugador;
import co.edu.unilibre.dto.TipoCarta;
import co.edu.unilibre.impOperaciones.BasesDePrueba;
import co.edu.unilibre.modelo.BarajaUno;
import co.edu.unilibre.modelo.GestorPartidaUno;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Recorre las pantallas Swing sin mostrarlas y guarda capturas en target/capturas.
 * Se omite en equipos sin pantalla (modo headless).
 */
@EnabledIf("hayPantalla")
public class VistaSwingTest {

    static boolean hayPantalla() {
        return !GraphicsEnvironment.isHeadless();
    }

    private static final File CARPETA = new File("target/capturas");

    private static void capturar(VentanaPrincipal v, String nombre) throws Exception {
        v.validate();
        v.getContentPane().doLayout();
        Dimension d = v.getContentPane().getSize();
        BufferedImage img = new BufferedImage(d.width, d.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        v.getContentPane().printAll(g);
        g.dispose();
        CARPETA.mkdirs();
        ImageIO.write(img, "png", new File(CARPETA, nombre + ".png"));
    }

    /** Semilla cuya carta inicial (3 jugadores) es un número, para no abrir diálogos modales. */
    private static long semillaConInicioNumerico() {
        for (long s = 1; ; s++) {
            BarajaUno b = new BarajaUno(s);
            b.generar108Cartas();
            b.barajar();
            if (b.obtenerCartasRestantes().get(21).getTipo() == TipoCarta.NUMERO) {
                return s;
            }
        }
    }

    /** Juega por los jugadores usando el modelo (los botones abren diálogos modales). */
    private static void jugarHastaElFinal(GestorPartidaUno g) {
        for (int paso = 0; paso < 3_000 && !g.isPartidaTerminada(); paso++) {
            Jugador a = g.obtenerJugadorActivo();
            Carta elegida = null;
            for (Carta c : a.getMano()) {
                boolean ilegal = c.getTipo() == TipoCarta.COMODIN_TOMA_CUATRO && !g.validarUsoTomaCuatro(a);
                if (!ilegal && c.esJugableSobre(g.obtenerCartaSuperiorDescarte(), g.getColorActivo())) {
                    elegida = c;
                    break;
                }
            }
            if (elegida == null) {
                Carta r = g.robarCartaUno(a);
                if (g.getCartaRecienRobada() != null) {
                    g.jugarCartaRecienRobada(a, true, r.getColor() == ColorCarta.NEGRO ? ColorCarta.AZUL : null);
                }
            } else {
                g.jugarTurno(a, elegida, elegida.getColor() == ColorCarta.NEGRO ? ColorCarta.AZUL : null);
            }
            if (a.getMano().size() == 1) {
                g.cantarUno(a);
            }
        }
    }

    @Test
    @DisplayName("Swing: menú → cambio de turno → mesa → resultados → auditoría, con capturas")
    public void testRecorridoCompleto() throws Exception {
        Exception[] error = new Exception[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                recorrer();
            } catch (Exception e) {
                error[0] = e;
            }
        });
        if (error[0] != null) {
            throw error[0];
        }
    }

    private void recorrer() throws Exception {
        VentanaPrincipal v = new VentanaPrincipal(BasesDePrueba.soloLocal());
        v.addNotify();
        v.setSize(1280, 800);

        // RF20: perfiles; un perfil previo con puntos para que el ranking tenga datos.
        Jugador veterano = v.getRanking().registrarOSeleccionarPerfil("Valentina");
        v.getRanking().procesarResultadosPartida(veterano, 140, List.of(veterano));
        v.getPanelMenu().agregarPerfilesDePrueba("Jeff", "Brenda", "Camila");
        v.mostrarMenu();
        assertEquals(VentanaPrincipal.MENU, v.getPantallaActual());
        capturar(v, "1-menu");

        List<Jugador> jugadores = List.of(
                v.getRanking().registrarOSeleccionarPerfil("Jeff"),
                v.getRanking().registrarOSeleccionarPerfil("Brenda"),
                v.getRanking().registrarOSeleccionarPerfil("Camila"));
        v.iniciarPartida(jugadores, semillaConInicioNumerico(), true);
        assertEquals(VentanaPrincipal.TURNO, v.getPantallaActual());
        capturar(v, "2-cambio-de-turno");

        v.mostrarMesa();
        assertEquals(VentanaPrincipal.MESA, v.getPantallaActual());
        capturar(v, "3-mesa");
        v.setSize(1024, 700); // RNF02: tamaño mínimo
        capturar(v, "3b-mesa-1024x700");
        v.setSize(1280, 800);

        GestorPartidaUno partida = v.getPartida();
        jugarHastaElFinal(partida);
        assertTrue(partida.isPartidaTerminada());
        v.terminarRonda();
        assertEquals(VentanaPrincipal.RESULTADOS, v.getPantallaActual());
        assertFalse(partida.getPartidaFinalizada().isSospechaTrampa());
        capturar(v, "4-resultados");

        v.mostrarAuditoria();
        v.getPanelAuditoria().auditarSeleccionada();
        assertEquals(VentanaPrincipal.AUDITORIA, v.getPantallaActual());
        capturar(v, "5-auditoria");

        v.mostrarMenu();
        capturar(v, "6-menu-con-ranking");
        assertEquals(1, v.getDaoPartidas().obtenerTodos().size());
        v.dispose();
    }
}
