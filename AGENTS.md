# HomeSpace Gateway — Codex Instructions

## Scope

API Gateway and security boundary.

Treat authentication, authorization, route changes and internal identity propagation as high-risk operations.

## Development

- Preserve existing routing behavior.
- Preserve security filters.
- Preserve trusted identity propagation.
- Do not weaken authentication or authorization.
- Do not bypass internal secret verification.
- Preserve rate limiting and error handling where applicable.
- Avoid unrelated changes.

## Verification

- Inspect affected route and filter logic.
- Run targeted security tests.
- Validate relevant configuration.
- Expand verification to downstream interfaces when necessary.
- Do not start all services by default.

## Safety

Never expose internal shared secrets.

Never modify production gateway configuration without authorization.

## Skills

Use security-review for explicit gateway security review.

## Completion

Report security impact, verification and unresolved risks.
