# Security Policy

## About this project

This is a **demonstration application** built as a code assignment. It is not a released product, it is not deployed anywhere, and it is **not hardened for production use**.

Please read *Known limitations* below before reporting anything — the significant gaps are already known and documented, and reports about them are duplicates rather than new findings.

## Supported versions

This project has no releases and no version history. Only the current state of the default branch is maintained.

| Branch | Supported |
| ------ | --------- |
| `master` | :white_check_mark: |
| Any fork or older checkout | :x: |

## Reporting a vulnerability

If you find something genuinely not covered below, please report it **privately**. Do not open a public issue for a security problem.

**Preferred:** use GitHub's private vulnerability reporting — the *Security* tab → *Report a vulnerability*. This creates a private advisory visible only to the maintainer.

Please include:

- What the problem is, and which file or endpoint it affects
- How to reproduce it, ideally with a request or a failing test
- What an attacker could actually achieve

**What to expect.** This is a personal project maintained on a best-effort basis, not a product with a staffed security team. Expect an acknowledgement within about a week. If a report is accepted, the fix and any credit will be discussed in the advisory before anything is made public. If it is declined — usually because it is already listed below, or because it depends on a deployment scenario this project does not claim to support — you will get a straight explanation rather than silence.

Please allow a reasonable window before disclosing publicly.

## Scope

**In scope**

- Application code under `java-assignment/src/main/java/`
- The API contract in `warehouse-openapi.yaml`
- Build configuration in `pom.xml`

**Out of scope**

- Anything in *Known limitations* below
- Vulnerabilities in Quarkus, Hibernate, PostgreSQL or other third-party dependencies — report those upstream. A dependency version bump is welcome as a normal pull request.
- Findings that require access to the machine running the application
- The demo database credentials in `application.properties` — see below

## Known limitations

These are known, deliberate for a demonstration, and **not** accepted as vulnerability reports. All originate in the project skeleton this assignment was built on.

| Area | Status |
| ---- | ------ |
| **Schema generation** | `quarkus.hibernate-orm.database.generation=drop-and-create` is not profile-scoped, so it applies to every profile. Running the packaged application against a real database will drop and reseed it on boot. Do not point this at data you care about. |
| **Credentials** | `application.properties` contains demo database credentials in plaintext. They grant access to nothing but a local throwaway container. |
| **Authentication** | There is none. No security extension is declared and no endpoint is authorized. Every operation is open to any client that can reach the port. |
| **Error responses** | Error payloads include the internal exception class name and message. |
| **SQL logging** | Enabled in all profiles; statements and bound parameter values reach the logs. |
| **Request binding** | `ProductResource` and `StoreResource` bind request bodies directly into JPA entities, so the client controls every mapped column. |
| **Rate limiting** | None. No request-size limits and no pagination on list endpoints. |

Anyone intending to run this beyond a local demo should treat all of the above as work to be done first.

## What has been checked

- **SQL injection** — every database query uses positional parameters; there is no string concatenation into queries.
- **Committed secrets** — none beyond the demo database credentials noted above.
- **Layering** — the warehouse domain layer imports no transport, persistence or transaction types, so business rules cannot be bypassed through an adapter.
