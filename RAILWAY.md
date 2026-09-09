# Separata Docker-tjänster på Railway

## Deployment 2026-09-08

Projekt: `thriving-empathy`, environment: `production` på Joakims konto.

- Kundtjänst: https://customer-service-production-bbb4.up.railway.app
- Notifieringssida: https://notification-service-production-1290.up.railway.app
- Båda har en egen PostgreSQL-tjänst med beständig volym.

Deploymenten laddades upp från de lokala reporna med Railway CLI. GitHub-autodeploy är
inte kopplat. Nästa deployment kan göras från respektive repo med:

```sh
# Från backend2_customer-service
railway up -p 7ef6a6ad-fb35-4021-83b3-5cfdbee7b1fb -e production -s customer-service --detach
# Från backend2_notification-service
railway up -p 7ef6a6ad-fb35-4021-83b3-5cfdbee7b1fb -e production -s notification-service --detach
```

Inloggningen använder `admin` och lösenordet från kundrepots lokala `.env`.
Samma fil innehåller den gemensamma JWT-nyckeln; dela den separat med gruppmedlemmen.
`BOOKING_SERVICE_URL` var vid denna deployment en platshållare i väntan på bokningstjänstens adress.

Kundtjänsten och notifieringstjänsten körs på Joakims Railway-konto, i samma projekt och
environment. Bokningstjänsten körs på en annan gruppmedlems Railway-konto, i ett separat
projekt. Varje applikation bygger sin egen Docker-image från sitt repo. `railway.json`
väljer Dockerfile-byggaren och väntar på `/actuator/health/readiness` vid deployment.

| Konto | Railway-tjänst | Källa | PORT | Publik adress |
|---|---|---|---|---|
| Joakim | `customer-service` | `joakim-epp/backend_2-customer_service` | `8080` | Kundwebb, login och anrop från booking |
| Joakim | `notification-service` | `joakim-epp/backend2_notification-service` | `8082` | Notifieringslogg och bokningsbekräftelser |
| Joakim | `customer-db` | Railway PostgreSQL | `5432` | Nej |
| Joakim | `notification-db` | Railway PostgreSQL | `5432` | Nej |
| Gruppmedlemmen | `booking-service` | `MDAX1/Backend2_booking` | `8081` | Bokningswebb och anrop från kundtjänsten |
| Gruppmedlemmen | `booking-db` | Railway PostgreSQL | `5432` | Nej |

Lägg till två PostgreSQL-resurser på Joakims konto och en på gruppmedlemmens konto, med namn
enligt tabellen. Behåll deras beständiga volymer. Koppla varje applikation till sin egen databas.
Ingen applikation ska få anslutningsuppgifter till de andra applikationernas databaser.

Anrop mellan projekten använder publika HTTPS-adresser. Adresser med `.railway.internal`
och variabelreferenser mellan tjänster fungerar endast inom respektive projekt/environment.
Notifieringstjänsten kan därför använda kundtjänstens privata adress; booking använder dess
publika adress. HTTPS-adresserna använder ingen `:8080`/`:8081` efter domänen.

## Variabler

Skapa en gemensam `JWT_SECRET`, genererad med `openssl rand -base64 32`, som sätts till
exakt samma värde på alla tre applikationerna, även över kontogränsen. Railway delar inte
variabler automatiskt mellan kontona. Sätt ett eget `ADMIN_PASSWORD` endast på kundtjänsten.
Lagra värdena i Railway, inte i Git. Databaslösenorden genereras av Railway.

### customer-service

```dotenv
PORT=8080
SPRING_DATASOURCE_URL=jdbc:postgresql://${{customer-db.PGHOST}}:${{customer-db.PGPORT}}/${{customer-db.PGDATABASE}}
SPRING_DATASOURCE_USERNAME=${{customer-db.PGUSER}}
SPRING_DATASOURCE_PASSWORD=${{customer-db.PGPASSWORD}}
BOOKING_SERVICE_URL=https://booking-adress-kommer-senare.invalid
ADMIN_USERNAME=admin
```

Lägg också till den gemensamma `JWT_SECRET` och ditt `ADMIN_PASSWORD`.

`.invalid`-adressen är en avsiktlig platshållare. Byt den till bokningstjänstens riktiga
HTTPS-basadress när den finns, utan `/api` och utan avslutande snedstreck. Kundtjänsten
kan starta och hantera kunder innan booking finns, men kundradering svarar 503 tills
anslutningen fungerar. Kunden ligger kvar när bokningarna inte kan kontrolleras.

### booking-service

```dotenv
PORT=8081
SPRING_DATASOURCE_URL=jdbc:postgresql://${{booking-db.PGHOST}}:${{booking-db.PGPORT}}/${{booking-db.PGDATABASE}}
SPRING_DATASOURCE_USERNAME=${{booking-db.PGUSER}}
SPRING_DATASOURCE_PASSWORD=${{booking-db.PGPASSWORD}}
CUSTOMER_SERVICE_URL=https://customer-service-production-bbb4.up.railway.app
NOTIFICATION_SERVICE_URL=https://notification-service-production-1290.up.railway.app
```

Lägg också till den gemensamma `JWT_SECRET`. Använd bokningsversionen med webbinloggning,
JWT- och CSRF-skydd samt `NotificationClient`. Bokningstjänsten anropar notifieringstjänsten
automatiskt när en bokning sparas. Kontrollera att de två publika basadresserna fortfarande
är de som används för deploymenten. Ett fel från notifieringstjänsten loggas men hindrar
inte att bokningen sparas.

### notification-service

```dotenv
PORT=8082
SPRING_DATASOURCE_URL=jdbc:postgresql://${{notification-db.PGHOST}}:${{notification-db.PGPORT}}/${{notification-db.PGDATABASE}}
SPRING_DATASOURCE_USERNAME=${{notification-db.PGUSER}}
SPRING_DATASOURCE_PASSWORD=${{notification-db.PGPASSWORD}}
CUSTOMER_SERVICE_URL=http://customer-service.railway.internal:8080
```

Lägg också till den gemensamma `JWT_SECRET`.

## Bygg och öppna sidorna

1. Koppla respektive GitHub-repo och branch till rätt applikationstjänst. Byggkontexten är
   repots rot. Sätt inga egna Build/Start Commands: Dockerfile bygger och startar Java.
2. Sätt alla variabler innan applikationerna deployas. Varje tjänst byggs och startas separat.
3. Under Settings → Networking, välj Generate Domain för kundtjänsten (port 8080) och
   notifieringstjänsten (port 8082) på Joakims konto. Gruppmedlemmen gör samma sak för
   bokningstjänsten (port 8081) på sitt konto. Railway ger dem HTTPS-adresser.
4. Öppna kundtjänstens adress för inloggning och kundhantering, och bokningstjänstens adress
   för bokningswebben. Hälsokontrollen på båda adresserna ska svara `{"status":"UP"}`.

Notifieringstjänstens publika startsida visar de senaste 100 notifieringstexterna och
uppdateras var tionde sekund. Logga in med samma uppgifter som i kundtjänsten; inget kund-ID
behövs. Inloggningen förmedlas via tjänstens interna `CUSTOMER_SERVICE_URL`.

## Det ni behöver utbyta

Gruppmedlemmen skickar till Joakim:

- Bokningstjänstens publika HTTPS-basadress, exempelvis `https://booking-production-xxxx.up.railway.app`.
- Bekräftelse att JWT-ändringen är deployad och att `/actuator/health/readiness` svarar 200/UP.
- Bekräftelse att `GET /api/bookings/count?customerId=1&status=ACTIVE` finns och svarar med
  JSON `{"count":0}` (eller aktuellt antal) när en giltig token skickas med.

Joakim skickar till gruppmedlemmen:

- Kundtjänstens publika HTTPS-basadress för `CUSTOMER_SERVICE_URL`.
- Den gemensamma `JWT_SECRET`, separat från Git och PR-text.
- Notifieringstjänstens publika HTTPS-basadress för `NOTIFICATION_SERVICE_URL`.

Token utfärdas av kundtjänsten med HS256, issuer `pensionat-customer-service` och audience
`pensionat`. Dessa värden finns i bokningsprojektets JWT-konfiguration. Ni behöver inte
dela Railway-inloggningar eller databaslösenord mellan kontona.

## Kontrollera kopplingen när adresserna finns

1. Uppdatera `BOOKING_SERVICE_URL` på kundtjänsten samt `CUSTOMER_SERVICE_URL` och
   `NOTIFICATION_SERVICE_URL` på booking.
   Deploya om tjänsterna så att de nya variablerna används.
2. Logga in via `POST <kundadress>/api/auth/login` med JSON-fälten `username` och `password`.
   Kopiera fältet `token` ur svaret till Postmans Bearer Token-inställning.
3. Anropa `GET <bokningsadress>/api/bookings/count?customerId=1&status=ACTIVE`.
   Med giltig token ska svaret bli 200 med `count`; utan token ska det bli 401.
4. Kontrollera att en kund med aktiv bokning inte kan tas bort: kundtjänsten ska svara 409.
5. Skapa en bokning och kontrollera notifieringsloggen. Notifieringstjänsten ska slå upp
   kunden via sin interna anslutning till kundtjänsten och spara bekräftelsen i sin databas.

Kundtjänstens `/api/auth/login` utfärdar token. Skicka den som `Authorization: Bearer <token>`
vid API-anrop. Bokningswebben loggar in via `/login`, sparar JWT i serverns session och
skickar CSRF-token med formulären. Booking vidarebefordrar JWT vid kunduppslag och
automatiska notifieringsanrop. Notifieringarna loggas och sparas; de skickas inte via SMTP.

Lokalt startas samma Dockerfiles med `docker compose up --build`. Kubernetes-filerna i
`k8s/` används för den lokala Kubernetes-demonstrationen.

Railways dokumentation: [Dockerfiles](https://docs.railway.com/builds/dockerfiles),
[variabelreferenser](https://docs.railway.com/variables),
[publika adresser](https://docs.railway.com/networking/public-networking).
