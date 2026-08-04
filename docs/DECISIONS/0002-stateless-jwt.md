# 0002: Stateless bearer access JWT authentication

- **Status:** Accepted
- **Date:** 2026-08-04
- **Decision owners:** Project KEYSTONE engineering
- **Supersedes:** None

## Context

KEYSTONE is a browser-based API application for exactly four roles: `DISPATCHER`, `TECHNICIAN`, `MANAGER`, and `CUSTOMER`. The required stack includes Spring Security with stateless JWT authentication. Direct API calls must receive the same protection as UI-originated calls.

Authentication establishes identity but does not by itself enforce customer-organisation isolation, technician ownership, or field visibility.

## Decision

- Authenticate email/password login through `POST /api/v1/auth/login` and return an expiring access JWT plus a safe current-user summary.
- Send the token on protected requests as `Authorization: Bearer <token>`.
- Configure Spring Security as stateless. Do not create or depend on an HTTP server session.
- Do not add OAuth2/social login or an implicit refresh-token flow.
- Store passwords only as BCrypt hashes. Never persist or log plaintext passwords, tokens, or signing material.
- Keep signing secrets or keys in external configuration; never place them in source, images, migrations, seed data, or the browser bundle.
- Limit claims to the principal data required by the security layer, such as user identifier, normalized email, role, issue/expiry timing, and standard validation metadata. Do not embed sensitive domain records.
- Verify signature, supported algorithm, issuer/audience when configured, and expiry before protected feature code runs. Missing, malformed, tampered, unsupported, and expired tokens return the stable JSON `401` contract.

Authorization remains server-side after authentication:

- route and action rules recognize only the four required roles; there is no `ADMIN` role;
- customer reads and commands are scoped to the authenticated user's customer organisation;
- technician reads and ownership-sensitive commands are scoped to work orders currently assigned to that technician;
- a valid but insufficient principal receives `403` for a coarse route/action denial;
- out-of-scope direct identifiers use the same scoped `404` shape as nonexistent resources; and
- UI routes and hidden controls are usability measures, never security boundaries.

Business services consume an authenticated-principal abstraction rather than parsing headers or tokens themselves. API representations use explicit role-safe DTOs; persistence entities are never exposed directly.

## Consequences

### Positive

- API instances do not require shared server-session state.
- The SPA, Swagger-authorized calls, and integration tests use one authentication mechanism.
- Token failure is rejected consistently before business execution.
- Organisation and assignment scoping remain explicit, testable authorization concerns.

### Costs and constraints

- An access token normally remains usable until expiry unless a later revocation policy is approved.
- Browser storage, XSS defense, TLS, signing-key rotation, log redaction, and short-enough expiry are essential controls.
- Role, active-state, password, or organisation changes may require an approved freshness/revocation policy to take effect before token expiry.

## Alternatives Considered

### Server-side sessions

Rejected because the required architecture is stateless JWT and the playbook excludes server sessions.

### OAuth2/OIDC or social login

Rejected for the current scope; no external identity provider is required.

### Access and refresh token pair

Not selected. Rotation, storage, replay, and revocation behavior are not specified.

## Follow-up and Open Questions

Token lifetime, clock skew, algorithm/key rotation, issuer/audience, browser storage, revocation/freshness, login throttling, password recovery, CORS, and production OpenAPI exposure remain Q-013 and related security questions in `../ASSUMPTIONS.md`.

## References

- `../ACCESS_MATRIX.md`
- `../API_CONTRACT.md`
- `../reference/keystone_codex_build_playbook.md`
