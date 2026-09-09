# Kundtjänsten

Kundtjänst i pensionatets microservice-uppdelning. Äger all kunddata och är auktoritativ källa
för kunder. Bokningstjänsten äger rum och bokningar och har ingen kundtabell.

Self-contained system: egen databas, egen affärslogik, eget gränssnitt. Ingen annan tjänst
läser i databasen, all kommunikation sker via REST.

## Kom igång

Kräver Docker. Klona de tre repona bredvid varandra, compose bygger bokningstjänsten och
notifieringstjänsten från syskonklonerna.

```text
IdeaProjects/
├── backend2_customer-service/
├── Backend2_booking/
└── backend2_notification-service/
```

Kör Compose från kundtjänstens katalog. Bokningstjänsten byggs från
`../Backend2_booking` och körs på port 8081 i både Compose och Kubernetes.
`NOTIFICATION_SERVICE_URL=http://notification-service:8082` pekar på
notifieringstjänsten i containernätverket; använd inte `localhost` för detta anrop.

```bash
# hemligheter, en gång
cat > .env <<'ENV'
JWT_SECRET=<openssl rand -base64 32>
ADMIN_PASSWORD=<valfritt lösenord>
ENV

docker compose up --build
```

Applikationen på <http://localhost:8080>, Swagger UI på
<http://localhost:8080/swagger-ui/index.html>. Logga in med `admin` och lösenordet ur `.env`.
Bokningstjänsten med frontend på <http://localhost:8081>, notifieringstjänsten på
<http://localhost:8082>.

`JWT_SECRET` måste vara **identisk** i alla tre tjänsterna, annars underkänns varandras tokens.
Dela den utanför repot, `.env` är gitignorerad.

### Utveckling

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw spring-boot:run   # backend på 8080
cd frontend && npm install && npm run dev                          # frontend på 5173
```

Vite proxar `/api` till backend, så webbläsaren ser ett enda origin och ingen CORS-konfiguration
behövs. Backend ensamt serverar ingen frontend, bundlen byggs bara i Docker.

### Tester

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw test
```

36 tester. Postgres startas av Testcontainers, så Docker måste vara igång.

Bokningstjänsten stubbas med `MockRestServiceServer` i `BookingClientTest` och med
`@MockitoBean` i `CustomerDeleteIntegrationTest`.

## API

Alla endpoints utom `POST /api/auth/login` kräver `Authorization: Bearer <token>`.

| Metod | Path | Svar |
|---|---|---|
| POST | `/api/auth/login` | 200 `{token}` · 401 |
| GET | `/api/customers` | 200 |
| GET | `/api/customers?ids=1,2,3` | 200 · 400 |
| GET | `/api/customers/{id}` | 200 · 400 · 404 |
| POST | `/api/customers` | 201 + `Location` · 400 · 409 |
| PUT | `/api/customers/{id}` | 200 · 400 · 404 · 409 |
| DELETE | `/api/customers/{id}` | 204 · 400 · 404 · 409 · 503 |

`GET /actuator/health` är öppen och kräver ingen token. Grupperna `/readiness` (kollar även
databasen) och `/liveness` används av Docker-healthchecken och Kubernetes-probarna.

Fel returneras som `application/problem+json` med ett maskinläsbart `errorCode`:

```json
{
  "type": "/problems/customer-has-active-bookings",
  "title": "Customer cannot be deleted",
  "status": 409,
  "detail": "Customer has 2 active bookings",
  "instance": "/api/customers/5",
  "errorCode": "CUSTOMER_HAS_ACTIVE_BOOKINGS",
  "activeBookingCount": 2
}
```

## Beroende till bokningstjänsten

Innan en kund raderas frågar tjänsten bokningstjänsten om aktiva bokningar:

```
GET {BOOKING_SERVICE_URL}/api/bookings/count?customerId={id}&status=ACTIVE
```

Allt som inte är ett giltigt count-svar, alltså timeout, connection refused, 4xx, 5xx, obegriplig
kropp, ger 503 och kunden ligger kvar. Tjänsten misslyckas stängt: hellre nekad radering än
en kund som försvinner med bokningar kvar i en annan databas.

Timeouts 2 sekunder, inga omförsök.

## Bokningstjänsten i drift

`docker compose stop booking-service` ger 503 på radering, vilket är fallet "andra tjänsten är
nere". Bokningstjänsten frågar i sin tur `GET /api/customers/{id}` här innan en bokning skapas
och vidarebefordrar sina kundsidor till `/api/customers/**`.

## Miljövariabler

| Variabel | Standard | Beskrivning |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/customerdb` | |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` | |
| `JWT_SECRET` | — | 32 byte Base64, identisk i alla tjänster |
| `ADMIN_USERNAME` | `admin` | seedas vid uppstart om den saknas |
| `ADMIN_PASSWORD` | `admin` | byt i drift |
| `BOOKING_SERVICE_URL` | `http://localhost:8081` | |

## Kubernetes

Kör hela systemet i Docker Desktop Kubernetes med klustertypen **kind**.
Docker Desktop ska vara igång, `kubectl` ska använda context `docker-desktop`,
och `.env` ska innehålla `JWT_SECRET` och `ADMIN_PASSWORD`.
Alla tre repon ska ligga bredvid varandra enligt katalogstrukturen ovan.

```bash
./k8s/up.sh
```

Skriptet bygger de tre Docker-imagerna, ger dem nya versionstaggar, laddar in dem
på Kubernetes-noderna och skapar hemligheter, tre databaser och tre tjänster.
Databaserna blir redo innan applikationerna startas. Därefter öppnas:

| Tjänst | Adress |
|---|---|
| Kundtjänsten | http://localhost:18080 |
| Bokningstjänsten | http://localhost:18081 |
| Notifieringstjänsten | http://localhost:18082 |

Portarna skiljer sig från Compose så att båda miljöerna kan köras samtidigt.
Logga in med `admin` och `ADMIN_PASSWORD` från `.env`. Alla tre tjänster använder
samma JWT-nyckel. Notifieringar sparas i notifieringstjänstens databas; ingen
SMTP-leverans sker.

Varje databas har en egen PVC på 1 GiB via klustrets standard-StorageClass.
PostgreSQL-data monteras från volymen och finns kvar när en pod ersätts.
Databasernas Deployment använder `Recreate` så att två PostgreSQL-processer inte
startas samtidigt mot samma volym. Kubernetes-data är separat från Compose-data.

```bash
kubectl get pods,pvc
./k8s/port-forward.sh stop   # stänger bara lokala portar
./k8s/port-forward.sh        # öppnar portarna igen
```

Vid körning från en IDE eller automation kan `./k8s/port-forward.sh --foreground`
hålla port-forwarding i förgrunden. `./k8s/up.sh --skip-build` använder de redan
byggda Docker-imagerna men importerar dem med nya taggar, vilket undviker gamla
cachade images på noden. Skripten använder aktuellt namespace.

För att stoppa tjänsterna och behålla data:

```bash
./k8s/port-forward.sh stop
kubectl scale deployment customer-service booking-service notification-service customer-db booking-db notification-db --replicas=0
```

Starta igen med `./k8s/up.sh --skip-build`. Radering av PVC:erna tar bort datalagringen;
`kubectl delete -f k8s/` ska därför inte användas som vanligt stoppkommando.

## Deployment

Deploya **sist**, när allt fungerar lokalt. Render och Railway ger begränsade gratiskrediter,
och tar de slut går tjänsten inte att nå vid redovisningen.

Checklista: managed Postgres, `SPRING_DATASOURCE_*` mot den, `JWT_SECRET` och `ADMIN_PASSWORD`
som secrets, `BOOKING_SERVICE_URL` mot bokningstjänstens publika adress. Deploya från
`Dockerfile`, inte från en buildpack, frontenden byggs i node-steget.

## Radering och en känd begränsning

Radering är mjuk: `deletedAt` sätts, raden ligger kvar. Kunden försvinner ur `GET
/api/customers` och `GET /api/customers/{id}` svarar 404, men batch-uppslagningen
`?ids=` returnerar den fortfarande med `"deleted": true`.

Skälet är en race condition som inte går att stänga: en bokning kan skapas efter att antalet
hämtats men innan kunden raderas, och en kund kan raderas efter bokningstjänstens kundkontroll
men innan bokningen sparas. Invarianten spänner över två databaser, så inga atomära operationer
eller lås i en enskild tjänst räcker. Det skulle kräva reservation eller en samordnande tjänst.

Mjuk radering stänger inte racet, den gör följden ofarlig: en bokning som skapas i tidsfönstret
blir aldrig föräldralös, eftersom namnet fortfarande går att slå upp.

Priset är att "radera" inte betyder att uppgifterna är borta ur databasen. Ska de bort på
riktigt måste raden tömmas eller anonymiseras.
