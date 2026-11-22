# CanchaYa Pasto — Backend

API REST para reservar canchas sintéticas en Pasto. Hecha en **Java 21 + Spring Boot 3**, diseñada aplicando los **9 patrones GRASP**.

> El código está en inglés; esta documentación está en español para la exposición del curso de Arquitectura de Software.

**App web:** https://canchaya-frontend.onrender.com ([FRONTED-CANCHASYA](https://github.com/walterjojoa/FRONTED-CANCHASYA))
**API en producción:** https://canchaya-backend-dn7w.onrender.com/api/health (Render, plan gratuito: la primera petición tras un rato inactiva tarda ~50 s en despertar).
Base de datos: PostgreSQL en Neon, instalada con [BASE-DE-DATOS-CANCHAS-YA](https://github.com/walterjojoa/BASE-DE-DATOS-CANCHAS-YA).

---

## Tabla de contenido

1. [Tecnologías](#tecnologías)
2. [Cómo ejecutarlo](#cómo-ejecutarlo)
3. [Endpoints](#endpoints)
4. [Flujo de una reserva](#flujo-de-una-reserva)
5. [Estructura del proyecto](#estructura-del-proyecto)
6. [Patrones GRASP aplicados](#patrones-grasp-aplicados)
7. [Despliegue (Render + Neon)](#despliegue-render--neon)

---

## Tecnologías

| Capa | Tecnología |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.3 (Web, Data JPA, Validation, Security, Mail) |
| Seguridad | JWT (jjwt) + contraseñas con BCrypt |
| Base de datos | H2 en memoria (desarrollo) · PostgreSQL / Neon (producción) |
| Pagos | Wompi, Nequi, Daviplata (simulados en esta versión) |
| Pruebas | JUnit 5, AssertJ, MockMvc |

## Cómo ejecutarlo

Solo se necesita **Java 21**; el Maven Wrapper descarga Maven solo.

```bash
# Windows
mvnw.cmd spring-boot:run

# Linux / macOS / Git Bash
./mvnw spring-boot:run
```

La API queda en `http://localhost:8080`. Al arrancar se cargan datos de prueba:

| Usuario | Contraseña | Rol |
|---|---|---|
| `owner@canchaya.co` | `canchaya123` | OWNER (dueño de canchas) |
| `player@canchaya.co`, `laura@canchaya.co`, `mateo@canchaya.co` | `canchaya123` | PLAYER (jugador) |
| `admin@canchaya.co` | `canchaya123` | ADMIN |

Y las **38 canchas sintéticas reales de Pasto**, clasificadas por zona, desde [`src/main/resources/data/pasto_fields.csv`](src/main/resources/data/pasto_fields.csv). Es el mismo archivo que usa el repositorio de la base de datos.

### Canchas reales de Pasto

| Zona | `zone` | Canchas |
|---|---|---|
| Norte | `NORTH` | 8 |
| Centro | `CENTER` | 10 |
| Occidente | `WEST` | 2 |
| Oriente | `EAST` | 3 |
| Sur | `SOUTH` | 15 |

- Vienen de las fichas públicas de Google Maps (noviembre de 2026). Son negocios reales que **no se han unido a CanchaYa**, por eso la API las devuelve con `"referenceListing": true`. Pertenecen a la cuenta técnica `referencias@canchaya.co`, en la que nadie puede iniciar sesión.
- **El precio ($80.000/h) y el horario (08:00–22:00) son estimados**, porque ninguna publica su tarifa. El frontend debería mostrarlos como "aprox.". La única excepción es La Pasión del Fútbol, que publica su horario (06:00–22:00).
- No se guardan teléfonos ni calificaciones de Google.
- Las canchas que publique un dueño registrado tienen `"referenceListing": false`.

Consola de la base de datos H2: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:canchaya`, usuario `sa`, sin contraseña).

Ejecutar las pruebas:

```bash
./mvnw test
```

## Endpoints

Los endpoints protegidos necesitan el encabezado `Authorization: Bearer <token>` (el token lo devuelven `register` y `login`).

| Método | Ruta | Quién | Descripción |
|---|---|---|---|
| POST | `/api/auth/register` | Público | Crear cuenta (`role`: `PLAYER` u `OWNER`) |
| POST | `/api/auth/login` | Público | Iniciar sesión, devuelve el JWT |
| GET | `/api/fields` | Público | Listar canchas activas |
| GET | `/api/fields?zone=NORTH` | Público | Canchas de una zona (`NORTH`, `CENTER`, `WEST`, `EAST`, `SOUTH`) |
| GET | `/api/fields/nearby?lat=1.2136&lng=-77.2811&limit=10` | Público | Canchas más cercanas a un punto, con `distanceKm` (en línea recta) |
| GET | `/api/fields/{id}` | Público | Detalle de una cancha con sus horarios |
| GET | `/api/fields/{id}/availability?date=2026-12-05` | Público | Franjas de una hora libres/ocupadas ese día |
| POST | `/api/fields` | OWNER | Publicar una cancha |
| POST | `/api/fields/{id}/schedules` | OWNER (dueño) | Definir/cambiar el horario de un día |
| GET | `/api/owner/fields` | OWNER | Mis canchas |
| GET | `/api/owner/reservations` | OWNER | Reservas de mis canchas |
| POST | `/api/reservations` | PLAYER | Reservar una franja |
| GET | `/api/reservations/me` | Autenticado | Mis reservas |
| GET | `/api/reservations/{id}` | Jugador o dueño | Detalle de una reserva |
| POST | `/api/reservations/{id}/payments` | Jugador | Pagar el anticipo (`WOMPI`, `NEQUI`, `DAVIPLATA`) |
| POST | `/api/reservations/{id}/cancel` | Jugador | Cancelar la reserva |
| GET | `/api/health` | Público | Estado del servicio |

Los errores se devuelven en formato estándar `ProblemDetail` (RFC 7807): `400` regla de negocio, `401` sin token, `403` sin permiso, `404` no existe, `409` franja ocupada o correo repetido.

## Flujo de una reserva

```bash
# 1. Iniciar sesión como jugador
curl -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" \
     -d '{"email":"player@canchaya.co","password":"canchaya123"}'

# 2. Ver disponibilidad de la cancha 1
curl "localhost:8080/api/fields/1/availability?date=2026-12-05"

# 3. Reservar de 19:00 a 21:00  -> total $160.000, anticipo (30 %) $48.000, estado PENDING_PAYMENT
curl -X POST localhost:8080/api/reservations -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"fieldId":1,"date":"2026-12-05","startHour":19,"endHour":21}'

# 4. Pagar el anticipo con Nequi -> estado CONFIRMED y se notifica al jugador y al dueño
curl -X POST localhost:8080/api/reservations/1/payments -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" -d '{"method":"NEQUI"}'
```

## Estructura del proyecto

```
src/main/java/com/canchaya
├── web/            Controladores REST + manejador global de errores   (Controller, Pure Fabrication)
├── service/        Casos de uso: Auth, Field, Reservation, Payment     (High Cohesion, Low Coupling, Indirection)
├── domain/         Entidades con su lógica: Field, Schedule,
│                   Reservation, Payment, User                         (Information Expert, Creator)
├── repository/     Acceso a datos con Spring Data JPA                 (Pure Fabrication)
├── payment/        Métodos de pago: Wompi, Nequi, Daviplata           (Polymorphism, Indirection)
├── notification/   Envío de notificaciones (log / SMTP)               (Protected Variations)
├── policy/         Regla del anticipo                                 (Protected Variations)
├── security/       JWT                                                (Pure Fabrication)
├── dto/            Objetos de entrada/salida de la API                (Low Coupling)
├── exception/      Excepciones de negocio
└── config/         Seguridad, reloj, datos de prueba
```

---

## Patrones GRASP aplicados

En el código, cada lugar donde se aplica un patrón tiene un comentario `GRASP <Patrón>:` explicándolo. Puedes encontrarlos con:

```bash
grep -rn "GRASP" src/main/java
```

### 1. Experto en información (Information Expert)

> *Asignar la responsabilidad a la clase que tiene la información necesaria para cumplirla.*

| Clase | Responsabilidad | Por qué ella |
|---|---|---|
| `domain/Field.java` | `priceFor(hours)`, `isOpen(day, start, end)`, `scheduleFor(day)` | La cancha tiene el precio por hora y los horarios |
| `domain/Schedule.java` | `covers(day, start, end)` | El horario conoce su hora de apertura y cierre |
| `domain/Reservation.java` | `durationHours()`, `overlaps(start, end)`, `occupies(hour)`, `remainingAmount()` | La reserva conoce sus horas, su estado y sus montos |

**Ejemplo:** para saber si una nueva reserva choca con otra, el servicio no compara horas a mano; le pregunta a cada reserva: `r.overlaps(19, 21)`.

### 2. Creador (Creator)

> *B crea a A si B contiene a A, lo registra, o tiene los datos para inicializarlo.*

| Creador | Crea | Criterio |
|---|---|---|
| `Field.setSchedule(...)` | `Schedule` | La cancha **contiene** sus horarios |
| `Field.book(...)` | `Reservation` | La cancha **tiene los datos** para inicializarla (precio, horarios) |
| `Reservation.registerPayment(...)` | `Payment` | La reserva **registra** sus pagos |

Los constructores de `Schedule`, `Reservation` y `Payment` son *package-private*: ninguna otra clase puede hacer `new Reservation(...)` saltándose las validaciones.

### 3. Controlador (Controller)

> *Un objeto que no es de interfaz gráfica recibe y coordina los eventos del sistema.*

`web/AuthController`, `web/FieldController` y `web/ReservationController` reciben las peticiones HTTP (los "eventos del sistema": reservar, pagar, cancelar) y **solo delegan** en los servicios. No tienen reglas de negocio, ni SQL, ni lógica de pagos.

```java
@PostMapping("/reservations/{id}/payments")
public PaymentResponse pay(...) {
    return paymentService.payDeposit(user.id(), id, request.method());   // solo delega
}
```

### 4. Bajo acoplamiento (Low Coupling)

> *Asignar responsabilidades de forma que las clases dependan de pocas otras.*

- Los controladores solo conocen **servicios** y **DTOs**; nunca entidades JPA ni repositorios.
- `ReservationService` depende de **interfaces** (`ReservationRepository`, `DepositPolicy`) y de `NotificationService`; no sabe nada de Wompi, SMTP ni SQL.
- La API devuelve DTOs (`dto/`), así que cambiar una entidad no rompe al frontend.
- Las dependencias se inyectan por constructor (Spring), no se crean con `new`.

### 5. Alta cohesión (High Cohesion)

> *Cada clase tiene responsabilidades muy relacionadas y enfocadas.*

En vez de un "`SistemaService`" que lo haga todo, hay un servicio por tema:

| Clase | Solo se encarga de |
|---|---|
| `AuthService` | Registro e inicio de sesión |
| `FieldService` | Publicar canchas, horarios y disponibilidad |
| `ReservationService` | Crear, listar y cancelar reservas |
| `PaymentService` | Cobrar el anticipo |
| `NotificationService` | Redactar los mensajes al usuario |

### 6. Polimorfismo (Polymorphism)

> *Cuando el comportamiento varía según el tipo, usar operaciones polimórficas en vez de `if/switch`.*

`payment/PaymentMethod` es una interfaz con `charge(amount, reference)`. Cada método de pago la implementa a su manera:

```
PaymentMethod
├── WompiPaymentMethod      (tarjeta, convierte a centavos)
├── NequiPaymentMethod
└── DaviplataPaymentMethod  (agregado después sin tocar ninguna otra clase)
```

`PaymentService` nunca hace `if (metodo == WOMPI) ... else if (...)`: pide el método al `PaymentMethodResolver` y le envía `charge(...)`. Para agregar PSE basta con crear `PsePaymentMethod` con `@Component`.

### 7. Fabricación pura (Pure Fabrication)

> *Inventar una clase que no existe en el negocio para lograr alta cohesión y bajo acoplamiento.*

Ninguna de estas clases es un concepto del mundo de las canchas, pero evitan ensuciar las entidades:

| Clase inventada | Para qué |
|---|---|
| `repository/*Repository` | Guardar y consultar en la base de datos (las entidades no saben SQL) |
| `security/JwtService` | Generar y validar tokens |
| `payment/PaymentMethodResolver` | Escoger el método de pago según el tipo |
| `web/GlobalExceptionHandler` | Traducir excepciones a respuestas HTTP en un solo lugar |

### 8. Indirección (Indirection)

> *Poner un intermediario entre dos componentes para que no queden acoplados directamente.*

- `PaymentService` está entre la **reserva** y los **proveedores de pago**: la reserva nunca habla con Wompi.
- `WompiPaymentMethod` está entre **nuestro negocio** (monto en pesos + referencia) y el **API de Wompi** (`WompiClient`: centavos, moneda, llave pública). Si Wompi cambia su API, solo cambia ese intermediario.
- `NotificationService` está entre los servicios y el **canal de envío** (`NotificationSender`).

### 9. Variaciones protegidas (Protected Variations)

> *Identificar los puntos que pueden cambiar y protegerlos con una interfaz estable.*

| Punto de cambio | Interfaz estable | Implementaciones |
|---|---|---|
| Cómo se notifica (log hoy, correo mañana, WhatsApp después) | `notification/NotificationSender` | `LogNotificationSender` (desarrollo), `SmtpNotificationSender` (con `MAIL_ENABLED=true`) |
| Cuánto se cobra de anticipo (todavía en discusión: ¿30 %? ¿monto fijo?) | `policy/DepositPolicy` | `PercentageDepositPolicy` (porcentaje configurable en `canchaya.deposit-percentage`) |
| Qué pasarela/billetera se usa | `payment/PaymentMethod` | Wompi, Nequi, Daviplata |

Cambiar de log a correo real es solo una variable de entorno; cambiar la regla del anticipo es crear otra clase que implemente `DepositPolicy`. `Field`, `Reservation` y los servicios no se tocan.

### Resumen: dónde ver cada patrón en una sola petición

Así participan los patrones cuando un jugador hace `POST /api/reservations` y luego paga:

```
ReservationController.create()            ← Controller
  └─ ReservationService.create()          ← High Cohesion, Low Coupling
       ├─ FieldRepository.findByIdForUpdate()     ← Pure Fabrication
       ├─ reservation.overlaps(...)               ← Information Expert
       ├─ field.book(..., depositPolicy)          ← Creator + Protected Variations
       └─ NotificationService.reservationCreated  ← Indirection → NotificationSender (Protected Variations)

ReservationController.pay()               ← Controller
  └─ PaymentService.payDeposit()          ← Indirection, High Cohesion
       ├─ PaymentMethodResolver.resolve()         ← Pure Fabrication
       ├─ method.charge(...)                      ← Polymorphism (Wompi/Nequi/Daviplata)
       └─ reservation.registerPayment(...)        ← Creator + Information Expert
```

---

## Despliegue (Render + Neon)

1. Crear la base de datos en **Neon** y copiar la cadena de conexión.
2. En **Render** → *New Web Service* → este repositorio → entorno **Docker** (usa el `Dockerfile`).
3. Variables de entorno:

| Variable | Ejemplo |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL` | `jdbc:postgresql://ep-xxxx.aws.neon.tech/canchaya?sslmode=require` |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | credenciales de Neon |
| `JWT_SECRET` | texto aleatorio de 32+ caracteres |
| `CORS_ALLOWED_ORIGINS` | URL del frontend, p. ej. `https://canchaya.onrender.com` |
| `SEED_DEMO_DATA` | `true` solo si se quieren los datos de prueba |
| `MAIL_ENABLED`, `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD` | opcional, para correos reales |

4. Health check path: `/api/health`.

## Pendiente

- Integración real con el sandbox de Wompi (hoy `WompiClient` simula la transacción).
- Webhook de confirmación de pagos.
- Reportes de ingresos para el dueño.
