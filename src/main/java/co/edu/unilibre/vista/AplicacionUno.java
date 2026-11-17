package co.edu.unilibre.vista;

import co.edu.unilibre.impOperaciones.GestorConexion;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Punto de entrada del juego UNO (interfaz Swing). */
public final class AplicacionUno {

    private AplicacionUno() {
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Si falla, se usa el aspecto por defecto de Swing.
        }
        // H2 local (./db_unogame) + réplica asíncrona en Neon si hay DB_URL o config.local.properties.
        GestorConexion conexion = new GestorConexion();
        conexion.inicializarBaseDeDatos();
        SwingUtilities.invokeLater(() -> new VentanaPrincipal(conexion).setVisible(true));
    }
}
