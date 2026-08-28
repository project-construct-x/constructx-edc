# Construct-X EDC Connector – Überblick

> Diese Dokumentation beschreibt, **was** der Construct-X Connector ist, **wie** er
> aufgebaut ist, **welche Extensions** er verwendet, **welche Version** aktuell ist
> und **wie** er zur Laufzeit aussieht. Sie richtet sich sowohl an Einsteiger als
> auch an Entwickler:innen.

---

## 1. Was ist der Construct-X Connector?

Der **Construct-X EDC** ist eine eigene Distribution des
[Eclipse Tractus-X EDC](https://github.com/eclipse-tractusx/tractusx-edc), der
wiederum auf den [Eclipse Dataspace Components (EDC)](https://github.com/eclipse-edc/Connector)
aufsetzt.

Ein **EDC-Connector** ist die technische Komponente, mit der ein Teilnehmer an
einem **Datenraum (Dataspace)** teilnimmt. Er ermöglicht den **souveränen,
richtlinienbasierten Austausch von Daten** zwischen Organisationen: Daten werden
nicht einfach kopiert, sondern nur nach erfolgreicher **Vertragsverhandlung**
(Contract Negotiation) und unter Durchsetzung von **Nutzungsrichtlinien**
(ODRL Policies) übertragen.

Der Fokus von Construct-X liegt dabei **nicht** auf eigener Connector-Logik,
sondern auf einer **eigenen Distribution**, der **Integration in den
Construct-X-Datenraum** (Wallet/Identität) und dem zugehörigen **Betrieb**
(Docker-Testbed, Bruno, Helm). Die eigentlichen Datenraum-Funktionen stammen aus
dem Upstream:

- **BPN-Policy-Durchsetzung** – Zugriffskontrolle anhand der Business Partner Number *(Tractus-X)*
- **DCP-Credential-Handling** – Decentralized Claims Protocol für verifizierbare Nachweise, z. B. Membership Credential *(Tractus-X / EDC)*
- **EDR-Token-Lebenszyklus** – Endpoint Data References inkl. automatischem Token-Refresh *(Tractus-X)*
- **Construct-X-Wallet-Integration** – Anbindung an Identity Hub / DID / STS / Issuer *(Construct-X-Konfiguration)*

> Eine genaue Trennung, was von Tractus-X stammt und was Construct-X ergänzt,
> findet sich in [Abschnitt 2](#2-abgrenzung-tractus-x-vs-construct-x).

### Was macht der Connector konkret?

| Rolle                         | Aufgabe                                                                                                             |
|-------------------------------|---------------------------------------------------------------------------------------------------------------------|
| **Provider** (Datenanbieter)  | Stellt Assets bereit, definiert Zugriffs-/Nutzungs-Policies und Contract Definitions, veröffentlicht einen Katalog. |
| **Consumer** (Datenkonsument) | Fragt den Katalog ab, verhandelt Verträge, initiiert Transfers und ruft Daten über eine EDR ab.                     |

Beide Rollen werden von **derselben Connector-Software** bereitgestellt – ob ein
Connector als Provider oder Consumer agiert, hängt nur von der konkreten
Interaktion ab.

---

## 2. Abgrenzung: Tractus-X vs. Construct-X

Da dieses Repository ein **Fork von Tractus-X EDC** ist, lohnt sich eine klare
Trennung. Der **gesamte funktionale Connector** (Logik, Extensions, Protokolle)
stammt aus dem Upstream. Construct-X hat **keinen eigenen Java-Connector-Code**
beigetragen, sondern eine **Distributions-, Integrations- und Betriebsschicht**
darübergelegt.

> **In einem Satz:** *Tractus-X liefert den kompletten Connector (Logik,
> Extensions, Protokolle); Construct-X liefert eine eigene Distribution +
> Wallet-Integration + Betriebs-/Deployment-Konfiguration (Docker-Testbed, Bruno,
> Helm) – ohne eigenen Java-Connector-Code.*

**Belegt durch die Git-Historie:** Der erste Construct-X-Commit
(`538c0453`, "feat/con-x-edc-initial", 2026-02-06) fügte 54 Dateien hinzu –
**keine davon Java-Quellcode**, sondern ausschließlich Distributions-Assembly,
Docker-Testbed, Bruno-Requests und Gradle-Registrierung. Der letzte Abgleich mit
dem Upstream erfolgte am 2026-03-23 (Merge auf EDC `0.15.1` / TX `0.12.0`).

### Von Tractus-X geerbt (unverändert übernommen)

| Bereich                     | Inhalt                                                                                                                                                                                                                                                                                              |
|-----------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **SPI / Core / Extensions** | Gesamter Java-Code: `bpn-validation`, `cx-policy`, `cx-policy-legacy`, `dcp` (cx-dcp / tx-dcp / tx-dcp-sts-div / vp-cache), `edr`, `agreements`, `agreements-bpns`, `bdrs-client`, `connector-discovery`, `dataplane`, `tokenrefresh-handler`, `did-document`, `migrations`, `dataspace-protocol` … |
| **Standard-Distributionen** | `edc-controlplane-base`, `edc-controlplane-postgresql-hashicorp-vault`, `edc-dataplane-hashicorp-vault`, `edc-runtime-memory`                                                                                                                                                                       |
| **Protokolle & Features**   | DSP, DCP, ODRL-Policies, EDR-Lebenszyklus, BPN-Validierung, Vault-/SQL-Anbindung                                                                                                                                                                                                                    |
| **Tests**                   | gesamte E2E-, Component- und Compatibility-Suiten                                                                                                                                                                                                                                                   |
| **Helm-Charts (Basis)**     | Grundstruktur von `tractusx-connector` und `tractusx-connector-memory`                                                                                                                                                                                                                              |
| **Build & Standards**       | Gradle, Version-Katalog, Checkstyle, Coding-Prinzipien                                                                                                                                                                                                                                              |

### Von Construct-X ergänzt (die eigene Schicht darüber)

| # | Ergänzung                                                                                                                        | Ort / Nachweis                                                                                              |
|---|----------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| 1 | **Neue Distributions-Module** (schlanke Assemblies, die EDC 0.15.1 + TX 0.12.0 pinnen und `con-x`-benannte JARs/Images erzeugen) | `edc-controlplane/edc-controlplane-construct-x/…`, `edc-dataplane/edc-dataplane-construct-x/…`              |
| 2 | **Lokales Docker-Testbed** (zwei Stacks gegeneinander)                                                                           | `.../local/docker-compose.yaml`, `additional_config/` (vault-init, pg_init, Key-Gen)                        |
| 3 | **Bruno-HTTP-Collection** für Identitäten & Transaktionen                                                                        | `.../local/bruno/con-x-local-test/`                                                                         |
| 4 | **Integration der Construct-X-Wallet** (statt TX-/MIW-Identität)                                                                 | `image: ghcr.io/project-construct-x/wallet`                                                                 |
| 5 | **Helm-Chart-Anpassungen** inkl. neuer Templates                                                                                 | `values-consumer.yaml`, `values-provider.yaml`, `post-install-vault-setup.yaml`, `vault-edc-configmap.yaml` |
| 6 | **Versions-Pinning & Update** auf EDC 0.15.1 / TX 0.12.0, Repo-Version `0.13.0-SNAPSHOT`                                         | `build.gradle.kts` der con-x-Module, `gradle.properties`                                                    |
| 7 | **Gradle-Registrierung & Doku**                                                                                                  | `settings.gradle.kts`, diverse `README.md`                                                                  |

> ⚠️ **Hinweis zur Einordnung:** Die oft als "Catena-X-Features" bezeichneten
> Funktionen (BPN-Policy, DCP, EDR, Wallet-Support) sind
> **Tractus-X-/EDC-Upstream-Features** und keine Construct-X-Eigenentwicklungen.
> Construct-X *konfiguriert und integriert* sie lediglich für den
> Construct-X-Datenraum.

---

## 3. Aktuelle Version

| Komponente                              | Version                                       |
|-----------------------------------------|-----------------------------------------------|
| **Construct-X EDC (dieses Repository)** | `0.13.0-SNAPSHOT`                             |
| Upstream **Eclipse EDC**                | `0.15.1`                                      |
| Upstream **Tractus-X EDC**              | `0.12.0`                                      |
| Helm-Chart `tractusx-connector`         | `0.13.0-SNAPSHOT`                             |
| Docker-Basis-Image                      | `eclipse-temurin:25-jre-alpine`               |
| Lokale Wallet (Testbed)                 | `ghcr.io/project-construct-x/wallet:0.17.0-1` |

> Die **Construct-X-Distributionsmodule** pinnen bewusst *explizite* Upstream-Versionen
> (EDC `0.15.1`, TX `0.12.0`), statt den zentralen Version-Katalog zu verwenden – so
> ist die Distribution reproduzierbar und stabil.

---

## 4. Architektur – Schichtenmodell

Der Connector folgt der Standard-EDC-Schichtenarchitektur.
Abhängigkeiten zeigen immer **nach unten**:

```
┌───────────────────────────────────────────────────────────────────────┐
│  DISTRIBUTIONEN  (lauffähige JARs)                                    │
│                                                                       │
│   ┌─────────────────────────────┐   ┌─────────────────────────────┐   │
│   │ con-x-controlplane-         │   │ con-x-dataplane-            │   │
│   │ postgresql-hashicorp-vault  │   │ postgresql-hashicorp-vault  │   │
│   └──────────────┬──────────────┘   └──────────────┬──────────────┘   │
└──────────────────┼─────────────────────────────────┼──────────────────┘
                   │                                 │
                   ▼                                 ▼
┌───────────────────────────────────────────────────────────────────────┐
│  EXTENSIONS  (/edc-extensions)  –  Laufzeit-Features                  │
│                                                                       │
│   • bpn-validation            • edr (edr-api-v2 / callback)           │
│   • cx-policy / -legacy       • agreements / agreements-bpns          │
│   • dcp (cx-dcp / tx-dcp …)   • migrations, dataplane, …              │
└──────────────────────────────────┬────────────────────────────────────┘
                                   │
                                   ▼
┌───────────────────────────────────────────────────────────────────────┐
│  CORE  (/core)  –  Basis-Implementierungen                            │
│                                                                       │
│   • edr-core                  • json-ld-core / json-ld-cx             │
└──────────────────────────────────┬────────────────────────────────────┘
                                   │
                                   ▼
┌───────────────────────────────────────────────────────────────────────┐
│  SPI  (/spi)  –  nur Verträge / Interfaces                            │
│                                                                       │
│   • Service Provider Interfaces                                       │
└───────────────────────────────────────────────────────────────────────┘

   Abhängigkeitsrichtung:  Distributionen → Extensions → Core → SPI
   (Extensions dürfen auch direkt auf SPI zugreifen)
```

**Schichtenregeln:**

- **SPI** (`/spi/`): nur Contracts/Interfaces; darf nur von anderen SPIs oder Shared-Libs abhängen.
- **Core** (`/core/`): Basis-Implementierungen; hängt nur von SPIs ab.
- **Extensions** (`/edc-extensions/`): konkrete Laufzeit-Features; hängen von SPIs ab.
- **Distributionen**: bündeln Extensions per **Gradle Shadow Plugin** zu einer ausführbaren „Fat-JAR".

---

## 5. Aufbau: Control Plane & Data Plane

Ein Connector besteht aus **zwei getrennten Laufzeiten**, die unabhängig
skaliert und deployt werden können:

```
                    ┌───────────────────────────────────────────┐
                    │   Construct-X Connector (ein Teilnehmer)  │
                    │                                           │
                    │  ┌───────────────────┐                    │
                    │  │   Control Plane   │                    │
                    │  │  (Verhandlung,    │                    │
                    │  │   Policies,       │                    │
                    │  │   Katalog,        │                    │
                    │  │   Verträge, EDR)  │                    │
                    │  └─────────┬─────────┘                    │
                    │            │ Data Plane Signaling         │
                    │            │ (Control API)                │
                    │            ▼                              │
                    │  ┌───────────────────┐                    │
                    │  │    Data Plane     │                    │
                    │  │  (Datentransfer,  │                    │
                    │  │   Public API,     │                    │
                    │  │   Token-Refresh)  │                    │
                    │  └───────────────────┘                    │
                    └──────┬──────────────────────┬───────┬─────┘
                           │                      │       │
              ┌────────────┴─────────┐   ┌────────┴──┐  ┌─┴──────────────┐
              │                      │   │           │  │                │
              ▼                      ▼   ▼           ▼  ▼                ▼
      ┌───────────────┐     ┌──────────────────┐   ┌────────────────────────┐
      │  PostgreSQL   │     │  HashiCorp Vault │   │  Construct-X Wallet    │
      │  (Zustände:   │     │  (Secrets/Keys)  │   │  (Identity Hub /       │
      │  Assets,      │     │                  │   │   DID / STS)           │
      │  Verträge,    │     │                  │   │                        │
      │  EDRs, …)     │     │                  │   │                        │
      └───────────────┘     └──────────────────┘   └────────────────────────┘
      genutzt von: Control Plane UND Data Plane (jeweils)
```

| Laufzeit          | Verantwortung                                                                                               |
|-------------------|-------------------------------------------------------------------------------------------------------------|
| **Control Plane** | Katalog, Contract Negotiation, Policy-Auswertung, Transferverwaltung, EDR-Ausstellung, Identität (DCP/DSP). |
| **Data Plane**    | Physischer Datentransfer, Public API v2, Proxy, Token-Refresh, Data-Flow-Handling.                          |

**Externe Abhängigkeiten** (nicht Teil des Connectors, müssen bereitgestellt werden):

- **PostgreSQL** – persistente Zustände (Assets, Verträge, EDRs, …)
- **HashiCorp Vault** – Secrets und kryptografische Schlüssel (Secrets *nie* in Config-Dateien!)
- **Construct-X Wallet** – Identity Hub, DID-Dokumente, STS (Secure Token Service), Issuer für Verifiable Credentials

---

## 6. Verwendete Extensions

### 5.1 Zusammensetzung der Construct-X-Distributionen

Die Construct-X-Distributionen sind **schlanke Assemblies**: Sie konsumieren die
*veröffentlichten* Upstream-BOMs und ergänzen gezielt einige Tractus-X-Module.

**Control Plane** (`con-x-controlplane-postgresql-hashicorp-vault`):

| Abhängigkeit                      | Herkunft   | Zweck                                   |
|-----------------------------------|------------|-----------------------------------------|
| `controlplane-dcp-bom`            | EDC 0.15.1 | Control-Plane-Kern inkl. DCP-Identität  |
| `controlplane-feature-sql-bom`    | EDC 0.15.1 | SQL-/PostgreSQL-Persistenz              |
| `vault-hashicorp`                 | EDC 0.15.1 | Secrets aus HashiCorp Vault             |
| `agreements`                      | TX 0.12.0  | Contract-Retirement-Auswertung          |
| `retirement-evaluation-store-sql` | TX 0.12.0  | SQL-Store für Contract Retirement       |
| `control-plane-migration`         | TX 0.12.0  | Flyway-Migrationen (Control Plane)      |
| `tx-dcp`                          | TX 0.12.0  | Tractus-X Decentralized Claims Protocol |

**Data Plane** (`con-x-dataplane-postgresql-hashicorp-vault`):

| Abhängigkeit                      | Herkunft   | Zweck                             |
|-----------------------------------|------------|-----------------------------------|
| `dataplane-base-bom`              | EDC 0.15.1 | Data-Plane-Kern                   |
| `dataplane-feature-sql-bom`       | EDC 0.15.1 | SQL-/PostgreSQL-Persistenz        |
| `vault-hashicorp`                 | EDC 0.15.1 | Secrets aus HashiCorp Vault       |
| `participant-context-config-core` | EDC 0.15.1 | Participant-Context-Konfiguration |
| `dataplane-public-api-v2`         | TX 0.12.0  | Public Data API v2                |
| `dataplane-util`                  | TX 0.12.0  | Data-Plane-Hilfsfunktionen        |

> Hinweis: Die Data-Plane schließt bewusst das EDC-Modul `data-plane-util` aus und
> ersetzt es durch die Tractus-X-Variante.

### 5.2 Extensions im Repository (`/edc-extensions/`)

Das Repository enthält den vollständigen Satz an Tractus-X-/Construct-X-Extensions.
Das gemeinsame Bundle `edc-controlplane-base` bindet u. a. folgende Extensions ein:

| Extension                                                                             | Funktion                                                                |
|---------------------------------------------------------------------------------------|-------------------------------------------------------------------------|
| `bpn-validation`                                                                      | ODRL-Policy-Funktionen für BPN-Number & BPN-Group inkl. API + SQL-Store |
| `cx-policy`, `cx-policy-legacy`                                                       | Catena-X ODRL-Policy-Funktionen (Membership, Affiliates …)              |
| `dcp/cx-dcp`, `dcp/tx-dcp`, `dcp/tx-dcp-sts-div`, `dcp/verifiable-presentation-cache` | Decentralized Claims Protocol, STS-DIM-Anbindung, VP-Cache              |
| `edr/edr-api-v2`, `edr/edr-callback`                                                  | EDR-API v2 & Callbacks (Endpoint Data Reference)                        |
| `agreements`, `agreements-bpns`                                                       | Contract-Retirement-Auswertung mit BPN-Scoping                          |
| `tokenrefresh-handler`                                                                | Token-Refresh-Koordination zwischen Control- und Data-Plane             |
| `bdrs-client`                                                                         | BPN-DID-Resolution-Service-Client                                       |
| `connector-discovery`                                                                 | Connector-Discovery-API + CX-Discovery                                  |
| `data-flow-properties-provider`                                                       | Zusätzliche Data-Flow-Eigenschaften                                     |
| `provision-additional-headers`                                                        | Zusätzliche HTTP-Header beim Provisioning                               |
| `dataspace-protocol`                                                                  | DSP-Anpassungen                                                         |
| `did-document`                                                                        | DID-Dokument-Service (Self-Registration & DIM)                          |
| `token-interceptor`, `event-subscriber`, `log4j2-monitor`                             | Querschnittsfunktionen (Token, Events, Logging)                         |
| `validators/empty-asset-selector`                                                     | Validierung leerer Asset-Selektoren                                     |
| `migrations`                                                                          | Flyway-Migrationen (Connector, Control-Plane, Data-Plane)               |

*(Weitere Module: `backport`, `non-finite-provider-push`, `single-participant-vault`.)*

---

## 7. Verwendete Protokolle & Konzepte

```
  DSP   (Dataspace Protocol)          ──▶  Katalog, Contract Negotiation, Transfer Process
  DCP   (Decentralized Claims Proto.) ──▶  Identität & Verifiable Credentials (z. B. MembershipCredential)
  ODRL  (Policies)                    ──▶  Zugriffs- & Nutzungsregeln (BPN, Membership, Affiliates)
  EDR   (Endpoint Data Reference)     ──▶  zeitlich begrenzter Token für Datenzugriff (+ Refresh)
```

- **DSP (Dataspace Protocol):** standardisierter Ablauf für Katalogabfrage, Vertragsverhandlung und Transfer zwischen Connectoren.
- **DCP (Decentralized Claims Protocol):** dezentrale Identitäts- und Nachweisprüfung über DIDs und Verifiable Credentials.
- **ODRL Policies:** maschinenlesbare Zugriffs- und Nutzungsregeln (in Construct-X u. a. BPN- und Membership-basiert).
- **EDR (Endpoint Data Reference):** kurzlebiges Zugriffstoken, das dem Consumer den Datenabruf über die Public API der Data Plane ermöglicht.

---

## 8. Ablauf: Vom Katalog zum Datentransfer

Der typische Interaktionsfluss zwischen einem Consumer- und einem
Provider-Connector:

```
   Consumer          Consumer         Wallet /            Provider          Provider
   Control Plane      Data Plane       Issuer (DCP)       Control Plane     Data Plane
   ────┬─────         ────┬─────        ────┬─────        ─────┬─────        ────┬─────
       │                                    │                                    │
       │ 0) Membership Credential anfordern │                                    │
       │───────────────────────────────────▶│                                    │
       │◀───────────────────────────────────│                                    │
       │      Verifiable Presentation       │                                    │
       │                                    │                                    │
       │ 1) Catalog Request (DSP)                              │                 │
       │──────────────────────────────────────────────────────▶│                 │
       │◀──────────────────────────────────────────────────────│                 │
       │      Katalog (Assets + Policies, BPN-gefiltert)       │                 │
       │                                                       │                 │
       │ 2) Contract Negotiation (DSP)                         │                 │
       │──────────────────────────────────────────────────────▶│                 │
       │                                        Policy-Auswertung                │
       │                                        (BPN, Membership …)              │
       │◀──────────────────────────────────────────────────────│                 │
       │      Contract Agreement                               │                 │
       │                                                       │                 │
       │ 3) Transfer Process starten (DSP)                     │                 │
       │──────────────────────────────────────────────────────▶│                 │
       │◀──────────────────────────────────────────────────────│                 │
       │      4) EDR (Endpoint Data Reference) ausstellen      │                 │
       │                                                       │                 │
       │        │                                              │                 │
       │        │ EDR verwenden                                │                 │
       │        │───────────────────────────────────────────────────────────────▶│
       │        │  5) Datenabruf über Public API (mit EDR-Token)                 │
       │        │◀───────────────────────────────────────────────────────────────│
       │        │       Daten (Token-Refresh bei Bedarf)                         │
```

1. **Catalog Request** – Consumer fragt den Provider-Katalog ab (nur zugelassene Assets sichtbar).
2. **Contract Negotiation** – Vertragsverhandlung; der Provider prüft die Policies (u. a. BPN, Membership).
3. **Transfer Process** – nach erfolgreichem Vertrag wird ein Transfer initiiert.
4. **EDR-Ausstellung** – der Consumer erhält eine Endpoint Data Reference (Token).
5. **Datenabruf** – der Consumer ruft die Daten über die Public API der Data Plane ab; Tokens werden bei Bedarf automatisch erneuert.

---

## 9. Wie "sieht" der Connector aus? (Laufzeit-Topologie)

Das lokale Docker-Testbed
(`edc-controlplane/edc-controlplane-construct-x/local/`) zeigt eine vollständige
Zwei-Teilnehmer-Umgebung: je ein Consumer- und ein Provider-Stack, die
gegeneinander getestet werden können.

```
                              ┌───────────────────────────┐
                              │   local-issuer-wallet     │
                              │   (Trusted Issuer)        │
                              └──────────┬────────────────┘
                                         │
                         ┌───────────────┴────────────────┐
                         │                                │
                         ▼                                ▼
   ┌─────────────────────────────────┐        ┌─────────────────────────────────┐
   │        CONSUMER-TEILNEHMER      │        │        PROVIDER-TEILNEHMER      │
   │                                 │        │                                 │
   │  ┌────────────────────────┐     │        │  ┌────────────────────────┐     │
   │  │   consumer-wallet      │     │        │  │   provider-wallet      │     │
   │  │   (Identity Hub / STS) │     │        │  │   (Identity Hub / STS) │     │
   │  └───────────┬────────────┘     │        │  └───────────┬────────────┘     │
   │              │                  │        │              │                  │
   │  ┌───────────▼──────────────┐   │        │  ┌───────────▼──────────────┐   │
   │  │  consumer-controlplane   │◀──┼───DSP──┼─▶│  provider-controlplane   │   │
   │  │  :29000 / :29010 (Mgmt)  │   │        │  │  :39000 / :39010 (Mgmt)  │   │
   │  │  :29020 (DSP)            │   │        │  │  :39020 (DSP)            │   │
   │  └───────────┬──────────────┘   │        │  └───────────┬──────────────┘   │
   │              │                  │        │              │                  │
   │  ┌───────────▼─────────────┐    │        │  ┌───────────▼──────────────┐   │
   │  │  consumer-dataplane     │◀──Public API──▶│  provider-dataplane      │   │
   │  │  :9600 (Public API)     │    │        │  │  :9500 (Public API)      │   │
   │  └─────────────────────────┘    │        │  └──────────────────────────┘   │
   └─────────────┬───────────────────┘        └─────────────┬───────────────────┘
                 │                                          │
                 └───────────────────┬──────────────────────┘
                                     ▼
                  ┌─────────────────────────────────────────┐
                  │        GEMEINSAME INFRASTRUKTUR         │
                  │                                         │
                  │  shared-postgres     shared-vault       │
                  │  (PostgreSQL 16)     (HashiCorp Vault)  │
                  │                                         │
                  │  vault-init (erzeugt EDR-Signing-Keys,  │
                  │              terminiert danach)         │
                  └─────────────────────────────────────────┘
```

**Testbed-Bestandteile:**

- 3× **Wallet** (Issuer, Consumer, Provider) – `ghcr.io/project-construct-x/wallet:0.17.0-1`
- 2× **Control Plane** + 2× **Data Plane** (Consumer & Provider) – Construct-X-Images
- 1× **PostgreSQL** und 1× **HashiCorp Vault** (aus Ressourcengründen gemeinsam genutzt)
- 1× **vault-init** – kurzlebiger Init-Container, der EDR-Signing-Keys erzeugt und im Vault ablegt

Zum Testen der Abläufe liegt eine **Bruno-HTTP-Collection** unter `local/bruno/`
bei (Ordner `identities` zuerst ausführen, danach `transactions`).

### Wichtige API-Endpunkte (pro Runtime)

| API                | Plane         | Zweck                                                                      |
|--------------------|---------------|----------------------------------------------------------------------------|
| **Management API** | Control Plane | Assets, Policies, Contract Definitions, Verhandlungen, Transfers verwalten |
| **DSP API**        | Control Plane | Protokoll-Kommunikation zwischen Connectoren                               |
| **Control API**    | Control Plane | Data-Plane-Signaling / Data-Plane-Selector                                 |
| **Validation API** | Control Plane | Token-/EDR-Validierung                                                     |
| **Public API v2**  | Data Plane    | Eigentlicher Datenabruf durch den Consumer                                 |

---

## 10. Build & Deployment

### Build (Gradle)

```bash
./gradlew build                              # vollständiger Build (Checkstyle + Tests)
./gradlew :edc-controlplane:edc-controlplane-construct-x:con-x-controlplane-postgresql-hashicorp-vault:dockerize
./gradlew :edc-dataplane:edc-dataplane-construct-x:con-x-dataplane-postgresql-hashicorp-vault:dockerize
```

- **Einstiegspunkt** aller Distributionen: `org.eclipse.edc.boot.system.runtime.BaseRuntime`
- **Konfiguration:** `edc.fs.config=/app/configuration.properties`
- Distributionen werden per **Shadow Plugin** zu einer Fat-JAR gebündelt.

### Deployment (Helm)

| Chart                              | Zweck                                                   |
|------------------------------------|---------------------------------------------------------|
| `charts/tractusx-connector`        | Produktions-Chart (benötigt externe PostgreSQL + Vault) |
| `charts/tractusx-connector-memory` | In-Memory-Chart für Tests                               |

---

## 11. Kurz-Glossar

| Begriff     | Bedeutung                                                            |
|-------------|----------------------------------------------------------------------|
| **EDC**     | Eclipse Dataspace Components – Basis-Framework                       |
| **BPN**     | Business Partner Number – eindeutige Kennung eines Geschäftspartners |
| **DSP**     | Dataspace Protocol – Protokoll für Katalog/Verhandlung/Transfer      |
| **DCP**     | Decentralized Claims Protocol – dezentrale Identität/Nachweise       |
| **DID**     | Decentralized Identifier                                             |
| **STS**     | Secure Token Service (Teil der Wallet)                               |
| **EDR**     | Endpoint Data Reference – Zugriffstoken für Datenabruf               |
| **ODRL**    | Open Digital Rights Language – Sprache für Policies                  |
| **VC / VP** | Verifiable Credential / Verifiable Presentation                      |

---

*Erstellt auf Basis des Repository-Stands `0.13.0-SNAPSHOT` (Branch `develop`).*
