# Uso de inteligencia artificial

Durante el desarrollo de `bancoias-credit-applications` se utilizó Codex como
herramienta de apoyo para generar código, realizar pruebas y elaborar
documentación. Las propuestas se contrastaron con los requisitos de la prueba y
con la estructura del proyecto antes de incorporarlas.

## Actividades y resultados aprovechados

| Actividad | Apoyo de IA | Resultado aprovechado |
| --- | --- | --- |
| DTOs | Apoyo en la creación de objetos de entrada y salida y sus validaciones. | `CreditApplicationRequest`, `CreditApplicationResponse` y `ApiErrorResponse`. |
| Entidades | Apoyo en la definición de entidades, campos y enums del dominio. | Modelos de clientes y solicitudes, estados y motivos de rechazo. |
| Pruebas automatizadas | Creación de casos de prueba para reglas de negocio, API, errores y mensajería. | Pruebas del servicio y la API, del publicador y del outbox, y pruebas de integración con PostgreSQL y RabbitMQ. |
| Manejo de errores | Creación de `GlobalExceptionHandler` y del contrato común de error. | Respuestas con `code`, `message` y `timestamp`, incluyendo HTTP 400, 404, 409 y 500. |
| RabbitMQ | Apoyo en la integración del evento de aprobación y el análisis de fallos de publicación. | Outbox transaccional, confirmaciones del broker, reintentos y un identificador de evento para detectar duplicados. |
| Documentación | Redacción y organización del registro del uso de IA. | Este archivo. |

## Validación de los resultados

- Se revisó la correspondencia entre las propuestas y los requisitos RF01–RF06.
- Se verificó la compilación y se ejecutaron las pruebas mediante Maven.
- Las pruebas de servicio comprobaron aprobaciones, rechazos, referencias repetidas,
  conflictos y la solicitud de rollback ante fallos. Estas pruebas usan mocks y
  no sustituyen una verificación de concurrencia contra una base de datos real.
- Las pruebas HTTP comprobaron los endpoints, la validación de entradas y las
  respuestas de error mediante `WebTestClient`.
- La integración de mensajería se verificó con PostgreSQL y RabbitMQ reales en
  Docker, utilizando una base separada y colas propias de prueba. Se comprobó
  la creación del evento, su publicación, el reintento tras un fallo y la ausencia
  de eventos para solicitudes rechazadas.
- La última ejecución verificada durante esta asistencia obtuvo 50 pruebas
  exitosas, más 2 pruebas de integración de RabbitMQ ejecutadas por separado.

Las pruebas reales de mensajería no constituyen una prueba exhaustiva de solicitudes
concurrentes para un mismo cliente ni garantizan entrega exactamente una vez.
Los consumidores deben detectar duplicados mediante `eventId`.

## Resultados corregidos y alternativas descartadas

- Se corrigieron repositorios que tenían interfaces anidadas y entidades cruzadas.
- Se completó la configuración del procesador de MapStruct para generar el mapper.
- La primera ejecución de las pruebas reales detectó que RabbitMQ 4 rechazaba
  el tipo de cola temporal utilizado por el test. Se cambió por una cola durable
  propia que se elimina al finalizar la prueba.
- Se descartó la publicación directa del evento después de guardar la aprobación,
  debido al riesgo de perderlo si ocurre un fallo entre el commit y el envío.
  Se adoptó un outbox transaccional.
- La revisión identificó la necesidad de alinear la precisión del monto y las
  longitudes permitidas por el DTO con los límites de PostgreSQL. Esta observación
  no se considera resuelta únicamente por disponer de pruebas con mocks.

## Información sensible

El contexto utilizado corresponde al código del ejercicio, sus requisitos y
datos de ejemplo. Los identificadores de clientes usados para desarrollar y probar
la solución son los proporcionados por la prueba o los generados para los tests.

Las credenciales presentes en la configuración local son de desarrollo; no deben
reutilizarse en producción. Para otros entornos, la conexión a RabbitMQ admite
variables de entorno. El uso de IA debe limitarse al contexto necesario y evitar
incluir datos personales reales, credenciales de producción o secretos de terceros.

## Responsabilidad sobre la entrega

La IA se utilizó como apoyo. La aceptación de sus propuestas debe sustentarse en
la revisión del código, las pruebas ejecutadas y la capacidad de explicar las
decisiones adoptadas. Este registro describe el uso realizado y las verificaciones
observadas; no reemplaza la revisión final del candidato.
