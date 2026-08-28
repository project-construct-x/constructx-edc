# Construct-X Policy Hub Client

This extension lets the **provider control plane** pull ODRL policies from the
[Construct-X Policy Hub](https://github.com/project-construct-x/policy-hub) and store them as
local `PolicyDefinition`s, so they can be referenced as `accessPolicyId` / `contractPolicyId`
in contract definitions.

## Endpoint

Registered on the Management API context (and therefore protected by the management API's
token auth):

```
POST /management/v1alpha/policyhub/policies/{hubPolicyId}/import
```

`hubPolicyId` is the UUID of the policy in the Policy Hub. The control plane fetches
`GET {hub}/api/v1/policies/{hubPolicyId}/odrl`, runs the response through the same JSON-LD
expansion, validation and transformation steps as `POST /v3/policydefinitions`, stamps the
participant context and stores the definition. Repeating the import updates the existing
definition (idempotent). The response is a standard `IdResponse` carrying the policy id as
maintained in the hub (e.g. `zugriff-konsortium-mitglieder`).

## Configuration

| Setting                                  | Required | Default                  | Description                                                                                          |
|------------------------------------------|----------|--------------------------|------------------------------------------------------------------------------------------------------|
| `constructx.policyhub.url`               | no       | –                        | Base URL of the Policy Hub. **If unset, the import API is not registered** (consumer deployments).   |
| `constructx.policyhub.credentials.alias` | no       | `policy-hub-credentials` | Vault alias holding the hub's basic-auth credentials in `user:password` format.                      |

The credentials are resolved from the vault on every request; they never appear in
configuration files.

## Prerequisites in the distribution

The Catena-X policy functions must be on the runtime classpath
(`org.eclipse.tractusx.edc:cx-policy` plus its mandatory injects from `bpn-validation-core`
and `bdrs-client`), otherwise `edc.policy.validation.enabled=true` (the default) rejects hub
policies at creation time because their left operands are unknown to the policy engine. The
Construct-X control plane distributions already include these.

## Known limitation

The Policy Hub maps its `DATE_RANGE` constraint to `DataUsageStartDate` + `DataUsageEndDate`
(namespace `https://w3id.org/catenax/2025/9/policy/`). cx-policy 0.12.0 knows only
`DataUsageEndDate` — there is no `DataUsageStartDate` constraint function or validator entry.
Hub policies containing a `DATE_RANGE` constraint therefore fail validation on import.

Of the hub's seed policies this affects `geodaten-bis-2027`, `bim-koordination-q2-2027` and
`materialpruefberichte-mitglieder`. Importable are the constraint-free
`oeffentlicher-zugriff-projektdokumentation`, `zugriff-konsortium-mitglieder` (Membership),
`baustellendaten-qualitaetspruefung` (UsagePurpose) and `datenaustausch-deg-rahmenvertrag`
(FrameworkAgreement). Whether the hub changes its mapping or the connector gains a
`DataUsageStartDate` constraint function is an open, separate decision.
