package co.edu.unilibre.dto;

import java.io.Serializable;

/** Carta del UNO. Objeto de transferencia sin lógica de negocio pesada. */
public class Carta implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private ColorCarta color;
    private TipoCarta tipo;
    private int numero;      // 0 al 9 para TipoCarta.NUMERO; -1 para especiales
    private int valorPuntos; // Números: cara; Salto/+2/Reversa: 20 pts; Comodines: 50 pts

    public Carta() {
    }

    public Carta(int id, ColorCarta color, TipoCarta tipo, int numero, int valorPuntos) {
        this.id = id;
        this.color = color;
        this.tipo = tipo;
        this.numero = numero;
        this.valorPuntos = valorPuntos;
    }

    /** RQ08: la carta se puede jugar si coincide en color activo, número o tipo, o si es comodín. */
    public boolean esJugableSobre(Carta descarte, ColorCarta colorActivo) {
        if (this.color == ColorCarta.NEGRO) return true;
        if (this.color == colorActivo) return true;
        if (descarte == null) return true;
        if (this.tipo == TipoCarta.NUMERO && descarte.getTipo() == TipoCarta.NUMERO) {
            return this.numero == descarte.getNumero();
        }
        return this.tipo == descarte.getTipo();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public ColorCarta getColor() {
        return color;
    }

    public void setColor(ColorCarta color) {
        this.color = color;
    }

    public TipoCarta getTipo() {
        return tipo;
    }

    public void setTipo(TipoCarta tipo) {
        this.tipo = tipo;
    }

    public int getNumero() {
        return numero;
    }

    public void setNumero(int numero) {
        this.numero = numero;
    }

    public int getValorPuntos() {
        return valorPuntos;
    }

    public void setValorPuntos(int valorPuntos) {
        this.valorPuntos = valorPuntos;
    }

    @Override
    public String toString() {
        return "Carta{id=" + id + ", color=" + color + ", tipo=" + tipo
                + ", numero=" + numero + ", valorPuntos=" + valorPuntos + "}";
    }
}
