# Construct-X Policy evaluation registry + DCP Enhancement
This branch enhances Construct-X-specific policy evaluation and DCP scope handling by introducing dedicated extensions instead of relying on Catena-X/Tractus-X defaults.
## Why this exists
Construct-X needs explicit control over:
- policy operand interpretation
- credential scope extraction
- default scope behavior across DSP contexts
- control-plane dependency wiring
Without this, behavior is implicit, coupled to external profile defaults, and harder to maintain/debug.
---
## What was added
### 1) Construct-X Policy Evaluation Registry
New module:
- `edc-extensions/constructx-policy/policy-evaluation-registry`
Main additions:
- registry bootstrap (`ConstructxPolicyEvaluationRegistry`)
- dynamic credential constraint base logic (`AbstractDynamicCredentialConstraintFunction`)
- membership credential constraint evaluation (`MembershipCredentialConstraintFunction`)
- policy scope binding and registration helpers
- service extension registration (`META-INF/services/...ServiceExtension`)
Purpose: make Construct-X policy evaluation explicit and extensible.
---
### 2) Construct-X DCP Extension
New module:
- `edc-extensions/dcp/con-x-dcp`
Main additions:
- Construct-X DCP constants (`DcpConstants`)
- default scope extension (`DcpDefaultScopeExtension`)
- scope extractor extension (`DcpScopeExtractorExtension`)
- policy-to-scope mapping (`CredentialScopeExtractor`)
- service extension registration
- test coverage for default scope behavior (`DcpDefaultScopeExtensionTest`)
Purpose: map Construct-X policy operands to DCP VC read scopes in a controlled way.
---
### 3) Runtime Integration
- `settings.gradle.kts` updated to include new modules
- Construct-X control plane wiring updated to use `con-x-dcp`
Purpose: ensure the runtime actually uses Construct-X extensions end-to-end.
---
## What this improves
- Creates a clear foundation for dynamically adding additional Construct-X policy evaluations
- Reduces coupling to TX/CX-specific defaults
- Makes policy/scope behavior deterministic (good foundation to handle multiple namespaces)

---
## Changed areas (high level)
- `edc-extensions/constructx-policy/policy-evaluation-registry/**`
- `edc-extensions/dcp/con-x-dcp/**`
- `edc-controlplane/edc-controlplane-construct-x/con-x-controlplane-postgresql-hashicorp-vault/build.gradle.kts`
- `settings.gradle.kts`