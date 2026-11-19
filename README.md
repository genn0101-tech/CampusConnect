# Campus Connect

Campus Connect is a web app that tracks attendance for student organizations and campus events.
Participants scan a QR code with their own phone to check in and check out, with no app to install.
Organizers get stay duration, attendance rates, and a CSV report they can use for funding applications.

## The problem

At the GRIT Basketball Tournament, about 60 people showed up: 30+ registered players and around 30 spectators.
I had to stand at the entrance and confirm every player by name, while also running the event.
Spectators were not recorded at all, so we had no idea how many came.

This data matters. When a club applies for funding, the school asks how many students it reaches.
Paper sign-in sheets are slow and hard to reuse, and Microsoft Forms only records who came in.
Campus Connect also records when people leave, how long they stayed, and how often they come back.

## Features

- **Groups**: each student organization has its own page and its own passphrase.
  New groups can only be created by people who know the group creation code.
- **Event creation**: an unlocked group can create events (name, date, location).
- **QR check-in and check-out**: each event has two QR codes, one for the entrance and one for the exit.
- **Guest registration**: a first-time Student ID is asked for a name and email once, then checked in.
- **Stay duration**: minutes between check-in and check-out, plus the average for each event.
- **Attendance rate**: for each member, events attended out of all events held by the group.
- **Funding report**: download an event's attendance as a CSV file (opens correctly in Excel).
- **Developer page**: see every group and reset a group passphrase that was forgotten.
- **Privacy note**: the check-in page states which group the data is collected for.

## Tech stack

| Technology | Why I chose it |
|------------|----------------|
| Java 17 | The language I learned in CS121, and a long-term support (LTS) version. |
| Spring Boot | Handles web pages, forms, and database access, so I can focus on attendance logic. |
| Thymeleaf | Builds HTML pages on the server, so the app works on any phone browser without JavaScript frameworks. |
| H2 (development) | A database that runs inside the app, so no installation is needed on my PC. |
| PostgreSQL (production) | Render deletes local files when the app restarts, so production data must live in a real database. |
| BCrypt | Group passphrases are stored only as slow, salted hashes, never as plain text. |
| ZXing | Generates the QR code images. |
| Docker | Render cannot run Java directly, so the app is packaged as a Docker image. |
| Render | Hosts the app at a public HTTPS URL, which phones need to open the QR code links. |

## Running on your computer

Requirements: Java 17. Maven is not needed because the project includes the Maven wrapper (`mvnw`).

```bash
# Windows
.\mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw spring-boot:run
```

Open http://localhost:8080.

The development profile loads fake sample data from `src/main/resources/data/`.
The development-only values are in `src/main/resources/application-dev.properties`
(for example, the sample group GRIT Club uses `dev-grit`, and the group creation code is `dev-create`).
Production never loads this file.

Run the tests:

```bash
./mvnw test
```

### Production settings

Production uses the `prod` profile (set in the `Dockerfile`).
It starts with an empty database and refuses to start unless these environment variables are set:

| Variable | Purpose |
|----------|---------|
| `JDBC_DATABASE_URL` | e.g. `jdbc:postgresql://<host>:5432/<database>` |
| `DB_USERNAME`, `DB_PASSWORD` | Database login |
| `GROUP_CREATION_CODE` | Needed to create a new group |
| `ADMIN_PASSPHRASE` | Needed to open the developer page (`/admin`) |
| `APP_BASE_URL` | Optional. Public URL used inside QR codes |

## Known limitations

- **No login system.** Access is "this browser knows the group passphrase", not user accounts.
- **No limit on passphrase attempts.** BCrypt makes each attempt slow, but repeated guessing is not blocked.
- **No CSRF protection.** Forms do not include CSRF tokens yet.
- **Free hosting sleeps.** On Render's free plan the app stops after 15 minutes without visitors,
  and the first visit afterwards takes about a minute. Open the site a few minutes before an event.
- **Free database expires.** Render's free PostgreSQL stops working 30 days after it is created,
  and its data is deleted 14 days after that (according to Render's official 2026 article; the terms may change).
  Download the CSV report after every event.

## Next steps

- Add Spring Security for real logins, CSRF protection, and limits on passphrase attempts
- Invitation lists for existing members
- Reminder emails before events
- Later (Phase 4): Bluetooth proximity check-in, a small event chat, and automatic promotion from Guest to Member
