package co.edu.unilibre.modelo;

import co.edu.unilibre.dto.Carta;
import co.edu.unilibre.dto.ColorCarta;
import co.edu.unilibre.dto.Jugador;
import co.edu.unilibre.dto.Partida;
import co.edu.unilibre.dto.TipoCarta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GestorPartidaUnoTest {

    private GestorPartidaUno gestor;
    private List<Jugador> jugadores;
    private final long SEMILLA_PRUEBA = 987654321L;

    private Jugador jeff;
    private Jugador brenda;
    private Jugador camila;

    @BeforeEach
    public void setUp() {
        this.gestor = new GestorPartidaUno();
        this.jugadores = new ArrayList<>();
        this.jugadores.add(new Jugador(1, "Jeff", new ArrayList<>(), false, 0, 0, 0));
        this.jugadores.add(new Jugador(2, "Brenda", new ArrayList<>(), false, 0, 0, 0));
        this.jugadores.add(new Jugador(3, "Camila", new ArrayList<>(), false, 0, 0, 0));
        this.jeff = this.jugadores.get(0);
        this.brenda = this.jugadores.get(1);
        this.camila = this.jugadores.get(2);
    }

    // ----------------------------------------------------------------- utilitarios

    /** Carta con su valor reglamentario. Ids desde 500 para no chocar con la baraja (1..108). */
    private static Carta carta(int id, ColorCarta color, TipoCarta tipo, int numero) {
        int puntos = switch (tipo) {
            case NUMERO -> numero;
            case SALTO, REVERSA, TOMA_DOS -> 20;
            default -> 50;
        };
        return new Carta(id, color, tipo, tipo == TipoCarta.NUMERO ? numero : -1, puntos);
    }

    /**
     * Mesa armada a mano (sin repartir, así que sin auditoría en tiempo real): descarte AZUL 7,
     * color activo AZUL, turno de Jeff en sentido horario. Brenda y Camila tienen dos cartas.
     */
    private void prepararMesa(long semilla) {
        this.gestor.configurarPartida(this.jugadores, semilla);
        this.gestor.setCartaDescarte(carta(500, ColorCarta.AZUL, TipoCarta.NUMERO, 7));
        this.gestor.setColorActivo(ColorCarta.AZUL);
        this.brenda.agregarCarta(carta(510, ColorCarta.VERDE, TipoCarta.NUMERO, 1));
        this.brenda.agregarCarta(carta(511, ColorCarta.VERDE, TipoCarta.NUMERO, 2));
        this.camila.agregarCarta(carta(520, ColorCarta.AMARILLO, TipoCarta.NUMERO, 1));
        this.camila.agregarCarta(carta(521, ColorCarta.AMARILLO, TipoCarta.NUMERO, 2));
    }

    private void prepararMesa() {
        prepararMesa(SEMILLA_PRUEBA);
    }

    private void partidaRepartida(long semilla) {
        this.gestor.configurarPartida(this.jugadores, semilla);
        this.gestor.repartirManosIniciales();
        this.gestor.iniciarDescarte();
    }

    private int cartasEnManos() {
        return this.jugadores.stream().mapToInt(j -> j.getMano().size()).sum();
    }

    // ----------------------------------------------------------------- RF01 configuración

    @Test
    @DisplayName("RF01: Rechaza partidas con menos de 2 o más de 10 jugadores")
    public void testRechazaCantidadInvalida() {
        assertThrows(IllegalArgumentException.class,
                () -> this.gestor.configurarPartida(List.of(new Jugador(1, "Solo")), SEMILLA_PRUEBA));
        List<Jugador> once = new ArrayList<>();
        for (int i = 1; i <= 11; i++) {
            once.add(new Jugador(i, "J" + i));
        }
        assertThrows(IllegalArgumentException.class, () -> this.gestor.configurarPartida(once, SEMILLA_PRUEBA));
    }

    @Test
    @DisplayName("RF01: Acepta los extremos de 2 y 10 jugadores")
    public void testAceptaDosYDiezJugadores() {
        assertDoesNotThrow(() -> this.gestor.configurarPartida(List.of(new Jugador(1, "A"), new Jugador(2, "B")), SEMILLA_PRUEBA));
        List<Jugador> diez = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            diez.add(new Jugador(i, "J" + i));
        }
        GestorPartidaUno otro = new GestorPartidaUno();
        otro.configurarPartida(diez, SEMILLA_PRUEBA);
        otro.repartirManosIniciales();
        otro.iniciarDescarte();
        diez.forEach(j -> assertTrue(j.getMano().size() >= 7));
    }

    // ----------------------------------------------------------------- RF02 / RF03 / RF04 reparto

    @Test
    @DisplayName("RF01 & RF03: Configurar partida reparte exactamente 7 cartas a cada jugador")
    public void testRepartoInicial7Cartas() {
        this.gestor.configurarPartida(this.jugadores, SEMILLA_PRUEBA);
        this.gestor.repartirManosIniciales();

        for (Jugador j : this.jugadores) {
            assertEquals(7, j.getMano().size(), "El jugador " + j.getNombre() + " debe tener 7 cartas.");
        }
    }

    @Test
    @DisplayName("RF03: El reparto es en ronda: la carta i del mazo barajado va al jugador i % n")
    public void testRepartoCircularDeterminista() {
        this.gestor.configurarPartida(this.jugadores, SEMILLA_PRUEBA);
        this.gestor.repartirManosIniciales();

        List<Carta> referencia = SimuladorPartida.barajaBarajada(SEMILLA_PRUEBA).obtenerCartasRestantes();
        for (int i = 0; i < 21; i++) {
            Carta recibida = this.jugadores.get(i % 3).getMano().get(i / 3);
            assertEquals(referencia.get(i).getId(), recibida.getId(), "Carta " + i + " del reparto");
        }
    }

    @Test
    @DisplayName("RF04: La carta inicial nunca es un +4 y entre manos, mazo y descarte siguen las 108 cartas")
    public void testDescarteInicial() {
        partidaRepartida(SEMILLA_PRUEBA);

        Carta descarte = this.gestor.obtenerCartaSuperiorDescarte();
        assertNotNull(descarte);
        assertNotEquals(TipoCarta.COMODIN_TOMA_CUATRO, descarte.getTipo());
        assertEquals(108, cartasEnManos() + this.gestor.cartasEnMazo() + 1);
    }

    @Test
    @DisplayName("RF04: esCartaInicialValida rechaza solo el +4")
    public void testEsCartaInicialValida() {
        assertFalse(this.gestor.esCartaInicialValida(carta(1, ColorCarta.NEGRO, TipoCarta.COMODIN_TOMA_CUATRO, -1)));
        assertTrue(this.gestor.esCartaInicialValida(carta(2, ColorCarta.NEGRO, TipoCarta.COMODIN_COLOR, -1)));
        assertTrue(this.gestor.esCartaInicialValida(carta(3, ColorCarta.ROJO, TipoCarta.SALTO, -1)));
        assertFalse(this.gestor.esCartaInicialValida(null));
    }

    @Test
    @DisplayName("RF04: Si la primera carta es un +4 vuelve al mazo y se revela la siguiente")
    public void testMasCuatroInicialVuelveAlMazo() {
        long semilla = SimuladorPartida.semillaConMasCuatroInicial();
        List<Carta> orden = SimuladorPartida.barajaBarajada(semilla).obtenerCartasRestantes();
        partidaRepartida(semilla);

        assertEquals(orden.get(22).getId(), this.gestor.obtenerCartaSuperiorDescarte().getId());
        assertEquals(108 - 21 - 1, this.gestor.cartasEnMazo(), "El +4 sigue en el mazo.");
    }

    @Test
    @DisplayName("RF04 & RF13: Un Salto inicial hace perder el turno al primer jugador")
    public void testSaltoInicial() {
        partidaRepartida(SimuladorPartida.semillaConCartaInicial(TipoCarta.SALTO));
        assertSame(this.brenda, this.gestor.obtenerJugadorActivo());
    }

    @Test
    @DisplayName("RF04 & RF14: Una Reversa inicial invierte el sentido y empieza el último jugador")
    public void testReversaInicial() {
        partidaRepartida(SimuladorPartida.semillaConCartaInicial(TipoCarta.REVERSA));
        assertFalse(this.gestor.isSentidoHorario());
        assertSame(this.camila, this.gestor.obtenerJugadorActivo());
    }

    @Test
    @DisplayName("RF04 & RF12: Un +2 inicial hace robar 2 al primer jugador y le quita el turno")
    public void testTomaDosInicial() {
        partidaRepartida(SimuladorPartida.semillaConCartaInicial(TipoCarta.TOMA_DOS));
        assertEquals(9, this.jeff.getMano().size());
        assertSame(this.brenda, this.gestor.obtenerJugadorActivo());
    }

    @Test
    @DisplayName("RF04 & RF15: Con un Comodín inicial el primer jugador elige el color antes de jugar")
    public void testComodinInicial() {
        partidaRepartida(SimuladorPartida.semillaConCartaInicial(TipoCarta.COMODIN_COLOR));
        assertTrue(this.gestor.isColorInicialPendiente());
        assertNull(this.gestor.getColorActivo());
        assertNull(this.gestor.robarCartaUno(this.jeff), "Primero debe elegir el color.");

        assertFalse(this.gestor.elegirColorInicial(this.brenda, ColorCarta.AZUL), "Solo elige el primer jugador.");
        assertFalse(this.gestor.elegirColorInicial(this.jeff, ColorCarta.NEGRO));
        assertTrue(this.gestor.elegirColorInicial(this.jeff, ColorCarta.VERDE));
        assertEquals(ColorCarta.VERDE, this.gestor.getColorActivo());
        assertFalse(this.gestor.isColorInicialPendiente());
    }

    @Test
    @DisplayName("Determinismo: Dos partidas con la misma semilla reparten las mismas manos y el mismo descarte")
    public void testMismaSemillaMismasManos() {
        partidaRepartida(SEMILLA_PRUEBA);

        List<Jugador> otros = SimuladorPartida.tresJugadores();
        GestorPartidaUno otro = new GestorPartidaUno();
        otro.configurarPartida(otros, SEMILLA_PRUEBA);
        otro.repartirManosIniciales();
        otro.iniciarDescarte();

        for (int j = 0; j < 3; j++) {
            for (int k = 0; k < 7; k++) {
                assertEquals(this.jugadores.get(j).getMano().get(k).getId(), otros.get(j).getMano().get(k).getId());
            }
        }
        assertEquals(this.gestor.obtenerCartaSuperiorDescarte().getId(), otro.obtenerCartaSuperiorDescarte().getId());
    }

    // ----------------------------------------------------------------- RF05 / RF07 turno y vista

    @Test
    @DisplayName("RF05 & RF06: Empieza el primer jugador en sentido horario y el turno es circular")
    public void testTurnoCircular() {
        this.gestor.configurarPartida(this.jugadores, SEMILLA_PRUEBA);
        assertSame(this.jeff, this.gestor.obtenerJugadorActivo());
        assertTrue(this.gestor.isSentidoHorario());

        this.gestor.avanzarTurno();
        assertSame(this.brenda, this.gestor.obtenerJugadorActivo());
        this.gestor.avanzarTurno();
        assertSame(this.camila, this.gestor.obtenerJugadorActivo());
        this.gestor.avanzarTurno();
        assertSame(this.jeff, this.gestor.obtenerJugadorActivo());
    }

    @Test
    @DisplayName("RF07: obtenerManoJugador devuelve la mano en solo lectura")
    public void testObtenerManoJugador() {
        partidaRepartida(SEMILLA_PRUEBA);
        List<Carta> mano = this.gestor.obtenerManoJugador(2);
        assertEquals(this.brenda.getMano().size(), mano.size());
        assertThrows(UnsupportedOperationException.class, () -> mano.remove(0));
        assertTrue(this.gestor.obtenerManoJugador(99).isEmpty());
    }

    // ----------------------------------------------------------------- RF08 validación

    @Test
    @DisplayName("RF08: Una jugada válida saca la carta, la pone en el descarte y pasa el turno")
    public void testJugadaValida() {
        prepararMesa();
        Carta azul3 = carta(501, ColorCarta.AZUL, TipoCarta.NUMERO, 3);
        this.jeff.agregarCarta(azul3);
        this.jeff.agregarCarta(carta(502, ColorCarta.ROJO, TipoCarta.NUMERO, 9));

        assertTrue(this.gestor.jugarTurno(this.jeff, azul3, null));
        assertEquals(1, this.jeff.getMano().size());
        assertEquals(501, this.gestor.obtenerCartaSuperiorDescarte().getId());
        assertSame(this.brenda, this.gestor.obtenerJugadorActivo());
    }

    @Test
    @DisplayName("RF08: Una carta que no coincide en color, número ni símbolo es rechazada")
    public void testJugadaInvalida() {
        prepararMesa();
        Carta rojo5 = carta(501, ColorCarta.ROJO, TipoCarta.NUMERO, 5);
        this.jeff.agregarCarta(rojo5);
        this.jeff.agregarCarta(carta(502, ColorCarta.ROJO, TipoCarta.NUMERO, 9));

        assertFalse(this.gestor.jugarTurno(this.jeff, rojo5, null));
        assertEquals(2, this.jeff.getMano().size());
        assertSame(this.jeff, this.gestor.obtenerJugadorActivo());
    }

    @Test
    @DisplayName("RF05: Nadie puede jugar fuera de su turno ni una carta que no tiene")
    public void testJugarFueraDeTurnoOCartaAjena() {
        prepararMesa();
        Carta azul4 = carta(512, ColorCarta.AZUL, TipoCarta.NUMERO, 4);
        this.brenda.agregarCarta(azul4);
        this.jeff.agregarCarta(carta(502, ColorCarta.ROJO, TipoCarta.NUMERO, 9));

        assertFalse(this.gestor.jugarTurno(this.brenda, azul4, null));
        assertFalse(this.gestor.jugarTurno(this.jeff, carta(999, ColorCarta.AZUL, TipoCarta.NUMERO, 2), null));
        assertEquals(500, this.gestor.obtenerCartaSuperiorDescarte().getId());
    }

    // ----------------------------------------------------------------- RF12-RF15 cartas de acción

    @Test
    @DisplayName("RF13: Salto hace perder el turno al siguiente jugador")
    public void testSalto() {
        prepararMesa();
        Carta salto = carta(501, ColorCarta.AZUL, TipoCarta.SALTO, -1);
        this.jeff.agregarCarta(salto);
        this.jeff.agregarCarta(carta(502, ColorCarta.ROJO, TipoCarta.NUMERO, 9));

        assertTrue(this.gestor.jugarTurno(this.jeff, salto, null));
        assertSame(this.camila, this.gestor.obtenerJugadorActivo());
    }

    @Test
    @DisplayName("RF06 & RF14: Reversa cambia el sentido a antihorario")
    public void testReversa() {
        prepararMesa();
        Carta reversa = carta(501, ColorCarta.AZUL, TipoCarta.REVERSA, -1);
        this.jeff.agregarCarta(reversa);
        this.jeff.agregarCarta(carta(502, ColorCarta.ROJO, TipoCarta.NUMERO, 9));

        assertTrue(this.gestor.jugarTurno(this.jeff, reversa, null));
        assertFalse(this.gestor.isSentidoHorario());
        assertSame(this.camila, this.gestor.obtenerJugadorActivo());
    }

    @Test
    @DisplayName("RF14: Con 2 jugadores la Reversa actúa como Salto")
    public void testReversaConDosJugadores() {
        List<Jugador> dos = new ArrayList<>(List.of(this.jeff, this.brenda));
        this.gestor.configurarPartida(dos, SEMILLA_PRUEBA);
        this.gestor.setCartaDescarte(carta(500, ColorCarta.AZUL, TipoCarta.NUMERO, 7));
        this.gestor.setColorActivo(ColorCarta.AZUL);
        Carta reversa = carta(501, ColorCarta.AZUL, TipoCarta.REVERSA, -1);
        this.jeff.agregarCarta(reversa);
        this.jeff.agregarCarta(carta(502, ColorCarta.ROJO, TipoCarta.NUMERO, 9));

        assertTrue(this.gestor.jugarTurno(this.jeff, reversa, null));
        assertSame(this.jeff, this.gestor.obtenerJugadorActivo());
    }

    @Test
    @DisplayName("RF12: +2 hace robar 2 cartas al siguiente y le quita el turno")
    public void testTomaDos() {
        prepararMesa();
        Carta tomaDos = carta(501, ColorCarta.AZUL, TipoCarta.TOMA_DOS, -1);
        this.jeff.agregarCarta(tomaDos);
        this.jeff.agregarCarta(carta(502, ColorCarta.ROJO, TipoCarta.NUMERO, 9));

        assertTrue(this.gestor.jugarTurno(this.jeff, tomaDos, null));
        assertEquals(4, this.brenda.getMano().size());
        assertSame(this.camila, this.gestor.obtenerJugadorActivo());
        assertEquals(108 - 2, this.gestor.cartasEnMazo());
    }

    @Test
    @DisplayName("RF15: El comodín de color exige elegir un color (no NEGRO) y lo deja activo")
    public void testComodinColor() {
        prepararMesa();
        Carta comodin = carta(501, ColorCarta.NEGRO, TipoCarta.COMODIN_COLOR, -1);
        this.jeff.agregarCarta(comodin);
        this.jeff.agregarCarta(carta(502, ColorCarta.ROJO, TipoCarta.NUMERO, 9));

        assertFalse(this.gestor.jugarTurno(this.jeff, comodin, null));
        assertFalse(this.gestor.jugarTurno(this.jeff, comodin, ColorCarta.NEGRO));
        assertTrue(this.gestor.jugarTurno(this.jeff, comodin, ColorCarta.VERDE));
        assertEquals(ColorCarta.VERDE, this.gestor.getColorActivo());
    }

    // ----------------------------------------------------------------- RF09 +4

    @Test
    @DisplayName("RF09: No permitir jugar Comodín +4 si el jugador tiene el color activo")
    public void testRestriccionComodinMasCuatro() {
        Jugador jugador = this.jugadores.get(0);
        jugador.getMano().clear();
        jugador.agregarCarta(new Carta(1, ColorCarta.ROJO, TipoCarta.NUMERO, 5, 5));
        jugador.agregarCarta(new Carta(2, ColorCarta.NEGRO, TipoCarta.COMODIN_TOMA_CUATRO, -1, 50));

        this.gestor.configurarPartida(this.jugadores, SEMILLA_PRUEBA);
        this.gestor.setColorActivo(ColorCarta.ROJO);

        Carta cartaIntentada = jugador.getMano().get(1); // El comodín +4
        boolean resultadoJugada = this.gestor.jugarTurno(jugador, cartaIntentada, ColorCarta.AZUL);

        assertFalse(resultadoJugada, "La regla oficial prohibe lanzar +4 teniendo cartas del color activo.");
        assertFalse(this.gestor.validarUsoTomaCuatro(jugador));
    }

    @Test
    @DisplayName("RF09 & RF15: +4 legal: el siguiente roba 4, pierde el turno y cambia el color")
    public void testComodinMasCuatroLegal() {
        prepararMesa();
        Carta masCuatro = carta(501, ColorCarta.NEGRO, TipoCarta.COMODIN_TOMA_CUATRO, -1);
        this.jeff.agregarCarta(carta(502, ColorCarta.ROJO, TipoCarta.NUMERO, 5));
        this.jeff.agregarCarta(masCuatro);

        assertTrue(this.gestor.validarUsoTomaCuatro(this.jeff));
        assertTrue(this.gestor.jugarTurno(this.jeff, masCuatro, ColorCarta.VERDE));
        assertEquals(6, this.brenda.getMano().size());
        assertSame(this.camila, this.gestor.obtenerJugadorActivo());
        assertEquals(ColorCarta.VERDE, this.gestor.getColorActivo());
    }

    // ----------------------------------------------------------------- RF10 / RF11 robar

    @Test
    @DisplayName("RF10 & RF11: Si la carta robada es jugable, solo esa se puede jugar en el mismo turno")
    public void testRobadaJugableSeJuega() {
        long semilla = SimuladorPartida.semillaConPrimeraCartaNumero();
        Carta primera = SimuladorPartida.barajaBarajada(semilla).obtenerCartasRestantes().get(0);
        prepararMesa(semilla);
        this.gestor.setColorActivo(primera.getColor() == ColorCarta.NEGRO ? ColorCarta.AZUL : primera.getColor());
        Carta otra = carta(502, this.gestor.getColorActivo(), TipoCarta.NUMERO, 9);
        this.jeff.agregarCarta(otra);
        this.jeff.agregarCarta(carta(503, ColorCarta.ROJO, TipoCarta.NUMERO, 8));

        Carta robada = this.gestor.robarCartaUno(this.jeff);

        assertEquals(primera.getId(), robada.getId());
        assertSame(robada, this.gestor.getCartaRecienRobada());
        assertFalse(this.gestor.jugarTurno(this.jeff, otra, null), "Tras robar solo se puede jugar la robada.");
        assertNull(this.gestor.robarCartaUno(this.jeff), "Solo se roba una vez por turno.");
        assertTrue(this.gestor.jugarCartaRecienRobada(this.jeff, true, ColorCarta.ROJO));
        assertEquals(robada.getId(), this.gestor.obtenerCartaSuperiorDescarte().getId());
        assertNull(this.gestor.getCartaRecienRobada());
    }

    @Test
    @DisplayName("RF11: El jugador puede no jugar la carta robada: se la queda y pasa el turno")
    public void testRobadaJugableNoSeJuega() {
        long semilla = SimuladorPartida.semillaConPrimeraCartaNumero();
        Carta primera = SimuladorPartida.barajaBarajada(semilla).obtenerCartasRestantes().get(0);
        prepararMesa(semilla);
        this.gestor.setColorActivo(primera.getColor() == ColorCarta.NEGRO ? ColorCarta.AZUL : primera.getColor());
        this.jeff.agregarCarta(carta(503, ColorCarta.ROJO, TipoCarta.NUMERO, 8));

        this.gestor.robarCartaUno(this.jeff);
        assertTrue(this.gestor.jugarCartaRecienRobada(this.jeff, false, null));

        assertEquals(2, this.jeff.getMano().size());
        assertSame(this.brenda, this.gestor.obtenerJugadorActivo());
    }

    @Test
    @DisplayName("RF11: Si la carta robada no es jugable el turno termina solo")
    public void testRobadaNoJugableTerminaTurno() {
        long semilla = SimuladorPartida.semillaConPrimeraCartaNumero();
        Carta primera = SimuladorPartida.barajaBarajada(semilla).obtenerCartasRestantes().get(0);
        prepararMesa(semilla);
        ColorCarta otroColor = primera.getColor() == ColorCarta.ROJO ? ColorCarta.AZUL : ColorCarta.ROJO;
        int otroNumero = (primera.getNumero() + 1) % 10;
        this.gestor.setCartaDescarte(carta(599, otroColor, TipoCarta.NUMERO, otroNumero));
        this.gestor.setColorActivo(otroColor);
        this.jeff.agregarCarta(carta(503, ColorCarta.VERDE, TipoCarta.SALTO, -1));

        this.gestor.robarCartaUno(this.jeff);

        assertNull(this.gestor.getCartaRecienRobada());
        assertSame(this.brenda, this.gestor.obtenerJugadorActivo());
        assertEquals(2, this.jeff.getMano().size());
    }

    @Test
    @DisplayName("RF11: pasarTurno sin haber robado lanza IllegalStateException")
    public void testPasarSinRobar() {
        prepararMesa();
        assertThrows(IllegalStateException.class, () -> this.gestor.pasarTurno());
    }

    // ----------------------------------------------------------------- RF16 / RF17 ¡UNO!

    @Test
    @DisplayName("RF16: Solo se puede declarar UNO con exactamente una carta")
    public void testCantarUno() {
        prepararMesa();
        this.gestor.cantarUno(this.brenda); // 2 cartas
        assertFalse(this.brenda.isDijoUno());

        this.brenda.getMano().remove(1);
        this.gestor.cantarUno(this.brenda); // 1 carta
        assertTrue(this.brenda.isDijoUno());
    }

    @Test
    @DisplayName("RF16 & RF17: Denunciar a jugador que no cantó UNO al quedar con 1 carta")
    public void testDenunciaFaltaDeUnoPenalizaDosCartas() {
        Jugador acusado = this.jugadores.get(0);
        Jugador acusador = this.jugadores.get(1);

        acusado.getMano().clear();
        acusado.agregarCarta(new Carta(1, ColorCarta.VERDE, TipoCarta.NUMERO, 2, 2)); // Solo 1 carta
        acusado.setDijoUno(false); // No presionó el botón

        this.gestor.configurarPartida(this.jugadores, SEMILLA_PRUEBA);
        this.gestor.denunciarNoUno(acusador, acusado);

        assertEquals(3, acusado.getMano().size(), "El acusado debio recibir 2 cartas de penalizacion.");
    }

    @Test
    @DisplayName("RF17: La denuncia vale hasta que el siguiente jugador actúa")
    public void testPlazoDeDenuncia() {
        prepararMesa();
        Carta azul3 = carta(501, ColorCarta.AZUL, TipoCarta.NUMERO, 3);
        this.jeff.agregarCarta(azul3);
        this.jeff.agregarCarta(carta(502, ColorCarta.ROJO, TipoCarta.NUMERO, 9));
        Carta azul5 = carta(512, ColorCarta.AZUL, TipoCarta.NUMERO, 5);
        this.brenda.agregarCarta(azul5);

        this.gestor.jugarTurno(this.jeff, azul3, null); // Jeff queda con 1 carta y no canta
        assertTrue(this.gestor.esDenunciable(this.jeff));

        this.gestor.jugarTurno(this.brenda, azul5, null); // Brenda actúa: vence el plazo
        assertFalse(this.gestor.esDenunciable(this.jeff));
        this.gestor.denunciarNoUno(this.camila, this.jeff);
        assertEquals(1, this.jeff.getMano().size(), "Fuera de plazo no hay penalización.");
    }

    @Test
    @DisplayName("RF17: Denunciar dentro del plazo penaliza; a quien cantó UNO o a uno mismo, no")
    public void testDenunciasDentroDelPlazo() {
        prepararMesa();
        Carta azul3 = carta(501, ColorCarta.AZUL, TipoCarta.NUMERO, 3);
        this.jeff.agregarCarta(azul3);
        this.jeff.agregarCarta(carta(502, ColorCarta.ROJO, TipoCarta.NUMERO, 9));
        this.gestor.jugarTurno(this.jeff, azul3, null);

        this.gestor.denunciarNoUno(this.jeff, this.jeff);
        assertEquals(1, this.jeff.getMano().size(), "Nadie se denuncia a sí mismo.");

        this.gestor.denunciarNoUno(this.camila, this.jeff);
        assertEquals(3, this.jeff.getMano().size());

        this.brenda.getMano().remove(1);
        this.gestor.cantarUno(this.brenda);
        this.gestor.denunciarNoUno(this.camila, this.brenda);
        assertEquals(1, this.brenda.getMano().size(), "Brenda sí cantó ¡UNO!");
    }

    // ----------------------------------------------------------------- RF18 / RF19 fin

    @Test
    @DisplayName("RF18: Quien juega su última carta gana y no se aceptan más jugadas")
    public void testVictoria() {
        prepararMesa();
        Carta ultima = carta(501, ColorCarta.AZUL, TipoCarta.NUMERO, 3);
        this.jeff.agregarCarta(ultima);

        assertNull(this.gestor.verificarGanador());
        assertTrue(this.gestor.jugarTurno(this.jeff, ultima, null));
        assertTrue(this.gestor.isPartidaTerminada());
        assertSame(this.jeff, this.gestor.verificarGanador());

        Carta azul3 = carta(512, ColorCarta.AZUL, TipoCarta.NUMERO, 3);
        this.brenda.agregarCarta(azul3);
        assertFalse(this.gestor.jugarTurno(this.brenda, azul3, null));
        assertNull(this.gestor.robarCartaUno(this.brenda));
    }

    private void ganaJeffCon76PuntosEnMesa(boolean conteoPuntos) {
        this.gestor.configurarPartida(this.jugadores, SEMILLA_PRUEBA, conteoPuntos);
        this.gestor.setCartaDescarte(carta(500, ColorCarta.AZUL, TipoCarta.NUMERO, 7));
        this.gestor.setColorActivo(ColorCarta.AZUL);
        this.brenda.agregarCarta(carta(510, ColorCarta.ROJO, TipoCarta.SALTO, -1));          // 20
        this.brenda.agregarCarta(carta(511, ColorCarta.VERDE, TipoCarta.NUMERO, 4));         // 4
        this.camila.agregarCarta(carta(520, ColorCarta.NEGRO, TipoCarta.COMODIN_COLOR, -1)); // 50
        this.camila.agregarCarta(carta(521, ColorCarta.AMARILLO, TipoCarta.NUMERO, 2));      // 2
        Carta ultima = carta(501, ColorCarta.AZUL, TipoCarta.NUMERO, 3);
        this.jeff.agregarCarta(ultima);
        this.gestor.jugarTurno(this.jeff, ultima, null);
    }

    @Test
    @DisplayName("RF19 & RF21: finalizarRonda suma las manos rivales y entrega la Partida cerrada")
    public void testFinalizarRonda() {
        ganaJeffCon76PuntosEnMesa(true);

        assertEquals(76, this.gestor.finalizarRonda());

        Partida partida = this.gestor.getPartidaFinalizada();
        assertEquals(this.gestor.getIdPartida(), partida.getIdPartida());
        assertEquals(SEMILLA_PRUEBA, partida.getSemilla());
        assertEquals(LocalDate.now(), partida.getFecha());
        assertEquals(1, partida.getIdGanador());
        assertEquals(76, partida.getPuntosGanador());
        assertTrue(partida.getLogJugadas().contains("JUGAR;1;501"));
        // La mesa se armó a mano (cartas 500+ que la semilla nunca repartió): el auditor lo detecta.
        assertTrue(partida.isSospechaTrampa());
    }

    @Test
    @DisplayName("RF19: Con el conteo de puntos desactivado la ronda da 0 puntos")
    public void testConteoDePuntosOpcional() {
        ganaJeffCon76PuntosEnMesa(false);
        assertFalse(this.gestor.isConteoPuntosActivo());
        assertEquals(76, this.gestor.calcularPuntosRonda(), "El cálculo existe, pero no se asigna.");
        assertEquals(0, this.gestor.finalizarRonda());
        assertEquals(0, this.gestor.getPartidaFinalizada().getPuntosGanador());
    }

    @Test
    @DisplayName("finalizarRonda sin ganador lanza IllegalStateException")
    public void testFinalizarSinGanador() {
        prepararMesa();
        assertThrows(IllegalStateException.class, () -> this.gestor.finalizarRonda());
    }

    @Test
    @DisplayName("Registro: cada jugada y cada robo (incluidas penalizaciones) queda en el log")
    public void testLogRegistraJugadasYRobos() {
        prepararMesa();
        Carta tomaDos = carta(501, ColorCarta.AZUL, TipoCarta.TOMA_DOS, -1);
        this.jeff.agregarCarta(tomaDos);
        this.jeff.agregarCarta(carta(502, ColorCarta.ROJO, TipoCarta.NUMERO, 9));

        this.gestor.jugarTurno(this.jeff, tomaDos, null);

        String[] lineas = this.gestor.getLogAcciones().split("\n");
        assertEquals("JUGADORES;0;1,2,3", lineas[0]);
        assertEquals("JUGAR;1;501", lineas[1]);
        assertTrue(lineas[2].startsWith("ROBAR;2;"));
        assertTrue(lineas[3].startsWith("ROBAR;2;"));
        assertEquals(4, lineas.length);
    }
}
