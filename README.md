# BancoIAS - Solicitudes de crédito

Backend para registrar, evaluar y consultar solicitudes de crédito. Utiliza Java 21,
Spring Boot WebFlux, R2DBC y PostgreSQL. Las aprobaciones nuevas generan eventos
JSON para RabbitMQ mediante un outbox transaccional.

## Requisitos

- JDK 21.
- Maven 3.9 o superior, disponible como `mvn`.
- Docker con Docker Compose v2, iniciado.
- Puertos locales libres: 5432 (PostgreSQL), 5672 (RabbitMQ),
  15672 (consola RabbitMQ) y 8080 (API).

Los ejemplos de esta guía usan PowerShell. Ejecuta los comandos desde la raíz del
repositorio. La aplicación se ejecuta en el host; Docker ejecuta PostgreSQL y RabbitMQ.

## Iniciar el proyecto

Comprueba las herramientas:

```powershell
java -version
mvn -version
docker compose version
```

Levanta los servicios y comprueba que estén listos:

```powershell
docker compose up -d postgres rabbitmq
docker compose ps
docker exec bancoias-postgres pg_isready -U bancoias -d bancoias_credit
docker exec bancoias-rabbitmq rabbitmq-diagnostics -q ping
```

El primer arranque descarga las imágenes. Si las comprobaciones fallan mientras
los servicios inician, vuelve a ejecutarlas antes de iniciar la aplicación.

```powershell
mvn spring-boot:run
```

Mantén esa terminal abierta. La API estará en `http://localhost:8080`.
El arranque inicializa las tablas y los clientes de ejemplo mediante `schema.sql`
y `data.sql`. Los siguientes arranques conservan solicitudes y cupo consumido.

## Clientes iniciales

| Cliente | Estado | Cupo máximo acumulado (COP) |
| --- | --- | ---: |
| CLI-1001 | ELIGIBLE | 10000000 |
| CLI-1002 | BLOCKED | 8000000 |
| CLI-2001 | ELIGIBLE | 15000000 |

Para aprobar una solicitud, el monto debe ser mayor que cero, el plazo debe estar
entre 6 y 60 meses, el cliente debe existir y estar habilitado, y debe tener cupo.
Los rechazos se guardan con su motivo y no consumen cupo.

## Endpoints

| Método | Ruta | Resultado |
| --- | --- | --- |
| POST | `/api/credit-applications` | Procesa una solicitud y devuelve la decisión. |
| GET | `/api/credit-applications/{applicationReference}` | Consulta una solicitud por referencia. |
| GET | `/api/credit-applications?limit=20` | Lista las solicitudes más recientes. |

El listado incluye aprobaciones y rechazos, ordenados por fecha descendente e ID
descendente en caso de empate. `limit` admite valores entre 1 y 100; por defecto, 20.

## Probar la API

Abre otra terminal PowerShell. Inicializa estas variables y la función auxiliar:

```powershell
$apiBase = 'http://localhost:8080/api/credit-applications'
$demoPrefix = 'DEMO-' + [Guid]::NewGuid().ToString('N')

function Send-CreditApplication {
    param([hashtable]$Payload)
    Invoke-RestMethod -Method Post -Uri $apiBase -ContentType 'application/json' `
        -Body ($Payload | ConvertTo-Json -Compress)
}
```

### Aprobación

```powershell
$approvedRequest = @{
    applicationReference = "$demoPrefix-APPROVED"
    customerId = 'CLI-1001'
    amount = 6000000
    termMonths = 24
}
Send-CreditApplication $approvedRequest | ConvertTo-Json
```

En una base recién inicializada devuelve HTTP 200 y `status: APPROVED`, con
`processedAt` y sin motivo de rechazo. El cupo consumido de CLI-1001 queda en
6000000. Si ya se procesaron créditos anteriormente, la decisión depende del
cupo restante.

### Referencia repetida e idempotencia

Reenvía exactamente la solicitud anterior:

```powershell
Send-CreditApplication $approvedRequest | ConvertTo-Json
```

Devuelve la decisión y fecha originales, sin crear otra solicitud, consumir cupo
otra vez ni generar un evento nuevo. Si cambias los datos con la misma referencia:

```powershell
$conflictingRequest = $approvedRequest.Clone()
$conflictingRequest.amount = 7000000
Send-CreditApplication $conflictingRequest
```

Devuelve HTTP 409, con `code: APPLICATION_REFERENCE_CONFLICT`. PowerShell muestra
los estados HTTP de error como excepciones; la solicitud original se conserva.

### Rechazos de negocio

Ejecuta después del ejemplo de aprobación para comprobar cupo insuficiente:

```powershell
Send-CreditApplication @{
    applicationReference = "$demoPrefix-LIMIT"
    customerId = 'CLI-1001'
    amount = 5000000
    termMonths = 24
} | ConvertTo-Json
```

Devuelve HTTP 200, `status: REJECTED` y `rejectionReason: INSUFFICIENT_CREDIT_LIMIT`
si el cupo restante es menor que 5000000.

Para probar los demás rechazos, usa una referencia diferente en cada envío:

| Referencia | customerId | amount | termMonths | Motivo esperado |
| --- | --- | ---: | ---: | --- |
| DEMO-BLOCKED | CLI-1002 | 1000000 | 24 | CUSTOMER_BLOCKED |
| DEMO-MISSING | CLI-NO-EXISTE | 1000000 | 24 | CUSTOMER_NOT_FOUND |
| DEMO-AMOUNT | CLI-1001 | 0 | 24 | INVALID_AMOUNT |
| DEMO-TERM | CLI-1001 | 1000000 | 5 | INVALID_TERM |

Ejemplo de cliente bloqueado:

```powershell
Send-CreditApplication @{
    applicationReference = "$demoPrefix-BLOCKED"
    customerId = 'CLI-1002'
    amount = 1000000
    termMonths = 24
} | ConvertTo-Json
```

### Consultas y errores de entrada

```powershell
Invoke-RestMethod -Uri "$apiBase/$($approvedRequest.applicationReference)" | ConvertTo-Json
Invoke-RestMethod -Uri "${apiBase}?limit=10" | ConvertTo-Json -Depth 5
Invoke-RestMethod -Uri "$apiBase/$demoPrefix-NO-EXISTE"
Invoke-RestMethod -Uri "${apiBase}?limit=0"
Send-CreditApplication @{ applicationReference = "$demoPrefix-INCOMPLETE" }
```

Los últimos tres comandos devuelven, respectivamente, HTTP 404, 400 y 400.
Los campos de entrada son `applicationReference`, `customerId`, `amount` y
`termMonths`; todos son obligatorios y los identificadores no pueden estar vacíos.
Un monto no positivo o un plazo fuera de rango son rechazos de negocio persistidos,
mientras que un campo ausente o un JSON inválido son errores HTTP 400.

Las respuestas de error tienen este formato:

```json
{
  "code": "CREDIT_APPLICATION_NOT_FOUND",
  "message": "No existe una solicitud con referencia REF-NO-EXISTE",
  "timestamp": "2026-10-01T17:00:00Z"
}
```

Los errores inesperados devuelven HTTP 500 con un mensaje genérico.

## Comprobar RabbitMQ

Abre [la consola local](http://localhost:15672) con usuario `bancoias` y contraseña
`bancoias123`. Son credenciales destinadas al entorno de desarrollo.

Después de aprobar un crédito nuevo, espera el ciclo de publicación, normalmente
unos cinco segundos. En **Queues and Streams**, abre `bancoias.credit.approved`.
En **Get messages**, selecciona una opción con requeue para inspeccionar el JSON
sin retirarlo permanentemente de la cola. Los rechazos no generan eventos.

| Elemento | Valor |
| --- | --- |
| Exchange durable, tipo topic | bancoias.credit.events |
| Routing key | credit.application.approved |
| Cola durable de ejemplo | bancoias.credit.approved |
| Tipo de evento | CreditApplicationApproved |
| Versión del contrato | schemaVersion = 1 |

El evento incluye `eventId`, `applicationReference`, `customerId`, `amount`,
`termMonths` y `processedAt`. Cada sistema que necesite todos los eventos debe
enlazar su propia cola al exchange con esa routing key. Los consumidores de una
misma cola se reparten los mensajes.

La aprobación y el evento pendiente se guardan en la misma transacción PostgreSQL.
Si RabbitMQ no está disponible, la aprobación puede continuar y el evento queda
pendiente en `credit_approval_outbox`. El publicador reintenta, comprueba la
confirmación y el enrutamiento, y después marca el evento como publicado.
La entrega es al menos una vez: los consumidores deben detectar duplicados por
`eventId` y confirmar el mensaje después de procesarlo.

Para ejecutar únicamente el backend y PostgreSQL, puedes pausar la publicación:

```powershell
$env:MESSAGING_ENABLED = 'false'
mvn spring-boot:run
```

Los eventos seguirán guardándose. Para reactivar la publicación, detén la aplicación,
ejecuta `Remove-Item Env:MESSAGING_ENABLED` y vuelve a iniciarla con RabbitMQ listo.

## Pruebas automatizadas

PostgreSQL debe estar disponible para la prueba de arranque del contexto Spring:

```powershell
docker compose up -d postgres
mvn test
```

La suite cubre servicio, API, errores, publicador y reintentos del outbox. Las dos
pruebas reales de RabbitMQ se omiten por defecto y se habilitan explícitamente.

Para ejecutarlas, levanta ambos servicios y crea una vez la base separada:

```powershell
docker compose up -d postgres rabbitmq
docker exec bancoias-postgres psql -U bancoias -d postgres -c "CREATE DATABASE bancoias_messaging_test;"
mvn test '-Drabbitmq.integration=true'
```

Si la base ya existe, omite el comando de creación. Las pruebas de integración
usan esa base y colas propias, y eliminan sus registros al terminar. Verifican
aprobación, persistencia del evento, idempotencia, reintento y publicación real,
así como ausencia de eventos para rechazos.

## Detener y reiniciar

Detén la aplicación con Ctrl+C y los servicios con:

```powershell
docker compose down
```

Esto conserva los volúmenes. Para repetir los ejemplos desde cero, el siguiente
comando **borra los datos locales de PostgreSQL y RabbitMQ de este Compose**,
incluida la base de integración. No lo ejecutes si necesitas conservarlos:

```powershell
docker compose down -v
docker compose up -d postgres rabbitmq
```

## Configuración y limitaciones

- Conexión PostgreSQL: `localhost:5432`, base `bancoias_credit`, usuario `bancoias`
  y contraseña `bancoias123`. Puede sobrescribirse con `SPRING_R2DBC_URL`,
  `SPRING_R2DBC_USERNAME` y `SPRING_R2DBC_PASSWORD`.
- RabbitMQ admite `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER` y
  `RABBITMQ_PASSWORD`. No reutilices las credenciales de ejemplo en producción.
- Redis no participa en el flujo actual y no necesita levantarse.
- Las referencias repetidas se comparan por cliente, monto y plazo. La base mantiene
  una restricción única y la actualización del cupo es condicional y transaccional.
- Sigue pendiente una prueba real de concurrencia para el cupo de un mismo cliente.
- Sigue pendiente alinear la validación del DTO con `NUMERIC(15,2)` y los tamaños
  de las columnas. Para estas pruebas, usa montos de hasta dos decimales,
  referencias de hasta 100 caracteres y clientes de hasta 50 caracteres.
- El outbox conserva los registros publicados y no genera eventos retroactivos
  para créditos aprobados antes de incorporar la integración.
- No se implementa un consumidor de negocio dentro de este backend.

Si falla la conexión, comprueba `docker compose ps` y consulta los logs con
`docker compose logs postgres rabbitmq`. Si 8080 está ocupado, establece
`$env:SERVER_PORT = '8081'` antes de iniciar la aplicación y ajusta `$apiBase`.

## Documentación de entrega

- [Decisiones técnicas](DECISIONS.md).
- [Uso de inteligencia artificial](AI_USAGE.md).
