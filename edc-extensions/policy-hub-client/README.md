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
participant context and stores the definition **under that same UUID** (not the Policy Hub's own
human-readable policy id, which the hub's ODRL rendering uses as `@id`). Repeating the import
updates the existing definition (idempotent). The response is a standard `IdResponse` whose
`@id` is this UUID, plus a `policyHubName` field carrying the Policy Hub's human-readable policy
id (e.g. `zugriff-konsortium-mitglieder`) for readability. The same name is also stored on the
`PolicyDefinition` itself, as `privateProperties.policyHubName`, so it remains visible on
`GET /v3/policydefinitions/{id}` later too.

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

## UsagePurpose on ACCESS policies + Construct-X use cases

The Policy Hub's `USE_CASE` constraint maps to `UsagePurpose`, and its own `PolicyValidator`
allows `USE_CASE` on both `ACCESS` and `CONTRACT` category policies. Upstream cx-policy only
allowed `UsagePurpose` on `CONTRACT` (usage) policies, and only accepted ~29 real, standardized
Catena-X identifiers (`cx.core.industrycore:1`, `cx.pcf.base:1`, ...) — none of which match the
Construct-X Policy Hub's own, closed use-case catalog
(`UC.bim-coordination`, `UC.quality-assurance`, `UC.material-testing`, `UC.site-documentation`,
`UC.geodata`, see the hub frontend's `use-case-options.data.ts`). Fixed as a further
Construct-X-local patch to `edc-extensions/cx-policy`:

1. Added `UsagePurpose` to `ACCESS_PERMISSION_POLICY_ALLOWED_LEFT_OPERANDS`.
2. Added the hub's 5 `UC.*` values to `UsagePurposeConstraintFunction`'s allowed value set,
   alongside the real Catena-X identifiers.

**A second, more severe bug was uncovered while verifying this**, entirely independent of the
above and not previously exercised by anything in this repo: `UsagePurpose` is bound to
`CATALOG_SCOPE` in `CxPolicyExtension.registerBindings()` (pre-existing, unmodified), but had
**no registered `AtomicConstraintRuleFunction` for `CatalogPolicyContext`** at all (also
pre-existing). Building a catalog for *any* offer whose policy carries a `UsagePurpose`
constraint — `ACCESS` or `CONTRACT`, regardless of this fix — throws
`NullPointerException: Cannot invoke "Boolean.booleanValue()" because the return value of
"Constraint.accept(Constraint$Visitor)" is null`, surfacing as a 502 on `POST
/v3/catalog/request` and taking down catalog generation for the whole contract definition, not
just the one offer. Fixed by additionally registering `UsagePurposeConstraintFunction` for
`CatalogPolicyContext` (mirroring how `MembershipCredentialConstraintFunction` /
`FrameworkAgreementConstraintFunction` already are). Verified end-to-end (catalog → negotiation
→ transfer, `FINALIZED`) for an `ACCESS`-category policy with only a `UsagePurpose` constraint,
using the hub's own unmodified ODRL output.

Note that `UsagePurposeConstraintFunction` is a placeholder (see its own class Javadoc):
`evaluate()` always returns `true`. The value-list check above only applies once, at policy
*creation* time; it is not re-checked during negotiation or transfer.

## FrameworkAgreement without a version (`"DataExchangeGovernance"`)

The Policy Hub's canonical `FrameworkAgreement` value (see `FRAMEWORK_AGREEMENT_VALUE` in
`use-case-options.data.ts`) is `"DataExchangeGovernance"`, with no version suffix.
`FrameworkAgreementConstraintFunction.validate()` used to accept only a single hardcoded literal,
`"DataExchangeGovernance:1.0"` — rejecting the hub's value outright, on an `ACCESS` or `CONTRACT`
policy alike.

This was not a case of a value missing from an allow-list (like `UsagePurpose`'s Construct-X
use cases above): the function's own class Javadoc documents the grammar as
`FrameworkAgreement EQ subtype[:version]` with the version **optional**, and `evaluate()`'s own
parsing (`getFilterPredicate`/`getFilterPredicateLegacy`) already handles a bare subtype with no
version correctly — provable from the pre-existing test `evaluate_rightOperand_onlySubtype`,
which asserts `"dataExchangeGovernance"` (no version) evaluates to `true`. `validate()` was
simply stricter than the function it guards. Fixed by having `validate()` reuse the same
`getFilterPredicate`/`getFilterPredicateLegacy` parsing `evaluate()` already relies on, instead
of a hardcoded string comparison, for both the new and legacy left-operand notation. Verified:
the hub's exact `{"leftOperand": "...FrameworkAgreement", "operator": "eq", "rightOperand":
"DataExchangeGovernance"}` now imports successfully as an `ACCESS` policy.

## `UsagePolicyValidator`: "at least one" actually meant "all"

`PermissionContainsRequiredConstraints` required **every** entry of
`{FrameworkAgreement, UsagePurpose}` to be present on a `CONTRACT`-category permission, even
though its own failure message already read "must include at least the following constraints".
A `CONTRACT` policy with only `FrameworkAgreement` (no `UsagePurpose`), or vice versa, was
therefore always rejected — independent of `DATE_RANGE` and of the two fixes above. Root cause:
the check iterated the whole required set and failed if it found *any* missing entry, instead of
succeeding as soon as it found *any* present entry.

Fixed by inverting the check to genuine "at least one of" semantics (`anyMatch` instead of
`filter(...).findFirst()`), and reworded the message to match what it now actually enforces
("must include **at least one of**..."). No test previously covered the specific
"exactly one of the two present" case — the existing failure-path test only exercised "neither
present", which is correct under both the old and the new logic and would not have caught this.

Verified end-to-end against the Policy Hub's own unmodified seed policies: 3 of the 4 previously
blocked `CONTRACT`-category policies now import (and, spot-checked for one of them, generate a
working catalog) exactly because they carry at least one of `FrameworkAgreement`/`UsagePurpose`.

## Constraint-free `CONTRACT` policies ("no restrictions", `CONTRACT` equivalent)

The Policy Hub can produce a `CONTRACT`-category policy with zero constraints at all — the
`odrl:use` counterpart of the constraint-free `ACCESS` policy
(`oeffentlicher-zugriff-projektdokumentation`), which already worked from the start. This was
blocked by two separate issues, both now fixed:

1. **`AtLeastOneRuleExists` required a `constraint` key to be present**, even though its own
   class Javadoc states its only job is checking for "at least one permission, obligation, or
   prohibition" — no mention of constraints. An unconditional permission (action only, no
   constraint) is valid ODRL and a real shape the Policy Hub produces
   (`@JsonInclude(NON_NULL)` omits a `null` constraint entirely), but was wrongly rejected as "no
   rule exists at all". No test caught this because this module's own test fixtures
   (`rule()`/`policy()`) always emit a `constraint` key, at worst as an empty array — they cannot
   construct the exact "key absent" shape the real system produces. Fixed by dropping the
   constraint requirement from the check, matching its documented purpose.
2. **`PermissionContainsRequiredConstraints` (see above) applied even when a permission had no
   constraints at all**, forcing every `CONTRACT` policy to carry a `FrameworkAgreement` or
   `UsagePurpose` even when it imposes no conditions whatsoever. There is nothing to anchor a
   framework agreement or usage purpose to on an unconditional permission, so — on explicit
   request — the check is now skipped entirely once a permission carries no constraints (an
   empty `constraint` array is treated the same as an absent key). It still applies, unchanged,
   the moment *any* constraint is present.

Verified end-to-end with the exact policy reported as failing
(`{"odrl:permission": [{"odrl:action": {"@id": "odrl:use"}}], ...}`, no constraint at all):
imports, and generates a working catalog. Confirmed as a regression check that a `CONTRACT`
permission that *does* carry a constraint (e.g. `Membership` alone, no `FrameworkAgreement`/
`UsagePurpose`) is still correctly rejected — the relaxation only applies to genuinely
constraint-free permissions.

## Known limitation (not fixed)

**Multi-value right operands (`odrl:isAnyOf`, used by `UsagePurpose` and others) are serialized
as a garbled Java `toString()` dump instead of a JSON array** when read back via the Management
API or rendered in a DSP catalog — a pre-existing, already publicly reported upstream bug
([tractusx-edc#3043](https://github.com/eclipse-tractusx/tractusx-edc/issues/3043), reported for
`BusinessPartnerNumber`, confirmed here to also affect `UsagePurpose`). Harmless for
`UsagePurpose` specifically (placeholder, ignores its right operand at evaluate time), but would
likely break round-tripping a catalog offer into a negotiation for any constraint that is
actually enforced against a multi-value right operand.

**Actually-verified importability of the hub's 7 seed policies** (corrected from an earlier,
untested assumption in this file):

| Seed policy | Category | Constraints | Importable? |
|---|---|---|---|
| `oeffentlicher-zugriff-projektdokumentation` | ACCESS | none | ✅ verified end-to-end |
| `zugriff-konsortium-mitglieder` | ACCESS | Membership | ✅ verified end-to-end |
| `materialpruefberichte-mitglieder` | ACCESS | Membership, UsagePurpose, DATE_RANGE | ✅ import + catalog verified; negotiation not re-confirmed after a later, unrelated DCP credential-scope error in the local environment |
| `datenaustausch-deg-rahmenvertrag` | CONTRACT | FrameworkAgreement | ✅ import + catalog verified |
| `baustellendaten-qualitaetspruefung` | CONTRACT | UsagePurpose | ✅ import + catalog verified |
| `bim-koordination-q2-2027` | CONTRACT | Membership, FrameworkAgreement, DATE_RANGE | ✅ import verified |
| `geodaten-bis-2027` | CONTRACT | Membership, DATE_RANGE | ❌ **not a bug**: has neither `FrameworkAgreement` nor `UsagePurpose` at all, so it correctly fails the (now-fixed) "at least one of" check. Only fixable by giving this specific seed policy one of the two constraints, in the Policy Hub. |

6 of 7 seed policies are now importable end-to-end. The remaining one is a data-modeling gap in
the seed data itself, not a code defect.
