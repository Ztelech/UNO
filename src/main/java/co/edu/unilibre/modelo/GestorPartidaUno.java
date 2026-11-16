package co.edu.unilibre.modelo;

import co.edu.unilibre.dto.Carta;
import co.edu.unilibre.dto.ColorCarta;
import co.edu.unilibre.dto.Jugador;
import co.edu.unilibre.dto.Partida;
import co.edu.unilibre.dto.TipoCarta;
import co.edu.unilibre.impOperaciones.ImpOperacionesPartidaBD;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orquesta una partida de UNO en memoria. No usa JDBC: delega la persistencia en
 * {@link ImpOperacionesPartidaBD} (jugadas en tiempo real y partida cerrada) y en {@link GestorRanking}.
 * Si la persistencia falla, la partida sigue: el error solo se informa.
 */
public class GestorPartidaUno {

    public static final int MINIMO_JUGADORES = 2;
    public static final int MAXIMO_JUGADORES = 10;
    public static final int CARTAS_INICIALES = 7;
    public static final int CARTAS_PENALIZACION_UNO = 2;

    private List<Jugador> jugadores;
    private BarajaUno barajaRobo;
    private Stack<Carta> pilaDescarte;
    private int indiceJugadorActivo;
    private boolean sentidoHorario;
    private ColorCarta colorActivo;
    private long semilla;
    private StringBuilder logAcciones;

    private String idPartida;
    private int numeroAccion;
    private boolean roboEnTurno;
    private Jugador ganador;
    private Partida partidaFinalizada;
    private int puntosRonda;

    private final ImpOperacionesPartidaBD daoPartidas;
    private final GestorRanking gestorRanking;
    private final AuditorSemilla auditor = new AuditorSemilla();

    /** Partida solo en memoria (sin base de datos). */
    public GestorPartidaUno() {
        this(null, null);
    }

    public GestorPartidaUno(ImpOperacionesPartidaBD daoPartidas, GestorRanking gestorRanking) {
        this.daoPartidas = daoPartidas;
        this.gestorRanking = gestorRanking;
    }

    // ------------------------------------------------------------------ preparación

    /** RQ01: de 2 a 10 jugadores. Crea y baraja la BarajaUno con la semilla. */
    public void configurarPartida(List<Jugador> jugadores, long semilla) {
        if (jugadores == null || jugadores.size() < MINIMO_JUGADORES || jugadores.size() > MAXIMO_JUGADORES) {
            throw new IllegalArgumentException("Una partida necesita entre " + MINIMO_JUGADORES
                    + " y " + MAXIMO_JUGADORES + " jugadores.");
        }
        this.jugadores = jugadores;
        this.semilla = semilla;
        this.barajaRobo = new BarajaUno(semilla);
        this.barajaRobo.generar108Cartas();
        this.barajaRobo.barajar();
        this.pilaDescarte = new Stack<>();
        this.indiceJugadorActivo = 0;
        this.sentidoHorario = true;
        this.colorActivo = null;
        this.logAcciones = new StringBuilder();
        this.idPartida = "P-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.numeroAccion = 0;
        this.roboEnTurno = false;
        this.ganador = null;
        this.partidaFinalizada = null;
        this.puntosRonda = 0;

        String ids = jugadores.stream().map(j -> String.valueOf(j.getId())).collect(Collectors.joining(","));
        registrar("JUGADORES", 0, ids);
    }

    /** RQ03: reparte 7 cartas en ronda (la carta i va al jugador i % n). */
    public void repartirManosIniciales() {
        exigirPartidaConfigurada();
        int total = CARTAS_INICIALES * this.jugadores.size();
        for (int i = 0; i < total; i++) {
            this.jugadores.get(i % this.jugadores.size()).agregarCarta(this.barajaRobo.robar());
        }
        registrar("REPARTO", 0, String.valueOf(CARTAS_INICIALES));
    }

    /** RQ04: la primera carta del descarte siempre es un número; las especiales van al fondo del mazo. */
    public void iniciarDescarte() {
        exigirPartidaConfigurada();
        Carta inicial = this.barajaRobo.robar();
        while (inicial.getTipo() != TipoCarta.NUMERO) {
            this.barajaRobo.colocarAlFondo(inicial);
            inicial = this.barajaRobo.robar();
        }
        this.pilaDescarte.push(inicial);
        this.colorActivo = inicial.getColor();
        registrar("DESCARTE", 0, String.valueOf(inicial.getId()));
    }

    // ------------------------------------------------------------------ turno

    /** RQ08, RQ09, RQ14, RQ18. colorElegido solo se usa con comodines. */
    public boolean jugarTurno(Jugador jugador, Carta carta, ColorCarta colorElegido) {
        if (this.jugadores == null || this.ganador != null || carta == null) {
            return false;
        }
        if (jugador != getJugadorActivo()) {
            return false;
        }
        int indice = indiceEnMano(jugador, carta.getId());
        if (indice < 0) {
            return false; // Anti-trampas: solo se juegan cartas que realmente están en la mano.
        }
        Carta real = jugador.getMano().get(indice);
        if (!real.esJugableSobre(getCartaDescarte(), this.colorActivo)) {
            return false;
        }
        if (real.getColor() == ColorCarta.NEGRO) {
            if (colorElegido == null || colorElegido == ColorCarta.NEGRO) {
                return false;
            }
            if (real.getTipo() == TipoCarta.COMODIN_TOMA_CUATRO && jugador.tieneColor(this.colorActivo)) {
                return false; // RQ09: +4 solo si no se tiene el color activo.
            }
        }

        jugador.jugarCarta(indice);
        this.pilaDescarte.push(real);
        this.colorActivo = real.getColor() == ColorCarta.NEGRO ? colorElegido : real.getColor();
        this.roboEnTurno = false;
        registrar("JUGAR", jugador.getId(), String.valueOf(real.getId()));

        if (jugador.getMano().isEmpty()) {
            this.ganador = jugador; // RQ18
            return true;
        }
        if (jugador.getMano().size() > 1) {
            jugador.setDijoUno(false);
        }
        aplicarEfecto(real);
        return true;
    }

    /** Roba una carta para el jugador en turno (una sola vez por turno). Devuelve null si no le corresponde. */
    public Carta robarCartaTurno(Jugador jugador) {
        if (this.jugadores == null || this.ganador != null || jugador != getJugadorActivo() || this.roboEnTurno) {
            return null;
        }
        Carta robada = robarDelMazo();
        jugador.agregarCarta(robada);
        jugador.setDijoUno(false);
        this.roboEnTurno = true;
        registrar("ROBAR", jugador.getId(), String.valueOf(robada.getId()));
        return robada;
    }

    /** Pasa el turno del jugador activo. Solo se permite después de robar. */
    public void pasarTurno() {
        if (!this.roboEnTurno) {
            throw new IllegalStateException("Debe robar una carta antes de pasar el turno.");
        }
        registrar("PASAR", getJugadorActivo().getId(), "0");
        avanzarTurno();
    }

    /** RQ05 y RQ06: turno circular según el sentido actual. */
    public void avanzarTurno() {
        exigirPartidaConfigurada();
        int n = this.jugadores.size();
        this.indiceJugadorActivo = this.sentidoHorario
                ? (this.indiceJugadorActivo + 1) % n
                : (this.indiceJugadorActivo - 1 + n) % n;
        this.roboEnTurno = false;
    }

    // ------------------------------------------------------------------ ¡UNO!

    /** RQ16: se puede cantar con 2 cartas (antes de jugar la penúltima) o con 1. */
    public void cantarUno(Jugador jugador) {
        int cartas = jugador.getMano().size();
        if (cartas == 1 || cartas == 2) {
            jugador.setDijoUno(true);
            registrar("UNO", jugador.getId(), "0");
        }
    }

    /** RQ17: si el acusado tiene 1 carta y no cantó ¡UNO!, roba 2 cartas de penalización. */
    public void denunciarNoUno(Jugador acusador, Jugador acusado) {
        exigirPartidaConfigurada();
        if (acusado.getMano().size() == 1 && !acusado.isDijoUno()) {
            registrar("DENUNCIA", acusador.getId(), String.valueOf(acusado.getId()));
            penalizar(acusado, CARTAS_PENALIZACION_UNO);
        }
    }

    // ------------------------------------------------------------------ fin de partida

    /** RQ18: devuelve el ganador o null si la partida sigue. */
    public Jugador verificarGanador() {
        return this.ganador;
    }

    /** RQ19: puntos de la ronda = suma de las manos de los rivales del ganador. */
    public int calcularPuntosRonda() {
        if (this.ganador == null) {
            throw new IllegalStateException("La ronda todavía no tiene ganador.");
        }
        int total = 0;
        for (Jugador j : this.jugadores) {
            if (j != this.ganador) {
                total += j.calcularPuntosMano();
            }
        }
        return total;
    }

    /**
     * Cierra la ronda: audita la semilla, guarda la Partida completa y actualiza el ranking.
     *
     * @return puntos ganados por el ganador.
     */
    public int finalizarRonda() {
        if (this.ganador == null) {
            throw new IllegalStateException("No se puede finalizar una ronda sin ganador.");
        }
        if (this.partidaFinalizada != null) {
            return this.puntosRonda; // Ya se cerró: no se guarda dos veces.
        }
        this.puntosRonda = calcularPuntosRonda();
        String log = this.logAcciones.toString();
        boolean sospecha = this.auditor.auditarPartidaCompleta(this.semilla, log);
        this.partidaFinalizada = new Partida(this.idPartida, this.semilla, LocalDate.now(),
                this.ganador.getId(), this.puntosRonda, sospecha, log);

        if (this.daoPartidas != null) {
            try {
                this.daoPartidas.crear(this.partidaFinalizada);
            } catch (RuntimeException e) {
                System.err.println("No se pudo guardar la partida " + this.idPartida + ": " + e.getMessage());
            }
        }
        if (this.gestorRanking != null) {
            try {
                this.gestorRanking.procesarResultadosPartida(this.ganador, this.puntosRonda, this.jugadores);
            } catch (RuntimeException e) {
                System.err.println("No se pudo actualizar el ranking: " + e.getMessage());
            }
        }
        return this.puntosRonda;
    }

    // ------------------------------------------------------------------ internos

    private void aplicarEfecto(Carta carta) {
        switch (carta.getTipo()) {
            case SALTO -> {
                avanzarTurno();
                avanzarTurno();
            }
            case REVERSA -> {
                this.sentidoHorario = !this.sentidoHorario;
                if (this.jugadores.size() == 2) {
                    // Con dos jugadores la reversa actúa como salto: vuelve a jugar el mismo.
                    this.roboEnTurno = false;
                } else {
                    avanzarTurno();
                }
            }
            case TOMA_DOS -> {
                avanzarTurno();
                penalizar(getJugadorActivo(), 2);
                avanzarTurno();
            }
            case COMODIN_TOMA_CUATRO -> {
                avanzarTurno();
                penalizar(getJugadorActivo(), 4);
                avanzarTurno();
            }
            default -> avanzarTurno();
        }
    }

    private void penalizar(Jugador jugador, int cantidad) {
        for (int i = 0; i < cantidad; i++) {
            Carta c = robarDelMazo();
            jugador.agregarCarta(c);
            registrar("ROBAR", jugador.getId(), String.valueOf(c.getId()));
        }
        jugador.setDijoUno(false);
    }

    /** Si el mazo se agota, recarga con el descarte (menos la carta superior). */
    private Carta robarDelMazo() {
        if (this.barajaRobo.cartasRestantes() == 0) {
            if (this.pilaDescarte.size() < 2) {
                throw new IllegalStateException("No quedan cartas para robar.");
            }
            Carta superior = this.pilaDescarte.pop();
            List<Carta> resto = new ArrayList<>(this.pilaDescarte);
            this.pilaDescarte.clear();
            this.pilaDescarte.push(superior);
            this.barajaRobo.recargarDescarte(resto);
        }
        return this.barajaRobo.robar();
    }

    private static int indiceEnMano(Jugador jugador, int idCarta) {
        List<Carta> mano = jugador.getMano();
        for (int i = 0; i < mano.size(); i++) {
            if (mano.get(i).getId() == idCarta) {
                return i;
            }
        }
        return -1;
    }

    /** Agrega la acción al log y la registra al instante en la base (sin detener la partida si falla). */
    private void registrar(String accion, int idJugador, String dato) {
        String linea = accion + ";" + idJugador + ";" + dato;
        if (this.logAcciones.length() > 0) {
            this.logAcciones.append('\n');
        }
        this.logAcciones.append(linea);
        this.numeroAccion++;
        if (this.daoPartidas != null) {
            try {
                this.daoPartidas.registrarJugadaInstantanea(this.idPartida, this.numeroAccion, idJugador, linea);
            } catch (RuntimeException e) {
                System.err.println("No se pudo registrar la jugada en tiempo real: " + e.getMessage());
            }
        }
    }

    private void exigirPartidaConfigurada() {
        if (this.jugadores == null) {
            throw new IllegalStateException("Primero llame a configurarPartida.");
        }
    }

    // ------------------------------------------------------------------ consultas

    public Jugador getJugadorActivo() {
        return this.jugadores == null ? null : this.jugadores.get(this.indiceJugadorActivo);
    }

    public List<Jugador> getJugadores() {
        return this.jugadores;
    }

    public boolean isSentidoHorario() {
        return this.sentidoHorario;
    }

    public Carta getCartaDescarte() {
        return this.pilaDescarte == null || this.pilaDescarte.isEmpty() ? null : this.pilaDescarte.peek();
    }

    /** Solo para preparar escenarios (pruebas o reanudación): coloca una carta sobre el descarte. */
    public void setCartaDescarte(Carta carta) {
        exigirPartidaConfigurada();
        this.pilaDescarte.push(carta);
    }

    public ColorCarta getColorActivo() {
        return this.colorActivo;
    }

    public void setColorActivo(ColorCarta colorActivo) {
        this.colorActivo = colorActivo;
    }

    public int cartasEnMazo() {
        return this.barajaRobo == null ? 0 : this.barajaRobo.cartasRestantes();
    }

    public boolean isPartidaTerminada() {
        return this.ganador != null;
    }

    public long getSemilla() {
        return this.semilla;
    }

    public String getIdPartida() {
        return this.idPartida;
    }

    public String getLogAcciones() {
        return this.logAcciones == null ? "" : this.logAcciones.toString();
    }

    /** Partida cerrada por finalizarRonda (null antes de cerrar). */
    public Partida getPartidaFinalizada() {
        return this.partidaFinalizada;
    }
}
