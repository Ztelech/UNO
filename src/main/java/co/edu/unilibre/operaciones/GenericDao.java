package co.edu.unilibre.operaciones;

import java.util.List;

/** Contrato genérico de persistencia. */
public interface GenericDao<T> {
    void crear(T dato);
    List<T> obtenerTodos();
    void actualizar(T dato);
}
