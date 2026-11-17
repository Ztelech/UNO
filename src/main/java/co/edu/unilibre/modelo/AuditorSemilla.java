package co.edu.unilibre.modelo;

import co.edu.unilibre.dto.Carta;
import co.edu.unilibre.dto.TipoCarta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Auditoría anti-trampas (RF24-RF27). Reconstruye en memoria la baraja con la misma semilla y
 * repite las acciones registradas por {@link GestorPartidaUno}:
 * <pre>
 * JUGADORES;0;1,2,3   orden de los jugadores
 * REPARTO;0;7         reparto en ronda de 7 cartas
 * DESCARTE;0;idCarta  carta inicial del descarte (los +4 vuelven al fondo del mazo)
 * JUGAR;idJugador;idCarta
 * ROBAR;idJugador;idCarta
 * COLOR / UNO / PASAR / DENUNCIA  (informativas)
 * </pre>
 * Funciona de dos formas: en tiempo real (el gestor le pasa cada acción mientras se juega)
 * y al final, repitiendo todo el registro con {@link #auditarPartidaCompleta(long, String)}.
 */
public class AuditorSemilla {

    private Simulacion monitoreo;
    private long semillaMonitoreada;

    // ------------------------------------------------------------------ tiempo real

    /** Empieza a seguir la partida en tiempo real con la semilla y el orden de los jugadores. */
    public void iniciarMonitoreo(long semilla, List<Integer> idsJugadores) {
        this.semillaMonitoreada = semilla;
        this.monitoreo = new Simulacion(semilla, idsJugadores);
    }

    public void detenerMonitoreo() {
        this.monitoreo = null;
    }

    public boolean isMonitoreando() {
        return this.monitoreo != null;
    }

    /** Aplica una acción del registro a la simulación. Devuelve false si no coincide con la semilla. */
    public boolean registrarAccion(String linea) {
        if (this.monitoreo == null) {
            return true;
        }
        try {
            return this.monitoreo.aplicar(linea);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Mano que el jugador debería tener según la semilla y las acciones registradas. */
    public List<Carta> obtenerManoCalculada(int idJugador) {
        if (this.monitoreo == null || !this.monitoreo.manos.containsKey(idJugador)) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(this.monitoreo.manos.get(idJugador));
    }

    /**
     * RF25: la carta robada debe ser la que la semilla pone en esa posición del mazo reconstruido
     * (0 = la de arriba).
     */
    public boolean auditarCartaRobada(int idCarta, int posicionEsperadaMazo) {
        if (this.monitoreo == null) {
            return false;
        }
        List<Carta> mazo = this.monitoreo.mazo.obtenerCartasRestantes();
        if (posicionEsperadaMazo < 0) {
            return false;
        }
        if (posicionEsperadaMazo >= mazo.size()) {
            // El mazo real se recicla al agotarse; la simulación lo hará en la misma acción.
            return posicionEsperadaMazo == 0 && mazo.isEmpty();
        }
        return mazo.get(posicionEsperadaMazo).getId() == idCarta;
    }

    /** RF26: true si la semilla realmente le dio esa carta al jugador (reparto o robo registrado). */
    public boolean validarCartaPertenenciaSemilla(int idJugador, Carta carta, long semilla) {
        if (this.monitoreo == null || semilla != this.semillaMonitoreada || carta == null) {
            return false;
        }
        List<Carta> mano = this.monitoreo.manos.get(idJugador);
        return mano != null && mano.stream().anyMatch(c -> c.getId() == carta.getId());
    }

    /** RF24: true si la mano en memoria no coincide con la calculada por la semilla (alteración). */
    public boolean detectarAlteracionMemoria(List<Carta> manoJugador, List<Carta> manoCalculadaPorSemilla) {
        return !idsOrdenados(manoJugador).equals(idsOrdenados(manoCalculadaPorSemilla));
    }

    /**
     * RF27: recrea el mazo oculto con la semilla y comprueba que el mazo real tenga exactamente
     * las mismas cartas en el mismo orden y que se haya sacado la misma cantidad.
     *
     * @return true si el mazo está íntegro.
     */
    public boolean verificarIntegridadMazoRestante(long semilla, List<Carta> mazoActual, int totalCartasExtraidas) {
        if (this.monitoreo == null || semilla != this.semillaMonitoreada || mazoActual == null) {
            return false;
        }
        if (totalCartasExtraidas != this.monitoreo.extraidas) {
            return false;
        }
        List<Carta> esperado = this.monitoreo.mazo.obtenerCartasRestantes();
        if (esperado.size() != mazoActual.size()) {
            return false;
        }
        for (int i = 0; i < esperado.size(); i++) {
            if (esperado.get(i).getId() != mazoActual.get(i).getId()) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ auditoría final

    /**
     * Repite toda la partida a partir de la semilla.
     *
     * @return true si hay sospecha de trampa: una carta jugada que el jugador no tenía,
     *         un robo que no corresponde al mazo determinista o un registro malformado.
     */
    public boolean auditarPartidaCompleta(long semilla, String logAcciones) {
        if (logAcciones == null || logAcciones.isBlank()) {
            return false;
        }
        String[] lineas = logAcciones.strip().split("\n");
        String[] cabecera = lineas[0].split(";");
        if (cabecera.length != 3 || !cabecera[0].equals("JUGADORES")) {
            return true;
        }
        try {
            List<Integer> ids = new ArrayList<>();
            for (String id : cabecera[2].split(",")) {
                ids.add(Integer.parseInt(id.trim()));
            }
            Simulacion simulacion = new Simulacion(semilla, ids);
            for (int i = 1; i < lineas.length; i++) {
                if (!simulacion.aplicar(lineas[i])) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException e) {
            // Números corruptos o un mazo que se agota donde la semilla dice que no: registro alterado.
            return true;
        }
    }

    private static List<Integer> idsOrdenados(List<Carta> cartas) {
        List<Integer> ids = new ArrayList<>();
        if (cartas != null) {
            for (Carta c : cartas) {
                ids.add(c.getId());
            }
        }
        ids.sort(Integer::compare);
        return ids;
    }

    // ------------------------------------------------------------------ simulación

    /** Copia de la partida calculada solo con la semilla y las acciones registradas. */
    private static final class Simulacion {

        private final BarajaUno mazo;
        private final Map<Integer, Carta> cartasPorId = new HashMap<>();
        private final Map<Integer, List<Carta>> manos = new LinkedHashMap<>();
        private final List<Integer> orden;
        private final List<Carta> pilaDescarte = new ArrayList<>();
        private int extraidas;

        Simulacion(long semilla, List<Integer> idsJugadores) {
            this.mazo = new BarajaUno(semilla);
            this.mazo.generar108Cartas();
            for (Carta c : this.mazo.obtenerCartasRestantes()) {
                this.cartasPorId.put(c.getId(), c);
            }
            this.mazo.barajar();
            this.orden = new ArrayList<>(idsJugadores);
            for (Integer id : idsJugadores) {
                this.manos.put(id, new ArrayList<>());
            }
        }

        /** Devuelve false si la acción no cuadra con la semilla. */
        boolean aplicar(String linea) {
            String[] partes = linea.split(";");
            if (partes.length != 3) {
                return false;
            }
            String accion = partes[0];
            int idJugador = Integer.parseInt(partes[1]);
            String dato = partes[2];

            switch (accion) {
                case "JUGADORES", "COLOR", "UNO", "PASAR", "DENUNCIA" -> {
                    return true;
                }
                case "REPARTO" -> {
                    int porJugador = Integer.parseInt(dato);
                    for (int k = 0; k < porJugador * this.orden.size(); k++) {
                        this.manos.get(this.orden.get(k % this.orden.size())).add(robar());
                    }
                    return true;
                }
                case "DESCARTE" -> {
                    Carta inicial = robar();
                    while (inicial.getTipo() == TipoCarta.COMODIN_TOMA_CUATRO) {
                        this.mazo.devolverAlMazo(inicial);
                        inicial = robar();
                    }
                    this.pilaDescarte.add(inicial);
                    return inicial.getId() == Integer.parseInt(dato);
                }
                case "JUGAR" -> {
                    List<Carta> mano = this.manos.get(idJugador);
                    int idCarta = Integer.parseInt(dato);
                    if (mano == null) {
                        return false;
                    }
                    for (int i = 0; i < mano.size(); i++) {
                        if (mano.get(i).getId() == idCarta) {
                            this.pilaDescarte.add(mano.remove(i));
                            return true;
                        }
                    }
                    return false;
                }
                case "ROBAR" -> {
                    List<Carta> mano = this.manos.get(idJugador);
                    if (mano == null) {
                        return false;
                    }
                    Carta esperada = robar();
                    mano.add(esperada);
                    return esperada.getId() == Integer.parseInt(dato);
                }
                default -> {
                    return false;
                }
            }
        }

        /** Mismo procedimiento que el gestor: si el mazo se agota, recicla el descarte menos la superior. */
        private Carta robar() {
            if (this.mazo.cartasRestantes() == 0) {
                if (this.pilaDescarte.size() < 2) {
                    throw new IllegalStateException("La semilla no deja cartas para robar.");
                }
                Carta superior = this.pilaDescarte.remove(this.pilaDescarte.size() - 1);
                List<Carta> resto = new ArrayList<>(this.pilaDescarte);
                this.pilaDescarte.clear();
                this.pilaDescarte.add(superior);
                this.mazo.reciclarDescarte(resto);
            }
            this.extraidas++;
            return this.mazo.robar();
        }
    }
}
