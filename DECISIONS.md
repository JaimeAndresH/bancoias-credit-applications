# Decisiones técnicas

## Proyecto

**Nombre:** `bancoias-credit-applications`  
**Artifact ID:** `ias-credit-applications`  
**Group ID:** `com.bancoias`  
**Java:** 21  
**Spring Boot:** 4.1.1

El objetivo es construir una solución backend mínima y funcional para registrar, evaluar, persistir y consultar solicitudes de crédito, manteniendo el alcance proporcional al tiempo estimado de la prueba.

---

## 1. Arquitectura del proyecto

Se adopta una arquitectura por capas simple:

```text
controller/
service/
repository/
domain/
  model/
  enums/
dto/
  request/
  response/
mapper/
exception/
config/
```

### Decisión

No se implementa una arquitectura hexagonal completa para evitar sobrearquitectura en una prueba de alcance reducido.

### Justificación

La estructura elegida permite separar claramente:

- API y transporte HTTP.
- Lógica de negocio.
- Persistencia.
- Modelos de dominio.
- DTOs.
- Manejo de errores.

La prioridad es mantener el código legible, testeable y fácil de explicar.

---

## 2. Stack reactivo

Se utiliza:

- Spring WebFlux.
- Spring Data R2DBC.
- PostgreSQL.
- Reactor (`Mono` / `Flux`).

### Decisión

Mantener el flujo reactivo de extremo a extremo y evitar Spring MVC y Spring Data JPA.

### Justificación

Spring Boot WebFlux es un requisito obligatorio de la prueba. R2DBC permite acceder a PostgreSQL sin introducir persistencia bloqueante en el flujo principal.

---

## 3. Persistencia

Se utiliza PostgreSQL levantado localmente con Docker Compose.

La conexión se realiza mediante R2DBC.

Las tablas iniciales se crean mediante:

- `schema.sql`
- `data.sql`

### Decisión

No se incorpora inicialmente Flyway o Liquibase.

### Justificación

Para el alcance de la prueba, `schema.sql` y `data.sql` permiten mantener la solución simple y reproducible. Una herramienta formal de migraciones sería recomendable en un entorno productivo.

---

## 4. Modelo de datos

Se definen dos entidades principales.

### Customer

```text
id
customerId
status
maxApprovedAmount
currentApprovedAmount
```

`currentApprovedAmount` se mantiene persistido.

### CreditApplication

```text
id
applicationReference
customerId
amount
termMonths
status
rejectionReason
processedAt
requestHash
```

### Decisión

No se crea una tabla adicional para decisiones de aprobación o rechazo.

### Justificación

Cada solicitud tiene una única decisión final dentro del alcance de la prueba. El estado, motivo de rechazo y fecha de procesamiento pueden conservarse directamente en `credit_application`.

Una tabla de historial tendría sentido si existieran reprocesamientos, múltiples etapas de aprobación o auditoría de cambios de estado.

---

## 5. Estados y motivos de rechazo

Se utilizan enums en lugar de strings libres.

### CustomerStatus

```text
ELIGIBLE
BLOCKED
```

### CreditApplicationStatus

```text
APPROVED
REJECTED
```

### RejectionReason

```text
INVALID_AMOUNT
INVALID_TERM
CUSTOMER_NOT_FOUND
CUSTOMER_BLOCKED
INSUFFICIENT_CREDIT_LIMIT
```

### Justificación

El uso de enums evita strings mágicos, mejora la legibilidad y mantiene consistencia en los valores persistidos y expuestos por la API.

---

## 6. DTOs

Se separan los objetos de transporte de las entidades de persistencia.

### CreditApplicationRequest

Contiene:

```text
applicationReference
customerId
amount
termMonths
```

### CreditApplicationResponse

Contiene:

```text
applicationReference
customerId
amount
termMonths
status
rejectionReason
processedAt
```

### Decisión

No se exponen directamente las entidades mediante los endpoints.

### Justificación

Los DTOs permiten:

- definir un contrato explícito de API;
- aplicar validaciones;
- evitar acoplar el contrato HTTP al modelo de persistencia;
- controlar qué campos puede enviar o recibir el cliente.

---

## 7. Mapeo

Se utiliza una capa `mapper` para convertir entre DTOs y entidades.

Puede implementarse manualmente o mediante MapStruct.

### Decisión actual

Usar un mapper dedicado y mantener la lógica de mapeo fuera del controller y del dominio.

### Justificación

El service debe concentrarse en la lógica de negocio. El mapper permite evitar conversiones repetidas dispersas por la aplicación.

---

## 8. Idempotencia

`applicationReference` identifica de forma única una solicitud.

En PostgreSQL se define:

```sql
UNIQUE(application_reference)
```

### Estrategia

1. Buscar inicialmente la referencia procesada.
2. Si existe y los datos coinciden, devolver el resultado original.
3. Si existe pero el payload es diferente, devolver un conflicto controlado.
4. Mantener el constraint `UNIQUE` como última garantía frente a solicitudes concurrentes.
5. Redis puede utilizarse posteriormente como optimización para recuperar rápidamente respuestas ya procesadas.

### Decisión

PostgreSQL es la fuente de verdad de la idempotencia. Redis no reemplaza la restricción única.

### Justificación

Dos requests simultáneas podrían consultar la referencia antes de que alguna haya sido persistida. El constraint de base de datos evita que ambas generen registros independientes.

---

## 9. Referencia repetida con datos diferentes

Una referencia existente no puede reutilizarse con información diferente.

### Comportamiento

```text
Misma referencia + mismo payload
→ devolver resultado original

Misma referencia + payload diferente
→ HTTP 409 CONFLICT
→ conservar la solicitud originalmente procesada
```

### Excepción

```text
ApplicationReferenceConflictException
```

La comparación puede realizarse mediante los campos relevantes o mediante `requestHash`.

---

## 10. Manejo de concurrencia del cupo

Persistir `currentApprovedAmount` permite reservar cupo mediante una única operación atómica.

Ejemplo conceptual:

```sql
UPDATE customer
SET current_approved_amount = current_approved_amount + :amount
WHERE customer_id = :customerId
  AND status = 'ELIGIBLE'
  AND current_approved_amount + :amount <= max_approved_amount;
```

### Interpretación

```text
1 fila actualizada
→ cupo reservado

0 filas actualizadas
→ no fue posible reservar el cupo
```

### Decisión

No calcular el cupo disponible mediante un `SUM` de todas las solicitudes aprobadas en cada request.

### Justificación

La actualización atómica:

- reduce consultas;
- evita condiciones de carrera;
- preserva el cupo máximo cuando llegan solicitudes simultáneas.

---

## 11. Transaccionalidad

La reserva de cupo y la persistencia de una solicitud aprobada deben pertenecer a la misma transacción.

### Problema evitado

Sin transacción podría ocurrir:

```text
se incrementa currentApprovedAmount
↓
falla el INSERT de CreditApplication
↓
el cliente queda con cupo consumido sin solicitud asociada
```

### Decisión

La operación deberá ejecutarse mediante una transacción reactiva.

---

## 12. Rechazos de negocio vs errores HTTP

Se separan los resultados normales del dominio de los errores técnicos o conflictos de API.

### Rechazos persistidos

Ejemplos:

```text
INVALID_AMOUNT
INVALID_TERM
CUSTOMER_NOT_FOUND
CUSTOMER_BLOCKED
INSUFFICIENT_CREDIT_LIMIT
```

Estos casos producen:

```text
CreditApplication.status = REJECTED
```

y se conserva el resultado procesado.

### Excepciones HTTP

Ejemplos:

```text
ApplicationReferenceConflictException → 409
CreditApplicationNotFoundException    → 404
```

### Justificación

Un cliente bloqueado o un cupo insuficiente no representan una falla técnica del sistema, sino una decisión de negocio que debe quedar persistida.

---

## 13. Manejo global de errores

Se utiliza:

```java
@RestControllerAdvice
```

junto con un modelo de error común:

```text
code
message
timestamp
```

### Justificación

Esto permite respuestas consistentes y evita duplicar lógica de manejo de errores en los controllers.

---

## 15. RabbitMQ

Se incorpora RabbitMQ para publicar un evento cuando una solicitud de crédito es aprobada, permitiendo que otros sistemas reaccionen de forma asíncrona al hecho de negocio sin acoplarse directamente al procesamiento principal.

Flujo:

```text
Solicitud APPROVED
        ↓
CreditApplicationApprovedEvent
        ↓
Exchange: bancoias.credit.events
        ↓
Routing key: credit.application.approved
        ↓
Queue: bancoias.credit.approved
```

### Topología definida

- Exchange durable de tipo `topic`: `bancoias.credit.events`.
- Routing key: `credit.application.approved`.
- Cola durable: `bancoias.credit.approved`.
- Mensajes en formato JSON.
- Mensajes publicados como persistentes.
- El atributo `messageId` del mensaje coincide con el `eventId` del evento.

### Decisión

El evento se publica únicamente cuando la solicitud queda en estado `APPROVED`.

La cola `bancoias.credit.approved` queda enlazada al exchange `bancoias.credit.events` mediante la routing key `credit.application.approved`.

### Justificación

Esta estrategia desacopla el procesamiento del crédito de posibles consumidores posteriores, como notificaciones, auditoría u otros procesos internos.

El uso de un exchange `topic` permite extender la topología en el futuro con nuevas routing keys y consumidores sin modificar el flujo principal de aprobación.

La durabilidad del exchange y de la cola, junto con mensajes persistentes, reduce el riesgo de pérdida de eventos ante reinicios del broker.

El uso de `messageId = eventId` proporciona un identificador consistente del evento y facilita estrategias posteriores de trazabilidad e idempotencia en los consumidores.

---

## 16. Docker

PostgreSQL se ejecuta mediante Docker Compose para facilitar la ejecución local y reproducible.

Los datos se almacenan en un volumen para conservarlos fuera del ciclo de vida del contenedor.

Actualmente la aplicación se ejecuta desde el host, por lo que la conexión utiliza:

```text
localhost:5432
```

Si posteriormente el backend se dockeriza dentro del mismo Compose, utilizará el nombre del servicio:

```text
postgres:5432
```

---

## 17. Orden de implementación

Se prioriza el alcance obligatorio en este orden:

```text
1. Infraestructura local y PostgreSQL
2. Entidades y enums
3. DTOs
4. Repositories
5. Service y reglas de negocio
6. Idempotencia con PostgreSQL
7. Operación atómica de cupo
8. Controller
9. Manejo de excepciones
10. Consultas
11. Pruebas automatizadas
12. Redis
13. RabbitMQ opcional
```

### Justificación

La lógica obligatoria debe quedar completamente funcional antes de añadir optimizaciones o puntos opcionales.

---

## 18. Trade-offs conocidos

### `currentApprovedAmount` persistido

Ventaja:
- facilita la validación atómica y mejora rendimiento.

Riesgo:
- es un dato derivado y debe mantenerse consistente con las solicitudes aprobadas.

Mitigación:
- actualización de cupo y persistencia de la solicitud dentro de la misma transacción.

### Redis

Ventaja:
- reduce lecturas repetidas para referencias ya procesadas.

Riesgo:
- puede no estar disponible o perder información según la configuración.

Mitigación:
- PostgreSQL mantiene la garantía definitiva de idempotencia.

### Arquitectura simple

Ventaja:
- menor complejidad y mayor velocidad de implementación.

Trade-off:
- una arquitectura hexagonal podría ofrecer mayor aislamiento en un sistema de mayor tamaño.

Decisión:
- mantener una arquitectura proporcional al alcance solicitado.

---

## 19. Validación de las decisiones

Las decisiones se validarán mediante pruebas automatizadas, principalmente sobre:

- solicitud aprobada;
- monto inválido;
- plazo inválido;
- cliente inexistente;
- cliente bloqueado;
- cupo insuficiente;
- referencia repetida con mismo payload;
- referencia repetida con payload diferente;
- solicitudes concurrentes que intenten superar el cupo máximo.

