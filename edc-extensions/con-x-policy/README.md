# Construct-X Policy Extension (`con-x-policy`)

This module provides the ODRL policy functions and the policy definition validation of the Construct-X
connector (package `org.constructx.edc.policy`). It makes sure that policies emitted by the Construct-X Policy
Hub (see [`policy-hub-client`](../policy-hub-client/README.md)) are accepted when a policy definition is created
and evaluate correctly at catalog, negotiation and transfer time.

## Setup

`ConxPolicyExtension` registers the policy functions, the rule bindings and `ConxPolicyDefinitionValidator` as
the JSON validator for policy definitions. It builds on the classes of [`cx-policy`](../cx-policy), which stays
unmodified.

`ConxPolicyExtension` must run **instead of** `CxPolicyExtension`, not next to it. EDC composes validators (all
registered validators must pass), so the stricter validation of `CxPolicyExtension` would otherwise still
apply. The Construct-X control plane distributions therefore remove
`org.eclipse.tractusx.edc.policy.cx.CxPolicyExtension` from the merged
`META-INF/services/org.eclipse.edc.spi.system.ServiceExtension` file (see `ExcludingServiceFileTransformer` in
their `build.gradle.kts`).

The extension injects `BusinessPartnerStore` and `BdrsClient`, so the runtime also needs `bpn-validation` and
`bdrs-client`.

## Policy behavior

### Date constraints

`DataUsageStartDate`, `DataUsageEndDate` and `DataProvisioningEndDate`:

- `DataUsageStartDate` and `DataUsageEndDate` are allowed in `ACCESS` and `CONTRACT` policies.
- Start dates accept the operators `eq` and `gteq`, end dates accept `eq` and `lteq`.
- Values can be full ISO date-times or bare ISO dates such as `"2026-08-01"`. A bare start date is interpreted
  as 00:00:00 UTC, a bare end date as 23:59:59 UTC of that day.
- The constraints are evaluated in the `transfer.process` and `policy.monitor` scopes.

Implementation: `DataUsageStartDateConstraintFunction`, `ConxDataUsageEndDateConstraintFunction`,
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

### `connector` (Participant selection)

The Policy Hub's "Participant selection" constraint uses EDC's own built-in left operand
`https://w3id.org/edc/v0.0.1/ns/connector` (not a Catena-X-namespaced one) with operator `eq` and a right operand
that is a single comma-separated string of one or more `did:...` values, e.g.
`"did:web:adesso.de,did:web:adesso-group.com"`. EDC core defines this left operand but implements no evaluation
semantics for it, so `ConnectorConstraintFunction` (no upstream equivalent) provides them.

- Allowed only in `ACCESS` permission policies, not in `CONTRACT` (usage) policies.
- The participant's identity is resolved to a DID for comparison: used as-is if already `did:`-prefixed, otherwise
  resolved up from a BPN via `BdrsClient.resolveDid(...)` — the inverse of the `BusinessPartnerGroup`/
  `BusinessPartnerNumber` functions, which resolve a DID down to a BPN.
- Like `BusinessPartnerGroup`/`BusinessPartnerNumber`, this constraint is evaluated only in the `catalog` scope: it
  governs whether an offer is visible in the catalog for a given participant, not whether a subsequent negotiation
  or transfer is accepted.

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

### Logical constraints

`odrl:and` and `odrl:or` are allowed and can be nested (e.g. an `and` inside an `or`). `odrl:xone` and
`odrl:andSequence` are rejected.

Things to keep in mind with `odrl:or`:

- **Validation looks at all branches together.** The required-constraints and mutual-exclusion checks collect
  left operands across all branches. `or(DataUsageEndDate, DataUsageEndDurationDays)` is therefore rejected as
  mutually exclusive, and a `FrameworkAgreement` in any branch satisfies the "at least one of" requirement.
- **Branches can disappear during evaluation.** Before evaluating, EDC drops constraints whose left operand is
  not bound to the current scope. Access policies are only evaluated in the `catalog` and
  `contract.negotiation` scopes, and the date constraints are bound to neither. In an access policy, an `or`
  branch consisting only of date constraints therefore never grants access on its own.

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
