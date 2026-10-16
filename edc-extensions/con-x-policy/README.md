# Construct-X Policy Extension (`con-x-policy`)

Construct-X additions and fixes on top of the Catena-X ODRL policy functions from
[`edc-extensions/cx-policy`](../cx-policy). `cx-policy` itself stays exactly as delivered by
Tractus-X; everything Construct-X-specific lives in this module (package `org.constructx.edc.policy`).

They are needed so that policies emitted by the Construct-X Policy Hub (see
[`policy-hub-client`](../policy-hub-client/README.md)) are accepted at creation time and evaluate
correctly at catalog, negotiation and transfer time.

## How it is wired

`ConxPolicyExtension` takes the role of the upstream `CxPolicyExtension`: it registers the same
policy functions and rule bindings, but with the Construct-X variants listed below and with
`ConxPolicyDefinitionValidator` instead of the upstream policy definition validator.

The two extensions must **not** run side by side. EDC *composes* validators: all JSON object
validators registered for a type must pass, and all `validate()` functions registered for a left operand
must pass. With both extensions active, the stricter upstream validation would still reject what
Construct-X relaxes. The Construct-X control plane distributions therefore remove
`org.eclipse.tractusx.edc.policy.cx.CxPolicyExtension` from the merged
`META-INF/services/org.eclipse.edc.spi.system.ServiceExtension` (see `ExcludingServiceFileTransformer`
in their `build.gradle.kts`). The upstream classes are still on the classpath and are reused wherever
nothing changes.

`ConxPolicyExtension` injects `BusinessPartnerStore` and `BdrsClient` (like upstream), so the runtime
also needs `bpn-validation` and `bdrs-client`.

## Differences to upstream `cx-policy`

- **`DataUsageStartDate` / `DataUsageEndDate` (`DATE_RANGE`)**: adds `DataUsageStartDate`
  (`DataUsageStartDateConstraintFunction`, which does not exist upstream at all) and allows both on
  `ACCESS` policies (upstream: `CONTRACT` only). Accepted operators are widened to `gteq`/`lteq`
  (upstream: `EQ` only), and bare ISO dates like `"2026-08-01"` are accepted besides full date-times
  (start → 00:00:00 UTC, end → 23:59:59 UTC). See `ConxAbstractDataEndDateConstraintFunction`,
  `ConxDataUsageEndDateConstraintFunction` and `ConxDataProvisioningEndDateConstraintFunction`.
- **`UsagePurpose` catalog NPE**: upstream binds `UsagePurpose` to `CATALOG_SCOPE` but registers
  **no** function for `CatalogPolicyContext`. Building a catalog for any offer with a `UsagePurpose`
  constraint then throws an NPE, which breaks catalog generation for the whole contract definition
  (502 on `POST /v3/catalog/request`). `ConxPolicyExtension` also registers the function for that
  context. Apart from that, `UsagePurpose` is unchanged from upstream.
- **`FrameworkAgreement` without a version**: upstream `validate()` accepts only the hardcoded literal
  `"DataExchangeGovernance:1.0"`, so it rejects the hub's canonical `"DataExchangeGovernance"`, even
  though the documented grammar makes the version optional and `evaluate()` already parses a bare
  subtype. `ConxFrameworkAgreementConstraintFunction` validates exactly what `evaluate()` accepts, in
  both the new and the legacy notation.
- **"At least one" really means "all"**: upstream `UsagePolicyValidator` requires a `CONTRACT`
  permission to carry *both* `FrameworkAgreement` and `UsagePurpose`, even though its failure message
  says "at least". `ConxUsagePolicyValidator` implements real "at least one of" semantics.
- **Constraint-free `CONTRACT` policies**: upstream rejects the `odrl:use` counterpart of a
  constraint-free `ACCESS` policy for two reasons. `AtLeastOneRuleExists` requires a `constraint` key,
  which the hub omits (`@JsonInclude(NON_NULL)`), and the required-constraints check also applies to
  permissions that have no constraints at all. `ConxAtLeastOneRuleExists` and `ConxUsagePolicyValidator`
  relax both. The required-constraints check still applies as soon as *any* constraint is present.
- **`odrl:or`**: upstream only allows `odrl:and` as logical constraint. Construct-X also allows `odrl:or`
  (nesting, e.g. an `and` inside an `or`, works as well). `odrl:xone` and `odrl:andSequence` stay rejected.
  The EDC policy engine evaluates `or` natively. Two things to keep in mind:
  - The required-constraints and mutual-exclusion checks still collect left operands across all branches.
    So `or(DataUsageEndDate, DataUsageEndDurationDays)` is still rejected as mutually exclusive, and a
    `FrameworkAgreement` in any branch satisfies the "at least one of" requirement.
  - Before evaluation, EDC drops constraints whose left operand is not bound to the current scope. A branch
    can therefore disappear: `DataUsageStartDate`/`DataUsageEndDate` are not bound to the `catalog` and
    `contract.negotiation` scopes, which are the only scopes that evaluate an access policy. In an access
    policy, an `or` branch that consists only of date constraints never grants access on its own.

`ConxPolicyValidationConstants` holds the Construct-X allowed left-operand sets. The remaining
`ConxPolicyDefinitionValidator` chain (`ConxAccessPolicyValidator`, `ConxConstraintValidator`,
`ConxLogicalConstraintValidator`, `ConxAtomicConstraintValidator`, `ConxLeftOperandValidator`) consists
of copies of the upstream classes. They have to be copied because upstream wires them through private
nested classes and static imports, so they cannot be extended.

Note that the upstream `UsagePurposeConstraintFunction` is a placeholder: `evaluate()` always returns
`true`, so its value-list check applies only at policy *creation* time.
