# Workshop Booking

Java integration-testing homework. This Maven project contains two small Spring
Boot applications:

- `booking-service`: registration API, transactional service, JPA repositories and database.
- `membership-registry`: HTTP provider used to check whether a member is eligible.

Production code is supplied. Write the integration tests and the test setup.

## Getting started

1. Extract the archive and open the root `pom.xml` in IntelliJ IDEA as a Maven project.
2. Select JDK 17 or newer as the project SDK and Maven runner JDK.
3. Allow Maven to download the dependencies. The first build needs internet access.
4. Run commands from the directory containing the root `pom.xml`.

Windows PowerShell:

```powershell
.\mvnw.cmd -version
.\mvnw.cmd package
```

Linux/macOS: use `sh mvnw` instead of `.\mvnw.cmd`. The Maven wrapper downloads
Maven automatically. If set, `JAVA_HOME` must point to a JDK.

To run the applications manually, open two terminals:

```powershell
.\mvnw.cmd -pl membership-registry spring-boot:run
```

```powershell
.\mvnw.cmd -pl booking-service spring-boot:run
```

The registry listens on port 9091 and booking on port 8080. The booking application
uses a local H2 file under `data/`. These are ordinary application defaults, not
test settings. Tests must create an isolated environment rather than depend on
these running processes or their data. Automated tests must select available ports.

The supplied POM files contain application dependencies only. Add the testing
libraries, Maven integration-test execution and isolated test configuration from
the practical lecture in the modules that need them. No test profile, test datasource, test container, test
fixture or completed test is supplied. The project uses Spring Boot **4.1.0**;
use test dependencies and imports compatible with that version.

The basic tests can run with an embedded database. A selected PostgreSQL
compatibility check also requires an available test PostgreSQL instance, for
example through a disposable container and its container runtime. Never use a
production database.

## Application behavior

### Workshops and registrations

A `Workshop` has a generated ID, unique code, title and open/closed flag. A
`Registration` has a generated ID, reference, attendee, email, member ID, ticket
count and a required relationship to a workshop.

At ordinary application startup the booking application creates `JAVA-01` (open)
and `CLOSED-01` (closed) if their codes are absent. These examples are for manual
use. Tests should arrange their own data rather than depend on generated IDs,
global row counts or existing files. A persistence slice does not necessarily
execute application startup runners.

`BookingService.register(request)` follows these rules:

1. Validate the complete request before accessing repositories or the registry.
2. Find the workshop by its exact code. An absent workshop fails with
   `WORKSHOP_NOT_FOUND`. A closed workshop fails with `WORKSHOP_CLOSED`.
3. Check membership through `MembershipGateway`. An ineligible member fails with
   `MEMBER_INELIGIBLE`.
4. Strip surrounding whitespace from the attendee name. Convert the email to
   lower case using the root locale. Preserve reference, workshop code and member
   ID exactly as supplied; do not trim or change their case.
5. Save and flush the registration, then return its generated ID and stored values.

The `BookingRequest` rules apply to direct service calls as well as API requests:

| Field | Required behavior |
| --- | --- |
| `reference` | 1–32 ASCII letters, digits or hyphens |
| `workshopCode` | 1–32 ASCII letters, digits or hyphens |
| `attendee` | Nonblank, at most 100 characters before stripping |
| `email` | Nonblank valid email address, at most 100 characters; surrounding spaces are invalid |
| `memberId` | 1–32 ASCII letters, digits or hyphens |
| `tickets` | Required integer, inclusive range 1–5 |

There is no aggregate workshop-capacity rule. The ticket limit applies to each
registration. The same email may register for different workshops, but only once
for the same workshop. Email uniqueness is based on the lower-case value saved
by the service.

### Persistence

`WorkshopRepository.findByCode` and `RegistrationRepository.findByReference`
return an empty Optional when no row matches.

`RegistrationRepository.findByWorkshopCodeAndAttendeeStartingWith` filters by
workshop code and attendee prefix, with pagination supplied by the caller. Use
an explicit sort when order matters. Prefix matching is case-sensitive for the
supplied H2 configuration and PostgreSQL's ordinary case-sensitive string matching.

Database mappings enforce:

- Unique workshop code (`uk_workshop_code`).
- Unique registration reference (`uk_registration_reference`).
- Unique workshop/email pair (`uk_workshop_email`).
- Non-null required columns and the registration-to-workshop foreign key.

The entities have no Bean Validation annotations. Direct repository writes can
therefore exercise database constraints separately from request validation.
The database does not itself enforce the service's ticket-range or email-format
rules. Constraint failures are observed when SQL executes, not necessarily when
an entity is first passed to `save`.

`BookingService.find(reference)` returns the registration with its workshop code,
or fails with `REGISTRATION_NOT_FOUND`. It performs the mapping inside its read
transaction so the lazy workshop relationship can be read safely.

### Batch registration and transactions

`BookingService.registerBatch(requests)` accepts 1–10 requests and processes them
in list order in **one transaction**. The corresponding endpoint is
`POST /registrations/batch` with a JSON array.

The batch is atomic: if a later item fails validation, membership checking or a
database constraint, none of the batch's registrations remains committed. A
failure can occur after an earlier item has already been flushed. Previously
committed records outside that batch remain unchanged. Generated IDs may have
gaps after rollback; do not require consecutive IDs.

Registry HTTP calls are read-only. A database rollback does not undo calls that
have already reached the registry. The operation does not attempt distributed
rollback or retry.

### Booking HTTP API

All routes use JSON. Successful single creation returns a relative `Location`
header naming the new registration. Batch creation has no per-record Location
header. Responses may include additional fields in future; the fields below
are required.

| Request | Success |
| --- | --- |
| `POST /registrations` | 201 and one registration object |
| `POST /registrations/batch` | 201 and registration objects in input order |
| `GET /registrations/{reference}` | 200 and one registration object |

A creation request is:

```json
{
  "reference": "REG-01",
  "workshopCode": "JAVA-01",
  "attendee": "  Ana  ",
  "email": "Ana@Example.com",
  "memberId": "C-01",
  "tickets": 2
}
```

The response contains:

| Field | JSON type and meaning |
| --- | --- |
| `id` | Number: generated database identifier |
| `reference` | String: supplied reference |
| `workshopCode` | String: supplied workshop code |
| `attendee` | String: stripped attendee name |
| `email` | String: lower-case email |
| `memberId` | String: supplied member ID |
| `tickets` | Number: supplied integer ticket count |

Errors handled by the API contain a string `code`:

| Situation | Status | `code` |
| --- | --- | --- |
| Invalid request fields, null request, invalid batch size or malformed JSON | 400 | `INVALID_INPUT` |
| Missing workshop | 404 | `WORKSHOP_NOT_FOUND` |
| Missing registration | 404 | `REGISTRATION_NOT_FOUND` |
| Closed workshop | 409 | `WORKSHOP_CLOSED` |
| Registration database constraint failure | 409 | `DATA_CONFLICT` |
| Registry reports ineligible member | 422 | `MEMBER_INELIGIBLE` |
| Registry reports missing member with HTTP 404 | 422 | `MEMBER_NOT_FOUND` |
| Registry connection/read failure, including timeout | 503 | `REGISTRY_UNAVAILABLE` |
| Other registry HTTP errors, unreadable response or incompatible response fields | 502 | `REGISTRY_CONTRACT_ERROR` |

Invalid, rejected and duplicate single registrations do not insert a new row.
A duplicate does not modify the existing row. Do not assert framework-generated
error text, stack traces or unspecified response-field ordering.

### Registry consumer and provider

The supplied provider exposes `GET /members/{memberId}`. `C-01` is eligible and
`C-02` is ineligible. For either known ID it returns 200 with:

```json
{"memberId":"C-01","eligible":true}
```

Unknown IDs return 404 with `{"code":"MEMBER_NOT_FOUND"}`.

The consumer `HttpMembershipGateway` requires a string `memberId` matching the
requested ID and a Boolean `eligible` on a successful response. Missing fields,
wrong types, a different ID and malformed JSON violate that contract. Additional
fields are permitted. A string such as `"true"` is not a Boolean. The error body
of a registry 404 is not required by the consumer to classify the missing member;
it uses the status code.

`registry.base-url`, `registry.connect-timeout-ms` and
`registry.read-timeout-ms` are application properties. Override them for the
controlled environment you create. The provider intentionally has no failure or
sleep endpoints. Simulate unavailable, delayed and incompatible responses using
a local HTTP test server outside the consumer's real HTTP-client boundary.
