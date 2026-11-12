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
Policy Hub: Policy Hub returned status 404`. If the hub itself is unreachable (connection failure,
timeout, `5xx`, `408`, `429`), the response is `503 Service Unavailable` instead; if it rejects the
connector's credentials (`401`/`403`), the response is `502 Bad Gateway`.

Every failed import is also logged as a warning in the control plane, so it does not only reach
the HTTP client: `Policy Hub import of '<id>' failed: <reason>`. For a policy the validator rejects,
the reason names each violation with its place in the policy and the offending value, e.g.
`Policy '<id>' is not a valid policy definition: leftOperand.@id value 'https://example.com/Unknown' is not allowed. Expected one of: […] (at '@id')`.
The periodic sync logs the same reason per policy (see [Keeping imports up to date](#keeping-imports-up-to-date)).

If the hub's content is unchanged since the last import, nothing is written and the existing
definition is returned (see [Keeping imports up to date](#keeping-imports-up-to-date)).

### Sync all imported policies now

```
POST {management}/v1alpha/policyhub/policies/sync
```

Runs the [periodic sync](#keeping-imports-up-to-date) once, right away, and synchronously. No body.
Useful for operations, tests and the testbed, where waiting for the next scheduled run is
impractical.

```json
{
  "@type": "PolicyHubSyncSummary",
  "checked": 3,
  "updated": 1,
  "unchanged": 1,
  "failed": 1,
  "@context": { "@vocab": "https://w3id.org/edc/v0.0.1/ns/" }
}
```

If the Policy Hub is unreachable, the manual run does **not** wait for it (it blocks an HTTP
request): it aborts at once and answers `503 Service Unavailable`, e.g.
`Policy Hub sync aborted: Policy Hub unreachable (Connection refused), 0 policies checked before`.
If the hub rejects the credentials (`401`/`403`), it answers `502 Bad Gateway` instead.

### Read imported policies back

Both endpoints are plain upstream Management API — no extension code involved. They read the
connector's own database, never the Policy Hub.

**A single policy**

```
GET {management}/v4/policydefinitions/{id}
```

`{id}` is the UUID returned above. The hub's human-readable name is also persisted on the definition
itself, as `privateProperties.policyHubName`, so it remains visible here. `/v3/policydefinitions/{id}` returns
the same definition in the v3 JSON-LD format.

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

Multi-value right operands (e.g. the `DataSubset` or `UsagePurpose` value lists) are not returned as a JSON array
here — see the [known limitation](../con-x-policy/README.md#known-limitation-multi-value-right-operands).

## How an import runs

The testbed uses two Bruno collections in
[`local/bruno`](../../edc-controlplane/edc-controlplane-construct-x/local/bruno):

- [`con-x-local-test`](../../edc-controlplane/edc-controlplane-construct-x/local/bruno/con-x-local-test) — the
  general testbed collection: identities, assets, contract definitions, catalog, negotiation and transfer.
- [`con-x-policy-hub-import`](../../edc-controlplane/edc-controlplane-construct-x/local/bruno/con-x-policy-hub-import) —
  only the Policy Hub requests described in this README (`ImportPolicyFromHub`, `Check Imported Policy`,
  `List All Policies`, `SyncPoliciesFromHub`). Its environment needs just `PROVIDER_MANAGEMENT` and
  `HUB_POLICY_ID`, the UUID of the hub policy to import.

```
 Bruno collection                                   Provider control plane          Policy Hub (:8080)
 ────────────────                                   ──────────────────────          ──────────────────

 1  con-x-local-test / identities  (once, top to bottom)
    onboards issuer, provider and consumer and puts their STS secrets into the vault


 2  con-x-local-test / transactions/provider/createAssetOne / CreateAsset
    POST /v3/assets ──────────────────────────────▶  asset "demoAssetIdOne"


 3  con-x-policy-hub-import / ImportPolicyFromHub
    POST /v1alpha/policyhub/policies/
         {{HUB_POLICY_ID}}/import ────────────────▶  resolve basic auth
                                                      from Vault
                                                        │
                                                      fetch ODRL ───────────────▶  GET /api/v1/policies/
                                                                                       {{HUB_POLICY_ID}}/odrl
                                                                   ◀───────────────  200 JSON-LD
                                                      expand → validate →
                                                      transform → create (or
                                                      update, if it exists)
                                                        │
                                                      PolicyDefinition
                                                        id = {{HUB_POLICY_ID}}
                                  ◀──────────────────  200 {"@id": …, "policyHubName": …}


 4  con-x-policy-hub-import / Check Imported Policy  (or List All Policies)
    GET /v4/policydefinitions/{{HUB_POLICY_ID}} ──▶  stored definition, incl.
                                  ◀──────────────────  privateProperties.policyHubName


 5  con-x-local-test / transactions/provider/createAssetOne / CreateContractDefinition
    POST /v3/contractdefinitions
      accessPolicyId   = <HUB_POLICY_ID> ──────────▶  contract definition
      contractPolicyId = <HUB_POLICY_ID>              → catalog serves "demoAssetIdOne"
                                                        under the hub policy


 6  con-x-local-test / transactions/consumer
    RequestProviderCatalog → InitiateNegotiation → CheckNegotiationResult →
    InitPullTransfer → Get EDR → PullAssetData
```

The import stores the policy under the Policy Hub's UUID, so `{{HUB_POLICY_ID}}` is also the id to use in
contract definitions. Step 5 is a manual edit: the shipped `CreateContractDefinition` request in
`con-x-local-test` points at `accessPolicyOne` / `contractPolicyOne`, so replace both ids with the value of
`HUB_POLICY_ID` before you run it.

The collections do not share variables. The `importedPolicyId` and `importedPolicyName` that
`ImportPolicyFromHub` sets in its post-response script only exist in the Bruno session of
`con-x-policy-hub-import` and are there for readability; no other request depends on them.

Switching to a different hub policy means changing `HUB_POLICY_ID`, re-running the import, and updating the two
policy ids in the contract definition. A policy id that was never created makes the contract definition reference a
non-existent policy: no error, the dataset just silently drops out of the catalog.

Without a Policy Hub, create policies by hand with the `CreateAccessPolicy` and `CreateContractPolicy` requests next
to `CreateAsset` in `con-x-local-test` and leave the contract definition as it is.

## Keeping imports up to date

An import is a snapshot: if the policy is changed in the Policy Hub afterwards, the connector would
keep offering the old version. The control plane therefore re-imports every policy it imported from
the hub periodically (every hour by default). The hub stays the single source of truth, so changes
are taken over automatically.

- **Scope** — only the connector's own imports are synced (definitions carrying
  `privateProperties.policyHubName`), one `GET …/odrl` request each. The cost depends on how many
  policies the connector uses, not on how many the hub holds.
- **Change detection** — every import stores a SHA-256 hash of the hub's ODRL rendering (keys
  sorted, so reordering does not count as a change) as `privateProperties.policyHubHash`. An
  unchanged policy is not written at all. `privateProperties.policyHubImportedAt` (epoch millis)
  records when the content was last taken over from the hub.
- **Nothing is ever deleted** — if the hub no longer serves a policy (`404`), is unreachable, or the
  new version fails validation, the stored definition stays as it is and a warning is logged.
  Contract definitions may still reference it.
- **Scheduling** — the next run is scheduled when the previous one has ended, so runs never
  overlap. The first run starts after a random delay of up to one interval, so that many connectors
  do not hit the hub at the same moment. Requests within a run are spaced by `request-delay`.
- **Hub unreachable** — a connection failure or timeout, or a `5xx`, `408` or `429` response,
  affects every policy alike, so it is handled for the run as a whole rather than per policy:
  1. The run waits and retries the same policy with a doubling pause (`5 s`, `10 s`, `20 s`, …),
     the last pause cut short so the pauses add up to at most `retry.max-wait` (`60 s`). If the hub
     comes back, the run simply continues.
  2. Otherwise the run is aborted; the remaining policies are not requested. The next run follows
     after `outage-interval` (`5 min`) instead of the regular interval, and again every 5 minutes
     until the hub is back. The first run that completes switches back to the regular interval.

  Requests to the hub use the runtime's plain `OkHttpClient` (same timeouts as the rest of the
  connector) but **not** EDC's built-in HTTP retries (`edc.core.retry.*`), which would retry every
  request silently for about 15 s. Each request is therefore a single attempt: the waiting happens
  only in the backoff above, and every attempt is logged. The import and sync endpoints answer
  `503` right away.
- **Hub rejects the credentials** — a `401` or `403` answer also affects every policy alike, but
  waiting cannot fix it: the secret in the vault has to be corrected. The run therefore aborts at
  once, on the first request, **without** waiting or retrying, and logs that the credentials were
  rejected. The next run follows after `outage-interval` like after an outage, so it picks up the
  corrected secret within minutes and without a restart. The import and sync endpoints answer
  `502 Bad Gateway` (the problem lies between the connector and the hub, not with the caller).
- **Single policy rejected** — a `404` or `400` concerns one policy only, is counted as `failed`
  and does not stop the run.
- **Multiple replicas** — each control plane replica syncs on its own. That is harmless because
  imports are idempotent and the hash prevents duplicate writes.
- **Existing contracts are not affected** — EDC copies the policy into the `ContractAgreement` when
  a negotiation completes. A sync only changes what new catalog requests and negotiations see.

Every run ends with a log line such as
`Policy Hub sync (scheduled) finished: 3 checked, 1 updated, 1 unchanged, 1 failed` (`(manual)` for
runs started through the sync endpoint). Each policy whose content changed additionally logs
`Updated policy definition '…' from the Policy Hub` once it is stored, and each failure a warning with the
reason.

While the hub is unreachable or rejects the credentials, the log shows:

| Situation                        | Level   | Log line                                                                                                                    |
|----------------------------------|---------|-----------------------------------------------------------------------------------------------------------------------------|
| Waiting for the hub              | WARNING | `Policy Hub sync (scheduled): Policy Hub unreachable (Connection refused), retry 1 in 5 s`                                  |
| Hub back within the same run     | INFO    | `Policy Hub sync (scheduled): Policy Hub reachable again after 2 retries`                                                   |
| Run aborted                      | WARNING | `Policy Hub sync (scheduled) aborted: Policy Hub unreachable for 60 s (Connection refused), 0 of 12 policies checked`       |
| Credentials rejected (401/403)   | WARNING | `Policy Hub sync (scheduled) aborted: Policy Hub rejected the credentials (status 401), 0 of 12 policies checked; check the Policy Hub credentials in the vault` |
| Next run after an abort          | WARNING | `Next Policy Hub sync in 300 s (3 aborted runs in a row since 2026-11-05T10:15:00Z, last cause: unreachable (Connection refused))` — or `last cause: access denied (status 401)` |
| First completed run after abort  | INFO    | `Policy Hub sync completed again after 3 aborted runs, back to the regular interval of 3600 s`                              |
| Manual sync while hub is down    | WARNING | `Policy Hub sync (manual) aborted: Policy Hub unreachable (Connection refused), 0 of 12 policies checked` (plus HTTP `503`) |
| Manual import/sync, credentials rejected | WARNING | `Policy Hub import of '<id>' failed: Policy Hub rejected the credentials (status 401); check the Policy Hub credentials in the vault` (plus HTTP `502`) |

To monitor the periodic sync, alert on `Policy Hub sync (scheduled) aborted` lines and when no
`Policy Hub sync (scheduled) finished` line appears for longer than about one interval; look at the
warnings when `failed` is not 0.

## Configuration

| Setting                                    | Required | Default                  | Description                                                                                                   |
|--------------------------------------------|----------|--------------------------|---------------------------------------------------------------------------------------------------------------|
| `constructx.policyhub.url`                 | no       | –                        | Base URL of the Policy Hub. **If unset, the import API is not registered and no sync runs** (consumer deployments). |
| `constructx.policyhub.credentials.alias`   | no       | `policy-hub-credentials` | Vault alias holding the hub's basic-auth credentials in `user:password` format.                               |
| `constructx.policyhub.sync.enabled`        | no       | `true`                   | Whether imported policies are periodically re-imported. The sync endpoint works either way.                   |
| `constructx.policyhub.sync.interval`       | no       | `3600`                   | Seconds between the end of one sync run and the start of the next.                                            |
| `constructx.policyhub.sync.initial-delay`  | no       | `-1`                     | Seconds before the first run. Negative means a random delay between 0 and the interval.                       |
| `constructx.policyhub.sync.request-delay`  | no       | `100`                    | Milliseconds between two Policy Hub requests within one run.                                                  |
| `constructx.policyhub.sync.retry.initial-delay` | no  | `5`                      | Hub unreachable: seconds until the first retry within a scheduled run, doubled on every further retry.        |
| `constructx.policyhub.sync.retry.max-wait` | no       | `60`                     | Hub unreachable: maximum total seconds a scheduled run waits for the hub before it is aborted.                |
| `constructx.policyhub.sync.outage-interval` | no      | `300`                    | Seconds until the next run after a run was aborted (hub unreachable or credentials rejected).                 |

Credentials are resolved from the vault on every request — they never appear in configuration files,
and rotating them needs no restart.

## Runtime prerequisite

The Catena-X policy functions must be on the classpath, otherwise `edc.policy.validation.enabled`
(default `true`) rejects hub policies at creation time because their left operands are unknown to
the policy engine. Both Construct-X control plane distributions
(`con-x-controlplane-postgresql-hashicorp-vault`, `con-x-controlplane-postgresql-vault`) therefore
depend on the local `edc-extensions:con-x-policy` module. They deliberately do not ship the Catena-X
BPN/BDRS extensions (`bpn-validation`, `bdrs-client`), so hub policies with `BusinessPartnerGroup` or
`BusinessPartnerNumber` are rejected on import.

`con-x-policy` builds on the **unmodified** Catena-X `edc-extensions:cx-policy` and adds the
Construct-X extensions and fixes that make the hub's own ODRL output importable (`DataUsageStartDate`,
`FrameworkAgreement` without a version, "at least one" semantics, constraint-free `CONTRACT` policies,
`UsagePurpose` catalog NPE, Participant selection via `connector`, `DataSubset`). Its `ConxPolicyExtension` replaces the upstream `CxPolicyExtension` in
both distributions. See [`edc-extensions/con-x-policy/README.md`](../con-x-policy/README.md) for the
complete list and the reasoning.
