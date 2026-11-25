# UNO — Edición Playa

Videojuego de escritorio del clásico juego de cartas **UNO** desarrollado en **Java 17**, diseñado para partidas multijugador locales (*hot-seat*) de **2 a 10 personas reales** siguiendo fielmente las reglas oficiales. Incorpora un motor determinista por semilla, auditoría anti-trampas en tiempo real y sincronización híbrida en la nube para ranking global multi-computador.

---

## Características Principales

* **Reglas Oficiales Completas:** Baraja reglamentaria de 108 cartas, cartas especiales (*Salto*, *Reversa*, *+2*, *Comodín*, *Comodín +4*), validación de uso legal del `+4` (bloqueado visualmente con candado si el jugador posee cartas del color activo), declaración de **¡UNO!** y sistema de denuncia por olvido (`+2` cartas de penalización).
* **Modo Multijugador Local con Privacidad:** Pantalla intermedia (`PanelCambioTurno`) que oculta las cartas entre turnos al pasar el computador y habilita la ventana reglamentaria para cantar **¡UNO!**.
* **Interfaz Vectorial Personalizada:** Renderizado 100 % en **Java2D / Swing** (`ComponenteCarta`, `FondoPlaya`, `PanelMesa`) sin dependencias de imágenes externas, elevación dinámica de cartas jugables e indicadores visuales de estado.
* **Auditoría Anti-Trampas por Semilla Determinista:** Toda la baraja y el reciclaje del descarte dependen de una única instancia `Random(semilla)`. `AuditorSemilla` ejecuta una simulación paralela oculta en tiempo real y permite re-auditar cualquier partida histórica desde la interfaz (`PanelAuditoria`).
* **Persistencia Híbrida Asíncrona y Ranking Global:** Estrategia `HIBRIDO_ASINCRONO` (`GestorConexion` y `SincronizadorNube`) que escribe de forma instantánea en **H2 embebida** (`./db_unogame`) y replica en segundo plano hacia **PostgreSQL (Neon)** con cola persistente de reintentos ante fallos de red y unificación automática de perfiles entre distintos equipos.

---

## Arquitectura del Proyecto

El sistema sigue una arquitectura modular por capas bajo el paquete base `co.edu.unilibre`, aplicando el patrón **DAO** (*Data Access Object*):

| Paquete | Responsabilidad | Clases Principales |
| :--- | :--- | :--- |
| **`dto`** | Objetos de transferencia de datos y reglas base de cartas | `Carta`, `Jugador`, `Partida`, `ColorCarta`, `TipoCarta` |
| **`operaciones`** | Contrato genérico de persistencia | `GenericDao<T>` |
| **`impOperaciones`** | Implementaciones JDBC, motor híbrido y sincronización | `GestorConexion`, `ImpOperacionesJugadorBD`, `ImpOperacionesPartidaBD`, `SincronizadorNube`, `ModoPersistencia` |
| **`modelo`** | Lógica de juego, determinismo, auditoría y ranking | `GestorPartidaUno`, `BarajaUno`, `AuditorSemilla`, `GestorRanking` |
| **`vista`** | Interfaz gráfica Swing, componentes Java2D y navegación | `AplicacionUno`, `VentanaPrincipal`, `PanelMenu`, `PanelMesa`, `PanelCambioTurno`, `PanelResultados`, `PanelAuditoria`, `ComponenteCarta` |

---

## Componentes Técnicos

* **Lenguaje y JDK:** Java 17 (JDK 17 / JEK26).
* **Construcción y Dependencias:** Apache Maven (`pom.xml` con `maven-assembly-plugin` para generación de JAR ejecutable con dependencias).
* **Base de Datos Local (Caliente):** H2 Database `2.3.232` (`./db_unogame` en modo compatibilidad PostgreSQL).
* **Base de Datos en la Nube (Fría):** PostgreSQL `42.7.3` alojado en Neon.
* **Pruebas Automatizadas:** JUnit Jupiter `5.10.2` (pruebas unitarias, de integración con Neon, reintentos de sincronización y vistas Swing).

---

## Compilación y Ejecución

### 1. Requisitos Previos
* **JDK 17** o superior instalado y configurado en la variable de entorno `JAVA_HOME`.
* **Apache Maven 3.8+** (incluido de forma integrada en IntelliJ IDEA).
* Verificar instalación en terminal:
  ```bash
  java -version
  mvn -version
