---
name: security-review
description: Review HomeSpace API Gateway changes involving authentication, authorization, routing, trusted headers, internal secrets and security filters. Use when explicit gateway security review is requested.
---

# Gateway Security Review

## Mode

Review-only.

## Checklist

- Authentication enforcement
- Authorization rules
- Trusted header propagation
- Internal service identity
- Secret handling
- Route exposure
- Rate limiting
- Error information leakage
- Downstream trust boundaries

## Verification

Inspect changed security components and configurations first.

Run focused security tests when necessary.

Do not weaken security controls to make tests pass.

Do not start the whole platform automatically.

## Output

Report findings with severity, affected file, exploit or failure scenario and suggested correction.
