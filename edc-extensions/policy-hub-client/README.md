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

The Catena-X policy functions must be on the runtime classpath, otherwise
`edc.policy.validation.enabled=true` (the default) rejects hub policies at creation time because
their left operands are unknown to the policy engine. Concretely, the Construct-X control plane
distributions (`con-x-controlplane-postgresql-hashicorp-vault` and `-vault`) depend on the
**local** `edc-extensions:cx-policy`, `edc-extensions:bpn-validation` and
`edc-extensions:bdrs-client` project modules (not the `org.eclipse.tractusx.edc:*` Maven
artifacts) — this is required, not just a style choice, because `cx-policy` carries a
Construct-X-local patch (see "DATE_RANGE support" below) that only exists in this repository's
source, not in the published 0.12.0 jar. There is no Gradle dependency substitution in this
repo, so pointing a distribution at the Maven coordinate instead of the project would silently
build against the unpatched upstream code.

## DATE_RANGE support (`DataUsageStartDate` / `DataUsageEndDate`)

The Policy Hub maps its `DATE_RANGE` constraint to two atomic ODRL constraints, both in
namespace `https://w3id.org/catenax/2025/9/policy/`:

- `DataUsageStartDate` `odrl:gteq` `<start-date>`
- `DataUsageEndDate` `odrl:lteq` `<end-date>`

`<start-date>`/`<end-date>` are bare ISO-8601 dates (e.g. `"2026-08-01"`), not full timestamps.

Upstream cx-policy 0.12.0 supported neither of these as actually usable end-to-end:
`DataUsageEndDate` existed (added in
[tractusx-edc#2668](https://github.com/eclipse-tractusx/tractusx-edc/pull/2668)) but only for
`CONTRACT`-category (`odrl:use`) permissions, only accepted the `EQ` operator, and only parsed
full ISO-8601 date-*times*; `DataUsageStartDate` did not exist at all — confirmed absent from
every tractusx-edc release up to the latest at the time of writing
([0.13.0](https://github.com/eclipse-tractusx/tractusx-edc/releases/tag/0.13.0)) and from the
specifications cx-policy's left operands are drawn from (the official
[Catena-X ODRL profile](https://github.com/catenax-eV/cx-odrl-profile/blob/main/profile.ttl) and
the older CX-3.2
[policy definitions](https://github.com/eclipse-tractusx/ssi-docu/blob/main/docs/architecture/cx-3-2/edc/policy.definitions.md)
list neither a start-date operand at all).

Rather than changing the Policy Hub's mapping, this was fixed on the connector side, as a
Construct-X-local patch to the vendored `edc-extensions/cx-policy` source
(a deliberate, one-off exception to this repository's "no Java connector code" principle —
see the module's own class-level comments for the exact diff against upstream):

1. Added `DataUsageStartDate` as a new left operand, with a `DataUsageStartDateConstraintFunction`
   mirroring the existing `DataUsageEndDateConstraintFunction` (`"now" >= start date`
   instead of `"now" <= end date`), bound to the same `TransferProcessPolicyContext` /
   `PolicyMonitorContext` scopes.
2. Added both `DataUsageStartDate` and `DataUsageEndDate` to
   `ACCESS_PERMISSION_POLICY_ALLOWED_LEFT_OPERANDS` (previously `CONTRACT`-only), so a
   `DATE_RANGE` constraint validates on `ACCESS`-category policies too, matching the Policy
   Hub's own `PolicyValidator` (`DATE_RANGE` is declared valid for both `PolicyCategory.ACCESS`
   and `.CONTRACT`).
3. Widened the allowed operators from `{EQ}` to `{EQ, GEQ}` (start) / `{EQ, LEQ}` (end) — the
   actual comparison direction is hardcoded in `evaluate()` regardless of the ODRL operator, so
   this only relaxes the input-validation check to match what a real date *range* naturally
   uses (`gteq`/`lteq`), without changing runtime enforcement semantics.
4. Accepted bare ISO-8601 dates (`"2026-08-01"`) in addition to full date-times, interpreting a
   bare start date as the beginning of that day (00:00:00 UTC) and a bare end date as the end of
   that day (23:59:59 UTC, inclusive).

With this in place, both of the following import successfully and enforce at
transfer/monitoring time — verified end-to-end against a running Policy Hub and provider
control plane, using the Hub's own unmodified ODRL output:

- An `ACCESS`-category policy with only a `DATE_RANGE` constraint.
- A `CONTRACT`-category policy combining `DATE_RANGE` with a `FrameworkAgreement`/`UsagePurpose`
  constraint that uses values cx-policy actually accepts (see the two limitations below — the
  Policy Hub's own example values do not qualify).

## Known limitations (not fixed, phased approach)

Two further issues were found in cx-policy 0.12.0 while verifying `DATE_RANGE`. Both are
independent of `DATE_RANGE`/`DataUsageStartDate` and are **not** part of the fix above:

1. **`UsagePolicyValidator` requires ALL of `FrameworkAgreement`/`UsagePurpose`, not "at least
   one".** `PermissionContainsRequiredConstraints` fails if *either* is missing from a
   `CONTRACT`-category permission, even though its own error message reads "must include at
   least the following constraints". A `CONTRACT` policy with only `FrameworkAgreement` (no
   `UsagePurpose`), or vice versa, is therefore always rejected — regardless of `DATE_RANGE`.
2. **`FrameworkAgreementConstraintFunction`/`UsagePurposeConstraintFunction` accept only a
   closed, hardcoded set of real Catena-X identifiers** (e.g. exactly
   `"DataExchangeGovernance:1.0"` for framework agreements; a fixed list of ~29 `cx.*` usage
   purpose IDs). The Policy Hub's own example/seed values (`"DataExchangeGovernance"`,
   `"UC.quality-assurance"`, `"UC.material-testing"`) match none of these and are always
   rejected.

**Actually-verified importability of the hub's 7 seed policies** (corrected from an earlier,
untested assumption in this file — `oeffentlicher-zugriff-projektdokumentation` and
`zugriff-konsortium-mitglieder` were the only ones ever actually confirmed end-to-end):

| Seed policy | Category | Constraints | Importable? |
|---|---|---|---|
| `oeffentlicher-zugriff-projektdokumentation` | ACCESS | none | ✅ |
| `zugriff-konsortium-mitglieder` | ACCESS | Membership | ✅ |
| `baustellendaten-qualitaetspruefung` | CONTRACT | UsagePurpose | ❌ limitations 1 + 2 |
| `datenaustausch-deg-rahmenvertrag` | CONTRACT | FrameworkAgreement | ❌ limitations 1 + 2 |
| `geodaten-bis-2027` | CONTRACT | Membership, DATE_RANGE | ❌ limitation 1 (no FA/UP at all) |
| `bim-koordination-q2-2027` | CONTRACT | Membership, FrameworkAgreement, DATE_RANGE | ❌ limitations 1 + 2 |
| `materialpruefberichte-mitglieder` | ACCESS | Membership, UsagePurpose, DATE_RANGE | ❌ UsagePurpose is not an allowed `ACCESS` left operand at all (CONTRACT-only) |

Whether to fix limitation 1 (a genuine logic bug, arguably worth reporting upstream), relax
limitation 2 (either in cx-policy or by changing the Policy Hub's example values to real Catena-X
identifiers), and/or allow `UsagePurpose` on `ACCESS` policies, are separate, open decisions.
