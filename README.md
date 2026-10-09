# spring-rabbit-saga

A choreographed **saga** over RabbitMQ, built as three **Spring Boot microservices** —
two in **Java**, one in **Kotlin** — deployed to **Google Cloud Run** with a RabbitMQ +
Postgres broker pair on `e2-micro` VMs. This is the reference implementation for the
"Java/Spring Boot microservices + mensageria RabbitMQ" pattern: distributed transaction,
compensating actions, retry + dead-lettering, and observability in one runnable system.

```
                        POST /orders
                             │
                     ┌───────▼────────┐
                     │  order-service  │  (Java 21)
                     │  status=CREATED │
                     └───────┬────────┘
                             │ order.created
                    ┌────────▼─────────┐
                    │ payment-service  │  (Kotlin 2)
                    │ approve/decline  │
                    └───┬─────────┬────┘
            payment.processed │     │ payment.failed
                    ┌─────────▼──┐  │
                    │ inventory  │  │  (Java 21)
                    │  service   │  │
                    └───┬────┬───┘  │
        inventory.reserved│    │inventory.failed
                         │    │    │
                 ┌───────▼────▼────▼──────┐
                 │       order-service    │
                 │ COMPLETED  |  FAILED   │
                 └────────────────────────┘
```

## Why this exists

DXC-style enterprise Java roles screen on three things this repository demonstrates
together, and which no other project here shows:

| Capability | Where it's shown |
|---|---|
| **Java + Spring Boot microservices** | `order-service`, `inventory-service` |
| **Kotlin on the JVM** | `payment-service` |
| **Async messaging (RabbitMQ)** | `saga.exchange` topic exchange + DLQs |
| **Distributed saga + compensation** | `order.failed` / `payment.failed` paths |
| **Code quality gate** | SonarCloud (CI, opt-in) + JaCoCo coverage |
| **API testing** | Postman collection + JUnit/Mockito suites |
| **Observability** | Actuator + Micrometer + Prometheus `/actuator/prometheus` |

## Services

| Service | Language | Port | Role |
|---|---|---|---|
| `order-service` | Java 21 | 8080 | REST entrypoint (`POST /orders`, `GET /orders/{id}`), saga coordinator |
| `payment-service` | Kotlin 1.9 | 8081 | consumes `order.created`, emits `payment.processed` / `payment.failed` |
| `inventory-service` | Java 21 | 8082 | consumes `payment.processed`, emits `inventory.reserved` / `inventory.failed` |

### Event flow

| Routing key | Emitted by | Consumed by |
|---|---|---|
| `order.created` | order-service | payment-service |
| `payment.processed` | payment-service | inventory-service |
| `payment.failed` | payment-service | order-service (fails the order) |
| `inventory.reserved` | inventory-service | order-service (completes the order) |
| `inventory.failed` | inventory-service | order-service (fails the order) |
| `order.completed` / `order.failed` | order-service | — (terminal, observable) |

Queues: `payment.queue`, `inventory.queue`, `order.payment-failed.queue`,
`order.inventory-reserved.queue`, `order.inventory-failed.queue`. Every queue has a
matching `*.dlq` dead-letter queue, with **3 attempts + exponential backoff** before a
message is dead-lettered (`RepublishMessageRecoverer`-style `RejectAndDontRequeueRecoverer`
+ `x-dead-letter-*` queue args).

## Running locally

```bash
docker compose up --build -d
```

Wait for the broker pair to be healthy, then:

```bash
# happy path -> order transitions CREATED -> COMPLETED
curl -X POST localhost:8080/orders -H 'Content-Type: application/json' \
  -d '{"customerId":"c1","amount":100}'

# compensation path -> amount > 10000 declines at payment, order -> FAILED
curl -X POST localhost:8080/orders -H 'Content-Type: application/json' \
  -d '{"customerId":"c2","amount":20000}'

curl localhost:8080/orders/<id>
```

Watch the flow live: RabbitMQ management UI at `http://localhost:15672` (`saga`/`saga`),
Swagger UI at `http://localhost:8080/swagger-ui.html`.

### Tests

Each service has its own Maven suite (`./mvnw test`):

- `order-service` — 9 tests: saga transitions, idempotency, REST validation (`@DataJpaTest`
  with H2 + `@WebMvcTest`).
- `payment-service` — 2 tests: approval/decline decision.
- `inventory-service` — 2 tests: reserve/out-of-stock.

## Deploying to GCP

```bash
# one-time: create the project + link a billing account
BILLING_ACCOUNT=XXXXXX-XXXXXX-XXXXXX ./scripts/deploy.sh spring-rabbit-saga-XXXX --create

# every time after:
./scripts/deploy.sh spring-rabbit-saga-XXXX
```

`deploy.sh` uses only the **gcloud CLI**: it enables APIs, provisions RabbitMQ + Postgres
as containers on a single `e2-micro` VM (Debian + a startup script), builds each image
with Cloud Build, and deploys to Cloud Run in `southamerica-east1`. Tear down with
`./scripts/teardown.sh <PROJECT_ID>`.

> **Why `--no-cpu-throttling --min-instances 1`?** Cloud Run allocates CPU only while a
> request is in flight and scales idle services to zero. The saga participants are
> background RabbitMQ consumers with no steady HTTP traffic, so without those two flags
> the listeners starve and messages sit unacked forever. Each service is a stateless
> worker, so a single always-on instance is enough for the demo.

### Security posture (demo only)

The broker VMs use default credentials (`saga`/`saga`) and a world-readable firewall rule
(`0.0.0.0/0` on 5432/5672/15672) so the demo works end to end. **Do not reuse these for
anything real.** For a real deployment: strong random passwords via Secret Manager, a
Serverless VPC Connector so Cloud Run reaches the VMs over private IP, and firewall rules
scoped to the connector's egress range.

## CI/CD

- **`.github/workflows/ci.yml`** — builds + tests all three services on every push; runs a
  SonarCloud scan once the `SONAR_TOKEN` repository secret is set.
- **`.github/workflows/deploy.yml`** — manual deploy via Workload Identity Federation
  (set `WIF_PROVIDER`, `WIF_SERVICE_ACCOUNT` secrets and `GCP_PROJECT_ID` variable).

## Design notes

- **Choreography, not orchestration.** Cloud Run is stateless, so there is no orchestrator
  holding saga state in memory. Each service reacts to events; the order of reactions *is*
  the saga. Compensation is the `order.failed` event on any participant failure.
- **Event contracts are duplicated on purpose.** Each service owns its DTOs and shares
  only the fully-qualified package name (`dev.gazerah.saga.events`), so Jackson's
  `__TypeId__` header resolves across services without a shared jar — and no service can be
  broken by another's change.
- **Retry + DLQ, not requeue-forever.** `defaultRequeueRejected=false` + a retry interceptor
  means a poison message is retried 3 times then dead-lettered, never spun forever.
- **Not built here (on purpose):** Swift (no Apple toolchain on this machine, not
  gcloud-deployable, low screening weight for a Java backend role), SoapUI (legacy —
  Postman + RestAssured/JUnit cover API testing), and a Vue frontend (already demonstrated
  in `booking-saas` and `inquilino-ia`). NestJS + Strapi are already covered by `omnistore`.
