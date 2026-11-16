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
        this.jugadores.add(new Jugador(1, "Jeff", false, new ArrayList<>(), false, 0, 0, 0));
        this.jugadores.add(new Jugador(2, "Brenda", false, new ArrayList<>(), false, 0, 0, 0));
        this.jugadores.add(new Jugador(3, "Camila", false, new ArrayList<>(), false, 0, 0, 0));
        this.jeff = this.jugadores.get(0);
        this.brenda = this.jugadores.get(1);
        this.camila = this.jugadores.get(2);
    }

    // ----------------------------------------------------------------- utilitarios

    private static Jugador nuevoJugador(int id, String nombre) {
        return new Jugador(id, nombre, false, new ArrayList<>(), false, 0, 0, 0);
    }

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
     * Mesa controlada: partida configurada sin repartir, descarte AZUL 7, color activo AZUL,
     * turno de Jeff en sentido horario. Brenda y Camila tienen dos cartas de relleno.
     */
    private void prepararMesa() {
        this.gestor.configurarPartida(this.jugadores, SEMILLA_PRUEBA);
        this.gestor.setCartaDescarte(carta(500, ColorCarta.AZUL, TipoCarta.NUMERO, 7));
        this.gestor.setColorActivo(ColorCarta.AZUL);
        this.brenda.agregarCarta(carta(510, ColorCarta.VERDE, TipoCarta.NUMERO, 1));
        this.brenda.agregarCarta(carta(511, ColorCarta.VERDE, TipoCarta.NUMERO, 2));
        this.camila.agregarCarta(carta(520, ColorCarta.AMARILLO, TipoCarta.NUMERO, 1));
        this.camila.agregarCarta(carta(521, ColorCarta.AMARILLO, TipoCarta.NUMERO, 2));
    }

    // ----------------------------------------------------------------- RQ01 configuración

    @Test
    @DisplayName("RQ01: Rechaza partidas con menos de 2 o más de 10 jugadores")
    public void testRechazaCantidadInvalida() {
        assertThrows(IllegalArgumentException.class,
                () -> this.gestor.configurarPartida(List.of(nuevoJugador(1, "Solo")), SEMILLA_PRUEBA));
        List<Jugador> once = new ArrayList<>();
        for (int i = 1; i <= 11; i++) {
            once.add(nuevoJugador(i, "J" + i));
        }
        assertThrows(IllegalArgumentException.class, () -> this.gestor.configurarPartida(once, SEMILLA_PRUEBA));
    }

    @Test
    @DisplayName("RQ01: Acepta los extremos de 2 y 10 jugadores")
    public void testAceptaDosYDiezJugadores() {
        assertDoesNotThrow(() -> this.gestor.configurarPartida(List.of(nuevoJugador(1, "A"), nuevoJugador(2, "B")), SEMILLA_PRUEBA));
        List<Jugador> diez = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            diez.add(nuevoJugador(i, "J" + i));
        }
        assertDoesNotThrow(() -> new GestorPartidaUno().configurarPartida(diez, SEMILLA_PRUEBA));
    }

    // ----------------------------------------------------------------- RQ03 / RQ04 reparto

    @Test
    @DisplayName("RQ01 & RQ03: Configurar partida reparte exactamente 7 cartas a cada jugador")
    public void testRepartoInicial7Cartas() {
        this.gestor.configurarPartida(this.jugadores, SEMILLA_PRUEBA);
        this.gestor.repartirManosIniciales();

        for (Jugador j : this.jugadores) {
            assertEquals(7, j.getMano().size(), "El jugador " + j.getNombre() + " debe tener 7 cartas.");
        }
    }

    @Test
    @DisplayName("RQ03: El reparto es en ronda: la carta i del mazo barajado va al jugador i % n")
    public void testRepartoCircularDeterminista() {
        this.gestor.configurarPartida(this.jugadores, SEMILLA_PRUEBA);
        this.gestor.repartirManosIniciales();

        BarajaUno referencia = new BarajaUno(SEMILLA_PRUEBA);
        referencia.generar108Cartas();
        referencia.barajar();
        for (int i = 0; i < 21; i++) {
            Carta esperada = referencia.robar();
            Carta recibida = this.jugadores.get(i % 3).getMano().get(i / 3);
            assertEquals(esperada.getId(), recibida.getId(), "Carta " + i + " del reparto");
        }
    }

    @Test
    @DisplayName("RQ04: El descarte inicial es de tipo NUMERO y fija el color activo")
    public void testDescarteInicial() {
        this.gestor.configurarPartida(this.jugadores, SEMILLA_PRUEBA);
        this.gestor.repartirManosIniciales();
        this.gestor.iniciarDescarte();

        Carta descarte = this.gestor.getCartaDescarte();
        assertNotNull(descarte);
        assertEquals(TipoCarta.NUMERO, descarte.getTipo());
        assertEquals(descarte.getColor(), this.gestor.getColorActivo());
        assertEquals(108 - 21 - 1, this.gestor.cartasEnMazo());
    }

    @Test
    @DisplayName("Determinismo: Dos partidas con la misma semilla reparten las mismas manos y el mismo descarte")
    public void testMismaSemillaMismasManos() {
        this.gestor.configurarPartida(this.jugadores, SEMILLA_PRUEBA);
        this.gestor.repartirManosIniciales();
        this.gestor.iniciarDescarte();

        List<Jugador> otros = List.of(nuevoJugador(1, "Jeff"), nuevoJugador(2, "Brenda"), nuevoJugador(3, "Camila"));
        GestorPartidaUno otro = new GestorPartidaUno();
        otro.configurarPartida(otros, SEMILLA_PRUEBA);
        otro.repartirManosIniciales();
        otro.iniciarDescarte();

        for (int j = 0; j < 3; j++) {
            for (int k = 0; k < 7; k++) {
                assertEquals(this.jugadores.get(j).getMano().get(k).getId(), otros.get(j).getMano().get(k).getId());
            }
        }
        assertEquals(this.gestor.getCartaDescarte().getId(), otro.getCartaDescarte().getId());
    }

    // ----------------------------------------------------------------- RQ05 / RQ06 turnos

    @Test
    @DisplayName("RQ05: Empieza el primer jugador en sentido horario y el turno es circular")
    public void testTurnoCircular() {
        this.gestor.configurarPartida(this.jugadores, SEMILLA_PRUEBA);
        assertSame(this.jeff, this.gestor.getJugadorActivo());
        assertTrue(this.gestor.isSentidoHorario());

        this.gestor.avanzarTurno();
        assertSame(this.brenda, this.gestor.getJugadorActivo());
        this.gestor.avanzarTurno();
        assertSame(this.camila, this.gestor.getJugadorActivo());
        this.gestor.avanzarTurno();
        assertSame(this.jeff, this.gestor.getJugadorActivo());
    }

    // ----------------------------------------------------------------- RQ08 validación

    @Test
    @DisplayName("RQ08: Una jugada válida saca la carta, la pone en el descarte y pasa el turno")
    public void testJugadaValida() {
        prepararMesa();
        Carta azul3 = carta(501, ColorCarta.AZUL, TipoCarta.NUMERO, 3);
        this.jeff.agregarCarta(azul3);
        this.jeff.agregarCarta(carta(502, ColorCarta.ROJO, TipoCarta.NUMERO, 9));

        assertTrue(this.gestor.jugarTurno(this.jeff, azul3, null));
        assertEquals(1, this.jeff.getMano().size());
        assertEquals(501, this.gestor.getCartaDescarte().getId());
        assertSame(this.brenda, this.gestor.getJugadorActivo());
    }

    @Test
    @DisplayName("RQ08: Una carta que no coincide en color, número ni tipo es rechazada")
    public void testJugadaInvalida() {
        prepararMesa();
        Carta rojo5 = carta(501, ColorCarta.ROJO, TipoCarta.NUMERO, 5);
        this.jeff.agregarCarta(rojo5);
        this.jeff.agregarCarta(carta(502, ColorCarta.ROJO, TipoCarta.NUMERO, 9));

        assertFalse(this.gestor.jugarTurno(this.jeff, rojo5, null));
        assertEquals(2, this.jeff.getMano().size());
        assertEquals(500, this.gestor.getCartaDescarte().getId());
        assertSame(this.jeff, this.gestor.getJugadorActivo());
    }

    @Test
    @DisplayName("RQ05: Nadie puede jugar fuera de su turno")
    public void testJugarFueraDeTurno() {
        prepararMesa();
        Carta azul4 = carta(512, ColorCarta.AZUL, TipoCarta.NUMERO, 4);
        this.brenda.agregarCarta(azul4);

        assertFalse(this.gestor.jugarTurno(this.brenda, azul4, null));
        assertEquals(3, this.brenda.getMano().size());
    }

    @Test
    @DisplayName("Anti-trampas: No se puede jugar una carta que no está en la mano")
    public void testCartaAjenaRechazada() {
        prepararMesa();
        this.jeff.agregarCarta(carta(502, ColorCarta.ROJO, TipoCarta.NUMERO, 9));
        assertFalse(this.gestor.jugarTurno(this.jeff, carta(999, ColorCarta.AZUL, TipoCarta.NUMERO, 2), null));
        assertEquals(500, this.gestor.getCartaDescarte().getId());
    }

    // ----------------------------------------------------------------- RQ14 / RQ06 acción

    @Test
    @DisplayName("RQ14: Salto hace perder el turno al siguiente jugador")
    public void testSalto() {
        prepararMesa();
        Carta salto = carta(501, ColorCarta.AZUL, TipoCarta.SALTO, -1);
        this.jeff.agregarCarta(salto);
        this.jeff.agregarCarta(carta(502, ColorCarta.ROJO, TipoCarta.NUMERO, 9));

        assertTrue(this.gestor.jugarTurno(this.jeff, salto, null));
        assertSame(this.camila, this.gestor.getJugadorActivo());
    }

    @Test
    @DisplayName("RQ06: Reversa cambia el sentido a antihorario")
    public void testReversa() {
        prepararMesa();
        Carta reversa = carta(501, ColorCarta.AZUL, TipoCarta.REVERSA, -1);
        this.jeff.agregarCarta(reversa);
        this.jeff.agregarCarta(carta(502, ColorCarta.ROJO, TipoCarta.NUMERO, 9));

        assertTrue(this.gestor.jugarTurno(this.jeff, reversa, null));
        assertFalse(this.gestor.isSentidoHorario());
        assertSame(this.camila, this.gestor.getJugadorActivo());
    }

    @Test
    @DisplayName("RQ06: Con 2 jugadores la Reversa actúa como Salto")
    public void testReversaConDosJugadores() {
        List<Jugador> dos = new ArrayList<>(List.of(this.jeff, this.brenda));
        this.gestor.configurarPartida(dos, SEMILLA_PRUEBA);
        this.gestor.setCartaDescarte(carta(500, ColorCarta.AZUL, TipoCarta.NUMERO, 7));
        this.gestor.setColorActivo(ColorCarta.AZUL);
        Carta reversa = carta(501, ColorCarta.AZUL, TipoCarta.REVERSA, -1);
        this.jeff.agregarCarta(reversa);
        this.jeff.agregarCarta(carta(502, ColorCarta.ROJO, TipoCarta.NUMERO, 9));

        assertTrue(this.gestor.jugarTurno(this.jeff, reversa, null));
        assertSame(this.jeff, this.gestor.getJugadorActivo());
    }

    @Test
    @DisplayName("RQ14: Toma Dos hace robar 2 cartas al siguiente y le quita el turno")
    public void testTomaDos() {
        prepararMesa();
        Carta tomaDos = carta(501, ColorCarta.AZUL, TipoCarta.TOMA_DOS, -1);
        this.jeff.agregarCarta(tomaDos);
        this.jeff.agregarCarta(carta(502, ColorCarta.ROJO, TipoCarta.NUMERO, 9));

        assertTrue(this.gestor.jugarTurno(this.jeff, tomaDos, null));
        assertEquals(4, this.brenda.getMano().size());
        assertSame(this.camila, this.gestor.getJugadorActivo());
        assertEquals(108 - 2, this.gestor.cartasEnMazo());
    }

    @Test
    @DisplayName("RQ08: El comodín de color cambia el color activo; sin color elegido es rechazado")
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

    // ----------------------------------------------------------------- RQ09 +4

    @Test
    @DisplayName("RQ09: No permitir jugar Comodín +4 si el jugador tiene el color activo")
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
    }

    @Test
    @DisplayName("RQ09: +4 legal: el siguiente roba 4, pierde el turno y cambia el color")
    public void testComodinMasCuatroLegal() {
        prepararMesa();
        Carta masCuatro = carta(501, ColorCarta.NEGRO, TipoCarta.COMODIN_TOMA_CUATRO, -1);
        this.jeff.agregarCarta(carta(502, ColorCarta.ROJO, TipoCarta.NUMERO, 5));
        this.jeff.agregarCarta(masCuatro);

        assertTrue(this.gestor.jugarTurno(this.jeff, masCuatro, ColorCarta.VERDE));
        assertEquals(6, this.brenda.getMano().size());
        assertSame(this.camila, this.gestor.getJugadorActivo());
        assertEquals(ColorCarta.VERDE, this.gestor.getColorActivo());
    }

    // ----------------------------------------------------------------- robar y pasar

    @Test
    @DisplayName("Robar en turno agrega una carta, quita una del mazo y solo se permite una vez")
    public void testRobarCartaTurno() {
        prepararMesa();
        this.jeff.agregarCarta(carta(502, ColorCarta.ROJO, TipoCarta.NUMERO, 9));

        assertNotNull(this.gestor.robarCartaTurno(this.jeff));
        assertEquals(2, this.jeff.getMano().size());
        assertEquals(107, this.gestor.cartasEnMazo());
        assertNull(this.gestor.robarCartaTurno(this.jeff), "Solo se roba una vez por turno.");
        assertNull(this.gestor.robarCartaTurno(this.brenda), "No es el turno de Brenda.");
    }

    @Test
    @DisplayName("Solo se puede pasar el turno después de robar")
    public void testPasarTurno() {
        prepararMesa();
        this.jeff.agregarCarta(carta(502, ColorCarta.ROJO, TipoCarta.NUMERO, 9));

        assertThrows(IllegalStateException.class, () -> this.gestor.pasarTurno());
        this.gestor.robarCartaTurno(this.jeff);
        this.gestor.pasarTurno();
        assertSame(this.brenda, this.gestor.getJugadorActivo());
    }

    // ----------------------------------------------------------------- RQ16 / RQ17 ¡UNO!

    @Test
    @DisplayName("RQ16: Se puede cantar ¡UNO! con 2 cartas, no con más; robar lo anula")
    public void testCantarUno() {
        prepararMesa();
        this.jeff.agregarCarta(carta(501, ColorCarta.AZUL, TipoCarta.NUMERO, 3));
        this.jeff.agregarCarta(carta(502, ColorCarta.ROJO, TipoCarta.NUMERO, 9));
        this.gestor.cantarUno(this.jeff);
        assertTrue(this.jeff.isDijoUno());

        this.camila.agregarCarta(carta(522, ColorCarta.AMARILLO, TipoCarta.NUMERO, 3));
        this.gestor.cantarUno(this.camila);
        assertFalse(this.camila.isDijoUno(), "Con 3 cartas no se puede cantar ¡UNO!");

        this.gestor.robarCartaTurno(this.jeff);
        assertFalse(this.jeff.isDijoUno(), "Al recibir cartas se pierde el ¡UNO!");
    }

    @Test
    @DisplayName("RQ16 & RQ17: Denunciar a jugador que no cantó UNO al quedar con 1 carta")
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
    @DisplayName("RQ17: Denunciar a quien cantó ¡UNO! o tiene varias cartas no penaliza")
    public void testDenunciasInjustificadas() {
        prepararMesa();
        this.gestor.denunciarNoUno(this.jeff, this.brenda); // Brenda tiene 2 cartas
        assertEquals(2, this.brenda.getMano().size());

        this.brenda.getMano().remove(1);
        this.brenda.setDijoUno(true);
        this.gestor.denunciarNoUno(this.jeff, this.brenda);
        assertEquals(1, this.brenda.getMano().size());
    }

    // ----------------------------------------------------------------- RQ18 / RQ19 fin

    @Test
    @DisplayName("RQ18: Quien juega su última carta gana y no se aceptan más jugadas")
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
    }

    @Test
    @DisplayName("RQ19: finalizarRonda suma las manos rivales y entrega la Partida cerrada")
    public void testFinalizarRonda() {
        prepararMesa();
        this.brenda.getMano().clear();
        this.brenda.agregarCarta(carta(510, ColorCarta.ROJO, TipoCarta.SALTO, -1));          // 20
        this.brenda.agregarCarta(carta(511, ColorCarta.VERDE, TipoCarta.NUMERO, 4));         // 4
        this.camila.getMano().clear();
        this.camila.agregarCarta(carta(520, ColorCarta.NEGRO, TipoCarta.COMODIN_COLOR, -1)); // 50
        this.camila.agregarCarta(carta(521, ColorCarta.AMARILLO, TipoCarta.NUMERO, 2));      // 2
        Carta ultima = carta(501, ColorCarta.AZUL, TipoCarta.NUMERO, 3);
        this.jeff.agregarCarta(ultima);
        this.gestor.jugarTurno(this.jeff, ultima, null);

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
    @DisplayName("finalizarRonda sin ganador lanza IllegalStateException")
    public void testFinalizarSinGanador() {
        prepararMesa();
        assertThrows(IllegalStateException.class, () -> this.gestor.finalizarRonda());
    }

    @Test
    @DisplayName("Log: cada jugada y cada robo (incluidas penalizaciones) queda registrado")
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
