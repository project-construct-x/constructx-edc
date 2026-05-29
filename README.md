# Construct-X EDC

[![Apache 2.0 License][license-shield]][license-url]

[Construct-X](https://www.construct-x.org/) specific Eclipse Dataspace Connector distributions and extensions, based on the [Eclipse Tractus-X EDC](https://github.com/eclipse-tractusx/tractusx-edc).

## Overview

The Construct-X EDC extends the Eclipse Tractus-X EDC with
Construct-X specific policies, extensions and configurations.

This repository provides:
- Construct-X specific EDC extensions
- Control-Plane and Data-Plane distributions
- Helm charts and docker compose
- Local development and testing environments

## Inventory

The Construct-X EDC is split into a Control Plane and a Data Plane.

The Control Plane is responsible for resource management,
contract negotiation and transfer orchestration.

The Data Plane handles the actual transfer of data streams.

### Control Plane distributions

- [edc-controlplane-postgresql-hashicorp-vault](edc-controlplane/edc-controlplane-construct-x/con-x-controlplane-postgresql-hashicorp-vault) with
  dependencies on
  - [Hashicorp Vault](https://www.vaultproject.io/)
  - [PostgreSQL 8.2 or newer](https://www.postgresql.org/)
- [edc-runtime-memory](edc-controlplane/edc-runtime-memory)

### Data Plane distributions

- [edc-dataplane-hashicorp-vault](edc-dataplane/edc-dataplane-construct-x/con-x-dataplane-postgresql-hashicorp-vault) with dependencies on
  - [Hashicorp Vault](https://www.vaultproject.io/)

## Upstream References

- [Eclipse Tractus-X EDC](https://github.com/eclipse-tractusx/tractusx-edc)
- [Eclipse Dataspace Components](https://github.com/eclipse-edc/Connector)

## Construct-X Extensions

Construct-X adds custom integrations and runtime extensions
on top of the Eclipse Tractus-X EDC.

This includes:
- Construct-X specific policy extensions
- Custom credential handling
- Wallet integration support
- Local testbed environments

## Getting Started

The local testbed provides a lightweight environment for local Construct-X EDC development and integration testing.
It is the recommended starting point for developers getting started with the Construct-X EDC.

- [Construct-X Local Testbed](https://github.com/project-construct-x/constructx-edc/blob/develop/edc-controlplane/edc-controlplane-construct-x/local/README.md)

## Contributing
See [CONTRIBUTING](https://github.com/project-construct-x/constructx-edc/blob/develop/CONTRIBUTING.md).

## License

Distributed under the Apache 2.0 License.
See [LICENSE](https://github.com/project-construct-x/constructx-edc/blob/develop/LICENSE) for more information.

<!-- MARKDOWN LINKS & IMAGES -->
<!-- https://www.markdownguide.org/basic-syntax/#reference-style-links -->

[license-shield]: https://img.shields.io/github/license/project-construct-x/constructx-edc.svg?style=for-the-badge
[license-url]: https://github.com/project-construct-x/constructx-edc/blob/develop/LICENSE