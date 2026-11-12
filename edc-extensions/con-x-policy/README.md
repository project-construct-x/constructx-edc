# Construct-X Policy Extension (`con-x-policy`)

This module provides the ODRL policy functions and the policy definition validation of the Construct-X
connector (package `org.constructx.edc.policy`). It makes sure that policies emitted by the Construct-X Policy
Hub (see [`policy-hub-client`](../policy-hub-client/README.md)) are accepted when a policy definition is created
and evaluate correctly at catalog, negotiation and transfer time.

## Setup

`ConxPolicyExtension` registers the policy functions, the rule bindings and `ConxPolicyDefinitionValidator` as
the JSON validator for policy definitions. It is the only policy extension that runs in Construct-X; the Catena-X
`CxPolicyExtension` is not used. Individual classes of [`cx-policy`](../cx-policy) (constraint functions, validator
building blocks) are reused unmodified as a library where their behavior fits.

Because the library classes come with the module, the Catena-X `CxPolicyExtension` is on the classpath too. It
must never be loaded next to `ConxPolicyExtension`: EDC composes validators (all registered validators must pass),
so its stricter validation would otherwise still apply. The Construct-X control plane distributions therefore remove
`org.eclipse.tractusx.edc.policy.cx.CxPolicyExtension` from the merged
`META-INF/services/org.eclipse.edc.spi.system.ServiceExtension` file (see `ExcludingServiceFileTransformer` in
their `build.gradle.kts`).

Construct-X deliberately does not depend on the Catena-X BPN/BDRS infrastructure: the runtime needs neither
`bpn-validation` nor `bdrs-client`. Consequently `BusinessPartnerGroup` and `BusinessPartnerNumber` are not
supported. No function is registered for them, and the validator rejects them as left operands. Participants are
selected by DID with the [`connector`](#connector-participant-selection) constraint instead.

## Policy behavior

### Date constraints

`DataUsageStartDate`, `DataUsageEndDate` and `DataProvisioningEndDate`:

- `DataUsageStartDate` and `DataUsageEndDate` are allowed in `ACCESS` and `CONTRACT` policies.
- Start dates accept the operators `eq` and `gteq`, end dates accept `eq` and `lteq`.
- Values can be full ISO date-times or bare ISO dates such as `"2026-08-01"`. A bare start date is interpreted
  as 00:00:00 UTC, a bare end date as 23:59:59 UTC of that day.
- In a `CONTRACT` policy they are evaluated in the `transfer.process` scope (a transfer cannot start outside the
  period) and in the `policy.monitor` scope (a running transfer is terminated after the end date).
- In an `ACCESS` policy they are evaluated in the `catalog` scope, the only scope an access policy is evaluated in
  (catalog and initial offer validation of a negotiation): outside the period the offer is not shown in the catalog
  and a negotiation for it is rejected. Contracts that already exist are not affected, their period comes from the
  contract policy. Only `DataUsageStartDate` and `DataUsageEndDate` work this way; `DataProvisioningEndDate` is a
  duty and is not allowed in access policies.

Implementation: `DataUsageStartDateConstraintFunction`, `ConxDataUsageEndDateConstraintFunction`, the catalog variants
`CatalogDataUsageStartDateConstraintFunction` and `CatalogDataUsageEndDateConstraintFunction` (time from the injected
`Clock`, the catalog context has no contract agreement),
`ConxDataProvisioningEndDateConstraintFunction` (based on `AbstractDataStartDateConstraintFunction` and
`ConxAbstractDataEndDateConstraintFunction`).

### `UsagePurpose`

`UsagePurpose` is evaluated in the `catalog`, `contract.negotiation` and `transfer.process` scopes, so offers
with a `UsagePurpose` constraint show up in the catalog.

The allowed values are checked when a policy definition is created. At evaluation time the constraint always
passes.

### `FrameworkAgreement`

The version is optional: both `"DataExchangeGovernance"` and `"DataExchangeGovernance:1.0"` are valid, in the
new as well as in the legacy notation (`ConxFrameworkAgreementConstraintFunction`).

Evaluation is unchanged from upstream: it requires a Catena-X `DataExchangeGovernanceCredential` (or the
credential named by the sub-type). There is no Construct-X credential that states a framework agreement, so
`basic-abac` cannot take over here. Policies with `FrameworkAgreement` are importable, but the constraint only
passes for participants that hold such a credential.

### `Membership` (evaluated through `basic-abac`)

`Membership eq "active"` is accepted as before (same left operand, operator and right operand as upstream), so Policy
Hub policies with a `MEMBERSHIP` constraint import unchanged. The evaluation differs: upstream requires a Catena-X
`MembershipCredential`, which Construct-X participants do not hold. `ConxMembershipConstraintFunction` (a subclass
of the upstream function) evaluates the constraint through the [`basic-abac`](../basic-abac/README.md) extension
instead, as the credential attribute

```
https://w3id.org/constructx/credentials/v1.0/ConstructXMembershipCredential.credentialSubject.isMember eq true
```

`ConstructXMembershipCredential` is the `basic-abac` default credential, which the consumer presents in every
request, so the constraint works in the catalog as well as in negotiation and transfer.

### `connector` (Participant selection)

The Policy Hub's "Participant selection" constraint uses EDC's own built-in left operand
`https://w3id.org/edc/v0.0.1/ns/connector` (not a Catena-X-namespaced one) with operator `eq` and a right operand
that is a single comma-separated string of one or more `did:...` values, e.g.
`"did:web:adesso.de,did:web:adesso-group.com"`. EDC core defines this left operand but implements no evaluation
semantics for it, so `ConnectorConstraintFunction` (no upstream equivalent) provides them.

- Allowed only in `ACCESS` permission policies, not in `CONTRACT` (usage) policies.
- The participant's identity must be a DID (Construct-X uses DIDs as participant ids) and is compared as-is. Any
  other identity (e.g. a BPN) is rejected. There is no BPN-to-DID resolution via BDRS.
- The constraint is evaluated only in the `catalog` scope: it governs whether an offer is visible in the catalog for a
  given participant, not whether a subsequent negotiation or transfer is accepted.

Implementation: `ConnectorConstraintFunction`.

### `DataSubset`

The Policy Hub's `DataSubset` constraint restricts an offer to a subset of the data. Its left operand lives in the
Construct-X namespace, `https://w3id.org/construct-x/policy/DataSubset`; there is no upstream equivalent.

```json
{
  "odrl:leftOperand": { "@id": "https://w3id.org/construct-x/policy/DataSubset" },
  "odrl:operator": { "@id": "odrl:isAnyOf" },
  "odrl:rightOperand": ["field:materialName", "attribute:color", "submodel:urn:samm:com.example.material:1.0.0#Material", "record:2027-Q1"]
}
```

- Allowed in `ACCESS` and `CONTRACT` permission policies.
- Operator `isAnyOf` only. The right operand is a list of unique values, each starting with `field:`, `attribute:`,
  `submodel:` or `record:` followed by at least one character.
- Like `UsagePurpose`, the values are checked when a policy definition is created, and evaluation always passes. It is
  bound to the `catalog`, `contract.negotiation` and `transfer.process` scopes. The connector does not filter the data
  itself: it carries the restriction in the offer and the contract agreement, and the provider's backend enforces it.
- `DataSubset` does not count as a required usage constraint: a `CONTRACT` permission with `DataSubset` must still
  contain `FrameworkAgreement` or `UsagePurpose` (see [Required usage constraints](#required-usage-constraints)).

Implementation: `DataSubsetConstraintFunction`.

### Required usage constraints

A `CONTRACT` permission with constraints must contain **at least one of** `FrameworkAgreement` and
`UsagePurpose` (`ConxUsagePolicyValidator`).

A `CONTRACT` permission without any constraints is valid as well, with or without a `constraint` key. This is
what the hub generates for a constraint-free `ACCESS` policy (`ConxAtLeastOneRuleExists`,
`ConxUsagePolicyValidator`).

### Credential attributes (`basic-abac`)

Besides the left operands listed in the validator, a permission may constrain a **credential attribute** as handled
by the [`basic-abac`](../basic-abac/README.md) extension, i.e. a left operand of the form

```
https://<host>/<path>/<CredentialType>.credentialSubject.<attribute>
```

for example `https://w3id.org/constructx/credentials/v1.0/ConstructXMembershipCredential.credentialSubject.isMember`.

- **Where:** in permissions of `ACCESS` and of `CONTRACT` policies. Not in prohibitions or obligations: `basic-abac`
  only evaluates permissions, so there the constraint would validate but never be enforced.
- **Required constraints:** a credential attribute satisfies the "at least one of `FrameworkAgreement` and
  `UsagePurpose`" requirement above, so a `CONTRACT` policy made of credential attributes alone is valid.
- **One pattern:** the validator uses `BasicAbacUtils.BASIC_ABAC_PATTERN` from `basic-abac` itself (module
  dependency), so what validates is exactly what `basic-abac` evaluates. A near miss such as `http://` instead of
  `https://`, a missing `.credentialSubject` segment or a lower-case credential type is rejected, and the error
  message names the expected form.
- **Why it is needed:** the validator accepts only a fixed set of left operands, which rejected every credential
  attribute. With `basic-abac` loaded in the same distribution, such policies could neither be created through
  `POST /v3/policydefinitions` nor imported from the Policy Hub (same validator).
- **Catalog requests:** a catalog request carries no policy, so the consumer only presents the `basic-abac` default
  credential (`edc.abac.defaultcredential`, by default `ConstructXMembershipCredential`). In an `ACCESS` policy, only
  attributes of that credential can therefore grant access. An attribute of another credential type (e.g.
  `Baustelle123Credential`) hides the offer from the catalog. Verified in the local testbed on 2026-11-12.
- **Right operand as string:** write numbers as strings (`"rightOperand": "4"`). `basic-abac` compares `"4"` and
  `4` as equal. A bare JSON number currently makes `POST /v3/policydefinitions` fail with a `500`
  (`ClassCastException` in `ConxAtomicConstraintValidator`, which uses EDC's `MandatoryValue`).

See the `basic-abac` README for how to write right operands (e.g. set operators need a stringified JSON list).
Implementation: `ConxPolicyValidationConstants.isAbacLeftOperand`, used by `ConxLeftOperandValidator` and
`ConxUsagePolicyValidator`.

### Logical constraints

`odrl:and` and `odrl:or` are allowed and can be nested (e.g. an `and` inside an `or`). `odrl:xone` and
`odrl:andSequence` are rejected.

Things to keep in mind with `odrl:or`:

- **Validation looks at all branches together.** The required-constraints and mutual-exclusion checks collect
  left operands across all branches. `or(DataUsageEndDate, DataUsageEndDurationDays)` is therefore rejected as
  mutually exclusive, and a `FrameworkAgreement` in any branch satisfies the "at least one of" requirement.
- **Branches can disappear during evaluation.** Before evaluating, EDC drops constraints whose left operand is
  not bound to the current scope. Access policies are only evaluated in the `catalog` and
  `contract.negotiation` scopes. The date constraints `DataUsageStartDate` and `DataUsageEndDate` are bound to the
  `catalog` scope, but the other date-like operands (e.g. `inForceDate`, which needs a contract agreement) are not,
  so an `or` branch consisting only of those never grants access on its own.

## Known limitation: multi-value right operands

Right operands with several values (`odrl:isAnyOf`, used by `UsagePurpose`, `DataSubset` and others) are stored and
returned as a garbled Java `toString()` dump instead of a JSON array. A `DataSubset` policy imported from the Policy
Hub looks like this when read back via `GET /v4/policydefinitions/{id}` and in the consumer's catalog:

```json
"rightOperand": "[{@value={chars=field:materialName, string=field:materialName, valueType=STRING}}, {@value={chars=attribute:color, ...}}]"
```

- It is a known upstream bug
  ([tractusx-edc#3043](https://github.com/eclipse-tractusx/tractusx-edc/issues/3043), reported for
  `BusinessPartnerNumber`) and is not caused by this module. It was still reproducible on 2026-11-06 with EDC 0.17.0
  in the Construct-X distribution.
- The value is already in this form in the provider's database, not only when it is rendered.
- `UsagePurpose` and `DataSubset` do not evaluate their right operand (they always pass), so catalog, negotiation,
  transfer and data access still work: the full flow with a hub policy containing `DataSubset` ran through.
  The consumer does, however, receive the garbled value, and a backend that enforces `DataSubset` from the offer
  or the contract agreement cannot use it. A constraint that is evaluated against a multi-value right operand would
  likely break when a catalog offer is passed into a negotiation.

## Policy definition validation

`ConxPolicyDefinitionValidator` is the entry point. Its chain consists of:

- `ConxAccessPolicyValidator`, `ConxUsagePolicyValidator`, `ConxAtLeastOneRuleExists`
- `ConxConstraintValidator`, `ConxLogicalConstraintValidator`, `ConxAtomicConstraintValidator`,
  `ConxLeftOperandValidator`
- `ConxPolicyValidationConstants`, which holds the allowed left operands per policy type
