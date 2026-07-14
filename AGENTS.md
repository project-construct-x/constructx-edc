# AGENTS.md

Construct-X fork of **Tractus-X EDC** (Eclipse Dataspace Connector). Multi-module
Gradle (Kotlin DSL) Java project that builds control-/data-plane Docker images and
Helm charts. Remote: `project-construct-x/constructx-edc`; upstream:
`eclipse-tractusx/tractusx-edc`.

## Upstream stack (3 layers — know which layer you're touching)
1. **eclipse-edc/Connector** (`org.eclipse.edc:*`) — the core framework: SPI, core services (`TransferProcessManager`, policy engine, DSP protocol engine), control plane and data plane. Released as Maven BOMs.
2. **eclipse-tractusx/tractusx-edc** (`org.eclipse.tractusx.edc:*`) — Catena-X/automotive extensions built on the EDC framework. **This repo is a fork of it**; the bulk of the tree mirrors upstream.
3. **Construct-X additions** — keep changes minimal and upstream-aligned. The only Construct-X-specific code is a handful of extensions plus the runtime distributions under `edc-controlplane/edc-controlplane-construct-x/` and `edc-dataplane/edc-dataplane-construct-x/`.

## Construct-X runtimes (review these first for con-x work)
The `con-x-controlplane-postgresql-hashicorp-vault` and `con-x-dataplane-postgresql-hashicorp-vault`
modules do **not** compile sibling source. They are thin `shadowJar` aggregations of **pre-built,
published** upstream artifacts, assembled at runtime via EDC's ServiceLoader autoload (`mergeServiceFiles()`):
- Dependencies are upstream BOMs/artifacts: `org.eclipse.edc:*` and `org.eclipse.tractusx.edc:*` (e.g. `controlplane-dcp-bom`, `controlplane-feature-sql-bom`, `vault-hashicorp`, `tx-dcp`, `agreements`).
- **Versions are pinned inline** in each module's `build.gradle.kts` (`val edcVersion` / `val txVersion`), deviating from the repo-wide version-catalog rule. To bump, edit those local vals in both runtime build files.
- Entrypoint: `mainClass = org.eclipse.edc.boot.system.runtime.BaseRuntime`.
- Dataplane intentionally `exclude`s `org.eclipse.edc:data-plane-util` in favor of the tractusx `dataplane-util`.
- To add a feature to a con-x runtime, add the BOM/artifact dependency here — don't wire code — unless authoring a genuinely new extension (then add it as a module and depend on it).
- Build a single runtime image, e.g.: `./gradlew :edc-controlplane:edc-controlplane-construct-x:con-x-controlplane-postgresql-hashicorp-vault:dockerize` (note: that module's README has a typo, `postgres` vs `postgresql`).

## Build & toolchain
- **Java 21** (Temurin in CI). Always use the wrapper: `./gradlew`.
- `version` and group live in root `gradle.properties` only — never add per-module `gradle.properties` (see `docs/development/coding-principles.md`).
- External dep versions come from version catalogs (`gradle/libs.*.versions.toml`), never inline versions.
- Build images: `./gradlew dockerize` (a `dockerize` task is auto-added to every module that applies `shadowJar`).
- The `edc` git submodule (`.gitmodules`) is the upstream EDC source; not needed for normal builds.

## Module layout
Modules are declared in `settings.gradle.kts` (~105 of them). Key groups:
- `spi/` — extensibility interfaces. `core/` — shared tractusx-edc modules.
- `edc-extensions/` — feature extensions (bpn-validation, dcp, edr, migrations, dataplane, etc.).
- `edc-controlplane/`, `edc-dataplane/` — runtime distributions assembled from `*-base`.
- `edc-tests/` — e2e/component/api test suites and their dedicated runtimes.
- **Construct-X-specific** distributions live under `edc-*/edc-*-construct-x/` (see section above). Do not confuse the `con-x-*` modules with the upstream `edc-controlplane-postgresql-hashicorp-vault` distribution alongside them.
- Module reference rules (enforced by convention): SPI→SPI/lib only; extension→SPI/lib only; lib→lib only.

## Verification (run before pushing; matches `.github/workflows/verify.yaml`)
- Formatting: `./gradlew checkstyleMain checkstyleTest` (Checkstyle = Google style + extra rules, config in `resources/tx-checkstyle-config.xml`; violations are warnings locally but gate PRs).
- Javadoc: `./gradlew javadoc`.
- Unit tests: `./gradlew test testCodeCoverage`.
- Tests are tag-filtered via JUnit; select with `-DincludeTags="..."`:
  - `ComponentTest` (needs `./gradlew :edc-tests:runtime:mock-connector:dockerize` first)
  - `ApiTest`, `EndToEndTest`, `PostgresqlIntegrationTest`, `CompatibilityTest`
- Run one suite: `./gradlew -p edc-tests/e2e/<dir> test -DincludeTags="EndToEndTest" -PverboseTest=true --no-build-cache`.
- Run one module's tests: `./gradlew :path:to:module:test`.
- `edc-tests/*` use Allure; results aggregate via the root `aggregateAllureResults` task.

## Coding conventions (from `docs/development/coding-principles.md`)
- Use `var`; `final` only on fields, not locals/params. No checked exceptions — throw subclasses of `org.eclipse.edc.spi.EdcException` only when fatal; otherwise return `Result`.
- Validate/load config at extension init (fail-fast); annotate config keys with `@Setting`. Inject deps via constructor for testability.
- Builder pattern for data objects (>3 args or optional args); builder named `Builder`, `newInstance()` factory, private object ctor.
- Single impl of an interface → `<Interface>Impl`. Secrets only in `Vault`, never logged. JSON ser/des only via `TypeManager`.
- Test naming: `method_whenCondition_shouldExpectation`. Prefer unit > component/integration > e2e.

## Local end-to-end testbed
`edc-controlplane/edc-controlplane-construct-x/local/` has a `docker-compose.yaml`
(two connectors + wallets + shared Postgres/Vault) plus a Bruno HTTP collection.
Requires locally built `con-x-*` images and a `docker login ghcr.io` with a
`read:packages` token from a `project-construct-x` org member. Reset with
`docker compose down -v`.

## Conventions
- Apache-2.0 license headers required on source files (Checkstyle covers `.github` too).
