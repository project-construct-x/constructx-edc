# Construct-X Policy Hub Client

Lets the **provider control plane** pull ODRL policies from the
[Construct-X Policy Hub](https://github.com/project-construct-x/policy-hub) and store them as local
`PolicyDefinition`s, so they can be referenced as `accessPolicyId` / `contractPolicyId` in contract
definitions.

Policies are **never hand-written on the connector**: the Policy Hub is the single source of truth,
the connector only imports from it.

## Endpoints

### Import a policy

```
POST {management}/v1alpha/policyhub/policies/{hubPolicyId}/import
```

Registered on the Management API context, so it uses the same token auth as every other management
request (`x-api-key`). Only registered if `constructx.policyhub.url` is set — consumer-side
deployments of the same distribution boot without the endpoint.

**Request** — no body. `hubPolicyId` is the **UUID** of the policy in the Policy Hub.

```
POST http://localhost:39010/management/v1alpha/policyhub/policies/2ae42981-b9a1-4eba-b7f1-8df23a7eb70b/import
x-api-key: prov-management-api-key
```

**What it does** — the control plane fetches `GET {hub}/api/v1/policies/{hubPolicyId}/odrl` itself
(basic auth resolved from the vault per request), then runs the response through the *exact* same
JSON-LD expansion, validation and transformation steps as `POST /v3/policydefinitions`, stamps the
participant context, and stores the definition **under the Policy Hub's UUID** — not under the
hub's human-readable policy id, which the hub's ODRL rendering uses as `@id`. Repeating the import
updates the existing definition, so it is idempotent.

**Response** — `200 OK`, a standard `IdResponse` plus a `policyHubName` field carrying the hub's
human-readable id, purely so it stays easy to tell which hub policy a connector-side UUID belongs
to:

```json
{
  "@type": "IdResponse",
  "@id": "2ae42981-b9a1-4eba-b7f1-8df23a7eb70b",
  "createdAt": 1759300000000,
  "policyHubName": "meine-policy-date",
  "@context": { "@vocab": "https://w3id.org/edc/v0.0.1/ns/" }
}
```

`@id` is the id to use in contract definitions. Errors come back as `400` with the reason in the
message — including a hub-side `404`, which surfaces as `400 Could not fetch policy '…' from the
Policy Hub: Policy Hub returned status 404`.

### Read imported policies back

Both endpoints are plain upstream Management API — no extension code involved. They read the
connector's own database, never the Policy Hub.

**A single policy**

```
GET {management}/v3/policydefinitions/{id}
```

`{id}` is the UUID returned above. The hub's human-readable name is also persisted on the definition
itself, as `privateProperties.policyHubName`, so it remains visible here.

**All policies stored in the connector**

```
POST {management}/v3/policydefinitions/request
x-api-key: prov-management-api-key
```

Returns every policy definition in the connector — imported from the Policy Hub as well as any
created by hand via `POST /v3/policydefinitions`. The body is a standard `QuerySpec`; an empty
`{}` lists everything, `offset` / `limit` paginate, and `filterExpression` narrows the result:

```json
{
  "@context": { "@vocab": "https://w3id.org/edc/v0.0.1/ns/" },
  "@type": "QuerySpec",
  "offset": 0,
  "limit": 50
}
```

Imported policies can be told apart by `privateProperties.policyHubName`.

## How an import runs

Walkthrough along the Bruno collection in
[`local/bruno/con-x-local-test/transactions/provider`](../../edc-controlplane/edc-controlplane-construct-x/local/bruno/con-x-local-test/transactions/provider).
Step 2 offers **two alternative ways** to get a policy onto the connector — run **either 2A or 2B**,
not both: 2A creates a policy by hand through the standard Management API, 2B imports it from the
Policy Hub.

```
 Bruno: transactions/provider              Provider control plane           Policy Hub (:8080)
 ────────────────────────────              ──────────────────────           ──────────────────

 1  CreateAsset
    POST /v3/assets ──────────────────▶  asset "assetId"


 2  Create a policy — choose ONE of the two options:

 ┌─ 2A  CreatePolicy  (manual, standard Management API) ─────────────────────────────────────┐
 │  POST /v3/policydefinitions        Hand-writes a policy ("@id": "aPolicy") with empty    │
 │  {"@id": "aPolicy", ...}           permission/prohibition/obligation arrays. Works with  │
 │                                    no Policy Hub at all (e.g. a testbed without one),    │
 │                                    but nothing is enforced and no hub policy is          │
 │                                    exercised. Policy id to use below: "aPolicy".         │
 └───────────────────────────────────────────────────────────────────────────────────────────┘

 ┌─ 2B  ImportPolicyFromHub  (from the Policy Hub) ──────────────────────────────────────────┐
 │  POST /v1alpha/policyhub/                                                                 │
 │       policies/{{HUB_POLICY_ID}}                                                          │
 │       /import ───────────────────▶  resolve basic auth                                    │
 │                                     from Vault                                            │
 │                                       │                                                   │
 │                                     fetch ODRL ─────────────▶ GET /api/v1/policies/       │
 │                                                                   {{HUB_POLICY_ID}}/odrl  │
 │                                                ◀───────────── 200 JSON-LD                 │
 │                                     expand → validate →                                   │
 │                                     transform → create (or                                │
 │                                     update, if it exists)                                 │
 │                                       │                                                   │
 │                                     PolicyDefinition                                      │
 │                                       id = {{HUB_POLICY_ID}}                              │
 │                          ◀──────────  200 {"@id": …, "policyHubName": …}                  │
 │  post-response script sets:                                                               │
 │    importedPolicyId   ← @id                                                               │
 │    importedPolicyName ← policyHubName                                                     │
 └───────────────────────────────────────────────────────────────────────────────────────────┘


 3  Check Imported Policy  (or List All Policies)
    GET /v3/policydefinitions/
        {{importedPolicyId}} ────────────▶  stored definition; for 2B incl.
                              ◀──────────   privateProperties.policyHubName


 4  CreateContractDefinition
    POST /v3/contractdefinitions
      accessPolicyId   = {{importedPolicyId}} ──▶  contract definition "1"
      contractPolicyId = {{importedPolicyId}}      → catalog serves "assetId" under the
                                                     chosen policy
                                                     (2A: "aPolicy", 2B: the hub UUID)
```

Which policy ends up in the contract definition is decided by what `accessPolicyId` /
`contractPolicyId` point at. After **2B** that is `importedPolicyId`, set by the import's
post-response script; after **2A** set both to `"aPolicy"` instead.

Switching to a different hub policy means changing `HUB_POLICY_ID` and re-running 2B — its
post-response script repoints `importedPolicyId`, which step 4 picks up. A stale or unset
`importedPolicyId` (or a policy id that was never created) makes the contract definition reference a
non-existent policy: no error, the dataset just silently drops out of the catalog.

## Configuration

| Setting                                  | Required | Default                  | Description                                                                                        |
|------------------------------------------|----------|--------------------------|----------------------------------------------------------------------------------------------------|
| `constructx.policyhub.url`               | no       | –                        | Base URL of the Policy Hub. **If unset, the import API is not registered** (consumer deployments). |
| `constructx.policyhub.credentials.alias` | no       | `policy-hub-credentials` | Vault alias holding the hub's basic-auth credentials in `user:password` format.                    |

Credentials are resolved from the vault on every request — they never appear in configuration files,
and rotating them needs no restart.

## Runtime prerequisite

The Catena-X policy functions must be on the classpath, otherwise `edc.policy.validation.enabled`
(default `true`) rejects hub policies at creation time because their left operands are unknown to
the policy engine. Both Construct-X control plane distributions
(`con-x-controlplane-postgresql-hashicorp-vault`, `con-x-controlplane-postgresql-vault`) therefore
depend on the **local** `edc-extensions:cx-policy`, `:bpn-validation` and `:bdrs-client` project
modules, not the `org.eclipse.tractusx.edc:*` Maven artifacts. This is required, not stylistic:
`cx-policy` carries Construct-X-local patches (below) that exist only in this repository's source.
There is no Gradle dependency substitution here, so pointing a distribution at the Maven coordinate
would silently build against the unpatched upstream code.

## Construct-X-local patches to `cx-policy`

Making the hub's own unmodified ODRL output importable required changes to the vendored
`edc-extensions/cx-policy` — a deliberate, one-off exception to this repository's "no Java connector
code" principle. Each class carries its own comments with the exact diff against upstream 0.12.0:

- **`DataUsageStartDate` / `DataUsageEndDate` (`DATE_RANGE`)** — added `DataUsageStartDate` (did not
  exist upstream at all), allowed both on `ACCESS` policies (were `CONTRACT`-only), widened the
  accepted operators to `gteq`/`lteq` (were `EQ`-only), and accepted bare ISO dates like
  `"2026-08-01"` in addition to full date-times (start → 00:00:00 UTC, end → 23:59:59 UTC).
- **`UsagePurpose` catalog NPE** — `UsagePurpose` was bound to `CATALOG_SCOPE` (pre-existing,
  `CONTRACT`-only) but had **no** registered function for `CatalogPolicyContext`, so building a
  catalog for *any* offer carrying a `UsagePurpose` constraint threw an NPE and took down catalog
  generation for the whole contract definition (502 on `POST /v3/catalog/request`). Fixed by
  registering the function for that context too. `UsagePurpose` itself (left operand, right-operand
  values) is otherwise untouched from upstream: it remains `CONTRACT`-only, and only the
  standardized Catena-X identifiers are accepted.
- **`FrameworkAgreement` without a version** — `validate()` accepted only the hardcoded literal
  `"DataExchangeGovernance:1.0"`, rejecting the hub's canonical `"DataExchangeGovernance"`, even
  though the documented grammar makes the version optional and `evaluate()` already parsed a bare
  subtype correctly. `validate()` now reuses that same parsing.
- **`UsagePolicyValidator`: "at least one" actually meant "all"** — a `CONTRACT` permission had to
  carry *both* `FrameworkAgreement` and `UsagePurpose`, despite the failure message reading "at
  least". Inverted to genuine "at least one of" semantics.
- **Constraint-free `CONTRACT` policies** — the `odrl:use` counterpart of a constraint-free `ACCESS`
  policy was rejected twice over: `AtLeastOneRuleExists` demanded a `constraint` key be present
  (contradicting its own documented purpose, and a shape the hub really produces, since
  `@JsonInclude(NON_NULL)` omits it), and the required-constraints check applied even to a
  permission with no constraints to anchor them to. Both relaxed; the check still applies unchanged
  the moment *any* constraint is present.

Note that `UsagePurposeConstraintFunction` is a placeholder (see its class Javadoc): `evaluate()`
always returns `true`, so the value-list check above only applies at policy *creation* time, never
during negotiation or transfer.
