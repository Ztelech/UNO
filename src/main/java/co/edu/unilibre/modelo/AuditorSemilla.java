package co.edu.unilibre.modelo;

import co.edu.unilibre.dto.Carta;
import co.edu.unilibre.dto.TipoCarta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Auditoría anti-trampas. Reconstruye en memoria la baraja con la misma semilla y repite
 * el registro de acciones de {@link GestorPartidaUno}:
 * <pre>
 * JUGADORES;0;1,2,3   orden de los jugadores (lo escribe configurarPartida)
 * REPARTO;0;7         reparto en ronda de 7 cartas
 * DESCARTE;0;idCarta  carta inicial del descarte
 * JUGAR;idJugador;idCarta
 * ROBAR;idJugador;idCarta
 * UNO / PASAR / DENUNCIA  (informativas)
 * </pre>
 */
public class AuditorSemilla {

    private List<Integer> ordenOriginal = new ArrayList<>();

    /** Guarda el orden en que la semilla deja el mazo recién barajado. */
    public void reconstruirBaraja(long semilla) {
        BarajaUno baraja = new BarajaUno(semilla);
        baraja.generar108Cartas();
        baraja.barajar();
        this.ordenOriginal = new ArrayList<>();
        for (Carta c : baraja.getCartas()) {
            this.ordenOriginal.add(c.getId());
        }
    }

    /**
     * @return true si la carta robada es la que la semilla pone en esa posición del mazo
     *         (posición 0 = primera carta robada tras barajar).
     */
    public boolean auditarCartaRobada(int idCarta, int posicionEsperadaMazo) {
        if (posicionEsperadaMazo < 0 || posicionEsperadaMazo >= this.ordenOriginal.size()) {
            return false;
        }
        return this.ordenOriginal.get(posicionEsperadaMazo) == idCarta;
    }

    /** @return true si la mano en memoria no coincide con la calculada por la semilla (alteración). */
    public boolean detectarAlteracionMemoria(List<Carta> manoJugador, List<Carta> manoCalculadaPorSemilla) {
        return !idsOrdenados(manoJugador).equals(idsOrdenados(manoCalculadaPorSemilla));
    }

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

        BarajaUno mazo = new BarajaUno(semilla);
        mazo.generar108Cartas();
        Map<Integer, Carta> cartasPorId = new HashMap<>();
        for (Carta c : mazo.getCartas()) {
            cartasPorId.put(c.getId(), c);
        }
        mazo.barajar();
        reconstruirBaraja(semilla);

        Map<Integer, List<Integer>> manos = new LinkedHashMap<>();
        for (String id : cabecera[2].split(",")) {
            manos.put(Integer.parseInt(id.trim()), new ArrayList<>());
        }
        List<Integer> ordenJugadores = new ArrayList<>(manos.keySet());
        List<Carta> pilaDescarte = new ArrayList<>();

        try {
            for (int i = 1; i < lineas.length; i++) {
                String[] partes = lineas[i].split(";");
                if (partes.length != 3) {
                    return true;
                }
                String accion = partes[0];
                int idJugador = Integer.parseInt(partes[1]);
                String dato = partes[2];

                switch (accion) {
                    case "REPARTO" -> {
                        int porJugador = Integer.parseInt(dato);
                        for (int k = 0; k < porJugador * ordenJugadores.size(); k++) {
                            manos.get(ordenJugadores.get(k % ordenJugadores.size())).add(mazo.robar().getId());
                        }
                    }
                    case "DESCARTE" -> {
                        Carta inicial = mazo.robar();
                        while (inicial.getTipo() != TipoCarta.NUMERO) {
                            mazo.colocarAlFondo(inicial);
                            inicial = mazo.robar();
                        }
                        if (inicial.getId() != Integer.parseInt(dato)) {
                            return true;
                        }
                        pilaDescarte.add(inicial);
                    }
                    case "JUGAR" -> {
                        List<Integer> mano = manos.get(idJugador);
                        Integer idCarta = Integer.parseInt(dato);
                        if (mano == null || !mano.remove(idCarta)) {
                            return true;
                        }
                        pilaDescarte.add(cartasPorId.get(idCarta));
                    }
                    case "ROBAR" -> {
                        List<Integer> mano = manos.get(idJugador);
                        if (mano == null) {
                            return true;
                        }
                        if (mazo.cartasRestantes() == 0) {
                            if (pilaDescarte.size() < 2) {
                                return true;
                            }
                            Carta superior = pilaDescarte.remove(pilaDescarte.size() - 1);
                            mazo.recargarDescarte(new ArrayList<>(pilaDescarte));
                            pilaDescarte.clear();
                            pilaDescarte.add(superior);
                        }
                        // El mazo simulado sigue exactamente los mismos pasos que el real
                        // (cartas al fondo y recargas incluidas), así que la carta debe coincidir.
                        int idCarta = Integer.parseInt(dato);
                        if (mazo.robar().getId() != idCarta) {
                            return true;
                        }
                        mano.add(idCarta);
                    }
                    case "UNO", "PASAR", "DENUNCIA" -> {
                        // Informativas: no mueven cartas.
                    }
                    default -> {
                        return true;
                    }
                }
            }
        } catch (RuntimeException e) {
            // Números corruptos o un mazo que se agota donde la semilla dice que no: registro alterado.
            return true;
        }
        return false;
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
}
