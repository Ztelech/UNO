package co.edu.unilibre.impOperaciones;

/** Dónde se escriben los datos. */
public enum ModoPersistencia {
    /** Solo H2 embebido en disco (desarrollo sin internet). */
    H2_LOCAL,
    /** Solo Neon PostgreSQL (lectura y escritura directa en la nube). */
    NEON_NUBE,
    /** Escritura síncrona en H2 y réplica en paralelo hacia Neon, sin bloquear la partida. */
    HIBRIDO_ASINCRONO
}
