# HomeSpace Gateway — Codex Instructions

## Engineering standards

- Preserve business invariants, public contracts and backward compatibility. Keep authentication, authorization, input validation and error handling intact.
- Protect secrets and sensitive data in code, commits, tests, logs and reports. Treat third-party hooks as untrusted until reviewed and explicitly authorized.
- Change production configuration, deploy or run destructive data operations only when explicitly authorized for the target environment.
- Keep review tasks read-only unless the developer requests fixes.
- Run targeted verification first. Broaden coverage when the risk or affected interfaces warrant it. Report checks that ran and any unverified behavior.

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
