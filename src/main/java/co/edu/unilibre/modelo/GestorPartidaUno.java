package co.edu.unilibre.modelo;

import co.edu.unilibre.dto.Carta;
import co.edu.unilibre.dto.ColorCarta;
import co.edu.unilibre.dto.Jugador;
import co.edu.unilibre.dto.Partida;
import co.edu.unilibre.dto.TipoCarta;
import co.edu.unilibre.impOperaciones.ImpOperacionesPartidaBD;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Orquesta una partida local de UNO en memoria (2 a 10 personas en el mismo computador).
 * No usa JDBC: delega la persistencia en {@link ImpOperacionesPartidaBD}. Si la persistencia
 * falla, la partida sigue: el error solo se informa.
 * <p>
 * Desde {@link #repartirManosIniciales()} el {@link AuditorSemilla} sigue la partida en tiempo real
 * (RF24-RF27). Si se arma una mesa a mano sin repartir (escenarios de prueba), no hay monitoreo.
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
    private Carta cartaRecienRobada;
    private boolean conteoPuntosActivo;
    private long semilla;
    private StringBuilder logAcciones;

    private final ImpOperacionesPartidaBD daoPartidas;
    private final AuditorSemilla auditor = new AuditorSemilla();

    private String idPartida;
    private int numeroAccion;
    private int cartasExtraidas;
    private boolean colorInicialPendiente;
    private Jugador ganador;
    private Partida partidaFinalizada;
    private int puntosRonda;
    /** RF17: jugadores con una carta y sin ¡UNO! cuyo plazo de denuncia ya pasó. */
    private final Set<Integer> plazoDenunciaVencido = new HashSet<>();
    private final Set<String> alertasTrampa = new LinkedHashSet<>();
    private Consumer<String> oyenteAlertas;

    /** Partida solo en memoria (sin base de datos). */
    public GestorPartidaUno() {
        this(null);
    }

    public GestorPartidaUno(ImpOperacionesPartidaBD daoPartidas) {
        this.daoPartidas = daoPartidas;
    }

    // ------------------------------------------------------------------ preparación

    /** RF01 con conteo de puntos activo (RF19). */
    public void configurarPartida(List<Jugador> jugadores, long semilla) {
        configurarPartida(jugadores, semilla, true);
    }

    /** RF01: de 2 a 10 jugadores. Crea y baraja la BarajaUno con la semilla. RF19: puntos opcionales. */
    public void configurarPartida(List<Jugador> jugadores, long semilla, boolean conteoPuntosActivo) {
        if (jugadores == null || jugadores.size() < MINIMO_JUGADORES || jugadores.size() > MAXIMO_JUGADORES) {
            throw new IllegalArgumentException("Una partida necesita entre " + MINIMO_JUGADORES
                    + " y " + MAXIMO_JUGADORES + " jugadores.");
        }
        this.jugadores = jugadores;
        this.semilla = semilla;
        this.conteoPuntosActivo = conteoPuntosActivo;
        this.barajaRobo = new BarajaUno(semilla);
        this.barajaRobo.generar108Cartas();
        this.barajaRobo.barajar();
        this.pilaDescarte = new Stack<>();
        this.indiceJugadorActivo = 0;
        this.sentidoHorario = true;
        this.colorActivo = null;
        this.cartaRecienRobada = null;
        this.logAcciones = new StringBuilder();
        this.idPartida = "P-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.numeroAccion = 0;
        this.cartasExtraidas = 0;
        this.colorInicialPendiente = false;
        this.ganador = null;
        this.partidaFinalizada = null;
        this.puntosRonda = 0;
        this.plazoDenunciaVencido.clear();
        this.alertasTrampa.clear();
        this.auditor.detenerMonitoreo();

        registrar("JUGADORES", 0, idsJugadores().stream().map(String::valueOf).collect(Collectors.joining(",")));
    }

    /** RF03: reparte 7 cartas en ronda (la carta i va al jugador i % n) y activa la auditoría en tiempo real. */
    public void repartirManosIniciales() {
        exigirPartidaConfigurada();
        this.auditor.iniciarMonitoreo(this.semilla, idsJugadores());
        int total = CARTAS_INICIALES * this.jugadores.size();
        for (int i = 0; i < total; i++) {
            this.jugadores.get(i % this.jugadores.size()).agregarCarta(sacarDelMazo());
        }
        registrar("REPARTO", 0, String.valueOf(CARTAS_INICIALES));
    }

    /** RF04: un +4 nunca puede ser la carta inicial. */
    public boolean esCartaInicialValida(Carta carta) {
        return carta != null && carta.getTipo() != TipoCarta.COMODIN_TOMA_CUATRO;
    }

    /**
     * RF04: revela la primera carta. Un +4 vuelve al mazo y se saca otra. Las demás cartas
     * especiales aplican su efecto oficial sobre el primer jugador.
     */
    public void iniciarDescarte() {
        exigirPartidaConfigurada();
        Carta inicial = sacarDelMazo();
        while (!esCartaInicialValida(inicial)) {
            this.barajaRobo.devolverAlMazo(inicial);
            inicial = sacarDelMazo();
        }
        this.pilaDescarte.push(inicial);
        registrar("DESCARTE", 0, String.valueOf(inicial.getId()));

        int n = this.jugadores.size();
        switch (inicial.getTipo()) {
            case SALTO -> {
                this.colorActivo = inicial.getColor();
                this.indiceJugadorActivo = 1 % n; // El primer jugador pierde el turno.
            }
            case REVERSA -> {
                this.colorActivo = inicial.getColor();
                this.sentidoHorario = false;
                this.indiceJugadorActivo = n - 1; // Empieza quien está "antes" y se juega al revés.
            }
            case TOMA_DOS -> {
                this.colorActivo = inicial.getColor();
                penalizar(this.jugadores.get(0), 2); // El primer jugador roba 2 y pierde el turno.
                this.indiceJugadorActivo = 1 % n;
            }
            case COMODIN_COLOR -> {
                this.colorActivo = null;
                this.colorInicialPendiente = true; // El primer jugador elige el color (RF15).
            }
            default -> this.colorActivo = inicial.getColor();
        }
    }

    /** RF15: con un Comodín como carta inicial, el primer jugador elige el color antes de jugar. */
    public boolean elegirColorInicial(Jugador jugador, ColorCarta color) {
        if (!this.colorInicialPendiente || jugador != obtenerJugadorActivo()
                || color == null || color == ColorCarta.NEGRO) {
            return false;
        }
        this.colorActivo = color;
        this.colorInicialPendiente = false;
        registrar("COLOR", jugador.getId(), color.name());
        return true;
    }

    // ------------------------------------------------------------------ jugar

    /** RF08 y RF09: valida que el jugador pueda lanzar un +4 (no tiene cartas del color activo). */
    public boolean validarUsoTomaCuatro(Jugador jugador) {
        return !jugador.tieneColor(this.colorActivo);
    }

    /** RF08, RF09, RF12-RF15, RF18 y RF26. colorElegido solo se usa con comodines. */
    public boolean jugarTurno(Jugador jugador, Carta carta, ColorCarta colorElegido) {
        if (!puedeActuar(jugador) || carta == null || this.cartaRecienRobada != null) {
            return false; // Tras robar solo se puede decidir sobre la carta robada (RF11).
        }
        return jugarCarta(jugador, carta, colorElegido);
    }

    /** RF10: el jugador activo roba una carta. Si no se puede jugar, su turno termina (RF11). */
    public Carta robarCartaUno(Jugador jugador) {
        if (!puedeActuar(jugador) || this.cartaRecienRobada != null) {
            return null;
        }
        vencerPlazosDeDenuncia(jugador);
        Carta robada = robarPara(jugador);
        boolean jugable = robada.esJugableSobre(obtenerCartaSuperiorDescarte(), this.colorActivo)
                && (robada.getTipo() != TipoCarta.COMODIN_TOMA_CUATRO || validarUsoTomaCuatro(jugador));
        if (jugable) {
            this.cartaRecienRobada = robada;
        } else {
            registrar("PASAR", jugador.getId(), "0");
            avanzarTurno();
        }
        return robada;
    }

    /** RF11: el jugador decide si juega la carta que acaba de robar; si no, pasa el turno. */
    public boolean jugarCartaRecienRobada(Jugador jugador, boolean aceptarJugada, ColorCarta colorElegido) {
        if (this.cartaRecienRobada == null || jugador != obtenerJugadorActivo()) {
            return false;
        }
        if (!aceptarJugada) {
            pasarTurno();
            return true;
        }
        Carta carta = this.cartaRecienRobada;
        this.cartaRecienRobada = null;
        if (jugarCarta(jugador, carta, colorElegido)) {
            return true;
        }
        this.cartaRecienRobada = carta; // p. ej. un comodín sin color elegido
        return false;
    }

    /** Termina el turno sin jugar la carta recién robada. */
    public void pasarTurno() {
        if (this.cartaRecienRobada == null) {
            throw new IllegalStateException("Solo se puede pasar el turno después de robar una carta jugable.");
        }
        this.cartaRecienRobada = null;
        registrar("PASAR", obtenerJugadorActivo().getId(), "0");
        avanzarTurno();
    }

    /** RF05 y RF06: turno circular según el sentido actual. */
    public void avanzarTurno() {
        exigirPartidaConfigurada();
        int n = this.jugadores.size();
        this.indiceJugadorActivo = this.sentidoHorario
                ? (this.indiceJugadorActivo + 1) % n
                : (this.indiceJugadorActivo - 1 + n) % n;
        this.cartaRecienRobada = null;
    }

    // ------------------------------------------------------------------ ¡UNO!

    /** RF16: se declara UNO cuando queda exactamente una carta en la mano. */
    public void cantarUno(Jugador jugador) {
        if (jugador.getMano().size() == 1 && !jugador.isDijoUno()) {
            jugador.setDijoUno(true);
            this.plazoDenunciaVencido.remove(jugador.getId());
            registrar("UNO", jugador.getId(), "0");
        }
    }

    /**
     * RF17: si el acusado tiene una carta, no cantó ¡UNO! y el siguiente jugador aún no ha actuado,
     * roba 2 cartas de penalización.
     */
    public void denunciarNoUno(Jugador acusador, Jugador acusado) {
        exigirPartidaConfigurada();
        if (acusador != acusado && esDenunciable(acusado)) {
            registrar("DENUNCIA", acusador.getId(), String.valueOf(acusado.getId()));
            penalizar(acusado, CARTAS_PENALIZACION_UNO);
        }
    }

    /** RF17: el acusado puede ser denunciado en este momento. */
    public boolean esDenunciable(Jugador acusado) {
        return acusado.getMano().size() == 1 && !acusado.isDijoUno()
                && !this.plazoDenunciaVencido.contains(acusado.getId());
    }

    // ------------------------------------------------------------------ fin de partida

    /** RF18: devuelve el ganador o null si la ronda sigue. */
    public Jugador verificarGanador() {
        return this.ganador;
    }

    /** RF19/RF21: suma de las cartas que quedaron en las manos de los rivales del ganador. */
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
     * Cierra la ronda: audita la semilla y guarda la Partida completa.
     *
     * @return puntos para el ganador (0 si el conteo de puntos está desactivado, RF19).
     */
    public int finalizarRonda() {
        if (this.ganador == null) {
            throw new IllegalStateException("No se puede finalizar una ronda sin ganador.");
        }
        if (this.partidaFinalizada != null) {
            return this.puntosRonda; // Ya se cerró: no se guarda dos veces.
        }
        this.puntosRonda = this.conteoPuntosActivo ? calcularPuntosRonda() : 0;
        String log = this.logAcciones.toString();
        boolean sospecha = !this.alertasTrampa.isEmpty() || this.auditor.auditarPartidaCompleta(this.semilla, log);
        this.partidaFinalizada = new Partida(this.idPartida, this.semilla, LocalDate.now(),
                this.ganador.getId(), this.puntosRonda, sospecha, log);

        if (this.daoPartidas != null) {
            try {
                this.daoPartidas.crear(this.partidaFinalizada);
            } catch (RuntimeException e) {
                System.err.println("No se pudo guardar la partida " + this.idPartida + ": " + e.getMessage());
            }
        }
        return this.puntosRonda;
    }

    // ------------------------------------------------------------------ auditoría en tiempo real

    /**
     * RF24 y RF27: compara las manos y el mazo restante con lo que dicta la semilla.
     * Se ejecuta después de cada acción; también se puede llamar desde la interfaz.
     *
     * @return alertas nuevas encontradas en esta verificación.
     */
    public List<String> verificarIntegridad() {
        List<String> nuevas = new ArrayList<>();
        if (!this.auditor.isMonitoreando()) {
            return nuevas;
        }
        for (Jugador j : this.jugadores) {
            if (this.auditor.detectarAlteracionMemoria(j.getMano(), this.auditor.obtenerManoCalculada(j.getId()))) {
                alertar("RF24: la mano de " + j.getNombre() + " no coincide con la semilla (alteración en memoria).", nuevas);
            }
        }
        if (!this.auditor.verificarIntegridadMazoRestante(this.semilla,
                this.barajaRobo.obtenerCartasRestantes(), this.cartasExtraidas)) {
            alertar("RF27: el mazo oculto no coincide con el que recrea la semilla.", nuevas);
        }
        return nuevas;
    }

    /** Recibe cada alerta de trampa nueva (la interfaz la muestra al jugador). */
    public void setOyenteAlertas(Consumer<String> oyenteAlertas) {
        this.oyenteAlertas = oyenteAlertas;
    }

    public List<String> getAlertasTrampa() {
        return new ArrayList<>(this.alertasTrampa);
    }

    // ------------------------------------------------------------------ internos

    private boolean puedeActuar(Jugador jugador) {
        return this.jugadores != null && this.ganador == null && !this.colorInicialPendiente
                && jugador == obtenerJugadorActivo();
    }

    private boolean jugarCarta(Jugador jugador, Carta carta, ColorCarta colorElegido) {
        int indice = indiceEnMano(jugador, carta.getId());
        if (indice < 0) {
            return false; // Solo se juegan cartas que realmente están en la mano.
        }
        Carta real = jugador.getMano().get(indice);
        if (!real.esJugableSobre(obtenerCartaSuperiorDescarte(), this.colorActivo)) {
            return false;
        }
        if (real.getColor() == ColorCarta.NEGRO) {
            if (colorElegido == null || colorElegido == ColorCarta.NEGRO) {
                return false;
            }
            if (real.getTipo() == TipoCarta.COMODIN_TOMA_CUATRO && !validarUsoTomaCuatro(jugador)) {
                return false; // RF09
            }
        }
        if (this.auditor.isMonitoreando()
                && !this.auditor.validarCartaPertenenciaSemilla(jugador.getId(), real, this.semilla)) {
            alertar("RF26: " + jugador.getNombre() + " intentó jugar una carta que la semilla nunca le dio (id "
                    + real.getId() + "). Jugada rechazada.", null);
            return false;
        }

        vencerPlazosDeDenuncia(jugador);
        jugador.jugarCarta(indice);
        this.pilaDescarte.push(real);
        this.colorActivo = real.getColor() == ColorCarta.NEGRO ? colorElegido : real.getColor();
        registrar("JUGAR", jugador.getId(), String.valueOf(real.getId()));
        if (real.getColor() == ColorCarta.NEGRO) {
            registrar("COLOR", jugador.getId(), colorElegido.name());
        }

        if (jugador.getMano().isEmpty()) {
            this.ganador = jugador; // RF18
            return true;
        }
        if (jugador.getMano().size() == 1) {
            this.plazoDenunciaVencido.remove(jugador.getId()); // Abre su plazo de denuncia (RF17).
        } else {
            jugador.setDijoUno(false);
        }
        aplicarEfecto(real);
        return true;
    }

    private void aplicarEfecto(Carta carta) {
        switch (carta.getTipo()) {
            case SALTO -> {
                avanzarTurno();
                avanzarTurno();
            }
            case REVERSA -> {
                this.sentidoHorario = !this.sentidoHorario;
                if (this.jugadores.size() > 2) {
                    avanzarTurno();
                }
                // Con dos jugadores la reversa actúa como salto: vuelve a jugar el mismo.
            }
            case TOMA_DOS -> {
                avanzarTurno();
                penalizar(obtenerJugadorActivo(), 2);
                avanzarTurno();
            }
            case COMODIN_TOMA_CUATRO -> {
                avanzarTurno();
                penalizar(obtenerJugadorActivo(), 4);
                avanzarTurno();
            }
            default -> avanzarTurno();
        }
    }

    /** RF17: cuando un jugador actúa, vence el plazo para denunciar a los demás. */
    private void vencerPlazosDeDenuncia(Jugador quienActua) {
        for (Jugador j : this.jugadores) {
            if (j != quienActua && j.getMano().size() == 1 && !j.isDijoUno()) {
                this.plazoDenunciaVencido.add(j.getId());
            }
        }
    }

    private void penalizar(Jugador jugador, int cantidad) {
        for (int i = 0; i < cantidad; i++) {
            robarPara(jugador);
        }
    }

    /** Roba del mazo para el jugador, la audita (RF25) y la registra. */
    private Carta robarPara(Jugador jugador) {
        Carta c = sacarDelMazo();
        if (this.auditor.isMonitoreando() && !this.auditor.auditarCartaRobada(c.getId(), 0)) {
            alertar("RF25: la carta robada por " + jugador.getNombre() + " no es la que predice la semilla.", null);
        }
        jugador.agregarCarta(c);
        jugador.setDijoUno(false);
        this.plazoDenunciaVencido.remove(jugador.getId());
        registrar("ROBAR", jugador.getId(), String.valueOf(c.getId()));
        return c;
    }

    /** Si el mazo se agota, recicla el descarte (menos la carta superior). */
    private Carta sacarDelMazo() {
        if (this.barajaRobo.cartasRestantes() == 0) {
            if (this.pilaDescarte.size() < 2) {
                throw new IllegalStateException("No quedan cartas para robar.");
            }
            Carta superior = this.pilaDescarte.pop();
            List<Carta> resto = new ArrayList<>(this.pilaDescarte);
            this.pilaDescarte.clear();
            this.pilaDescarte.push(superior);
            this.barajaRobo.reciclarDescarte(resto);
        }
        this.cartasExtraidas++;
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

    /** Agrega la acción al log, la pasa al auditor y la registra al instante en la base. */
    private void registrar(String accion, int idJugador, String dato) {
        String linea = accion + ";" + idJugador + ";" + dato;
        if (this.logAcciones.length() > 0) {
            this.logAcciones.append('\n');
        }
        this.logAcciones.append(linea);
        this.numeroAccion++;

        if (this.auditor.isMonitoreando()) {
            if (!this.auditor.registrarAccion(linea)) {
                alertar("La acción " + linea + " no coincide con la semilla; se detiene la auditoría en tiempo real.", null);
                this.auditor.detenerMonitoreo();
            } else if (!accion.equals("JUGADORES")) {
                verificarIntegridad();
            }
        }
        if (this.daoPartidas != null) {
            try {
                this.daoPartidas.registrarJugadaInstantanea(this.idPartida, this.numeroAccion, idJugador, linea);
            } catch (RuntimeException e) {
                System.err.println("No se pudo registrar la jugada en tiempo real: " + e.getMessage());
            }
        }
    }

    private void alertar(String mensaje, List<String> nuevas) {
        if (this.alertasTrampa.add(mensaje)) {
            if (nuevas != null) {
                nuevas.add(mensaje);
            }
            if (this.oyenteAlertas != null) {
                this.oyenteAlertas.accept(mensaje);
            }
        }
    }

    private List<Integer> idsJugadores() {
        return this.jugadores.stream().map(Jugador::getId).collect(Collectors.toList());
    }

    private void exigirPartidaConfigurada() {
        if (this.jugadores == null) {
            throw new IllegalStateException("Primero llame a configurarPartida.");
        }
    }

    // ------------------------------------------------------------------ consultas

    /** RF05: jugador con el turno activo. */
    public Jugador obtenerJugadorActivo() {
        return this.jugadores == null ? null : this.jugadores.get(this.indiceJugadorActivo);
    }

    /** RF07: carta superior del montón de descarte. */
    public Carta obtenerCartaSuperiorDescarte() {
        return this.pilaDescarte == null || this.pilaDescarte.isEmpty() ? null : this.pilaDescarte.peek();
    }

    /** RF07: mano del jugador (solo lectura). */
    public List<Carta> obtenerManoJugador(int idJugador) {
        for (Jugador j : this.jugadores) {
            if (j.getId() == idJugador) {
                return Collections.unmodifiableList(new ArrayList<>(j.getMano()));
            }
        }
        return Collections.emptyList();
    }

    /** Solo para preparar escenarios de prueba: coloca una carta sobre el descarte. */
    public void setCartaDescarte(Carta carta) {
        exigirPartidaConfigurada();
        this.pilaDescarte.push(carta);
    }

    public List<Jugador> getJugadores() {
        return this.jugadores;
    }

    public boolean isSentidoHorario() {
        return this.sentidoHorario;
    }

    public ColorCarta getColorActivo() {
        return this.colorActivo;
    }

    public void setColorActivo(ColorCarta colorActivo) {
        this.colorActivo = colorActivo;
    }

    public Carta getCartaRecienRobada() {
        return this.cartaRecienRobada;
    }

    public boolean isColorInicialPendiente() {
        return this.colorInicialPendiente;
    }

    public boolean isConteoPuntosActivo() {
        return this.conteoPuntosActivo;
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
