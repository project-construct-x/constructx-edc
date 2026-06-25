## Prerequisites

Make sure you have...
- ...docker installed on your machine (recommended: version>25.0.4)
- ...a domain for your IP-address
- ...copied [.env.example](./.env.example) to `.env` and changed the secrets
- ...the necessary docker images from [controlplane](https://github.com/project-construct-x/constructx-edc/pkgs/container/con-x-controlplane-postgresql-hashicorp-vault), [dataplane](https://github.com/project-construct-x/constructx-edc/pkgs/container/con-x-dataplane-postgresql-hashicorp-vault), [wallet](https://github.com/project-construct-x/wallet/pkgs/container/wallet)
    - wallet
    - controlplane
    - dataplane
    - Postgres DB
    - HashiCorp Vault


The domain and a valid email must be set in the [.env-file](./.env):

```
EMAIL=<YOUR-VALID-EMAIL>
DOMAIN=<YOUR-DOMAIN>
ISSUER_DOMAIN=<YOUR_ISSUER_DOMAIN>
```


## Steps
### Traefik

#### Create Network

First run the command `docker network create proxy`

Sample output:
```
35c1364363795...
```

#### Start Traefik 

Run the following command from the current folder to start the traefik container:
```bash
docker compose -f ./traefik/traefik-compose.yaml --env-file ./.env up -d
```

Wait 30 seconds before proceeding with the next steps.

Sample output:
```
[+] Running 1/1
✔ Container traefik Created 
```

## EDC in Memory

### Start EDC-Services in Memory

Run `docker compose -f ./local/docker-compose-memory.yaml --env-file ./.env up -d`

Sample output:
```
[+] Running 7/7
✔ Network network Created
✔ vault Healthy
✔ postgres Healthy 
✔ idhub Created 
✔ vault-init Exited
✔ dataplane Created
✔ controlplane Created
```


### Stop and Remove in Memory

1. `docker compose -f ./traefik/traefik-compose.yaml down`
2. `docker compose -f ./local/docker-compose-memory.yaml --env-file ./.env down -v`
3. `docker network rm proxy`

## EDC

### Initialize Vault (only once)

`docker compose -f ./local/docker-compose.yaml --env-file ./.env up shared-vault -d`

#### Set the appropriate ownership
The default Vault user inside the Container has the UID `100`.

`sudo chown -R 100:100 ./local/vault/data`

#### Open a Terminal inside the Container

`docker exec -it shared-vault sh`

Run the following command inside the vault container:

`vault operator init -key-shares=1 -key-threshold=1`

Save the unseal key and the root token. Set the root token in the [.env](./.env) file:

```VAULT_TOKEN=<YOUR-VAULT_TOKEN>```

#### Unseal Vault
Run the following command with the unseal key from the previous step.

`vault operator unseal <unseal-key>`

#### Log in with the root token

`export VAULT_TOKEN=<root-token>`

`vault login`

#### Enable secrets
`vault secrets enable -path=secret kv-v2`

#### Stop Vault

`docker compose -f ./local/docker-compose.yaml --env-file ./.env down shared-vault -v`

Your Vault is now initialized.

### Start EDC-Services
`docker compose -f ./local/docker-compose.yaml --env-file ./.env up -d`

#### Open a Terminal inside the Container

`docker exec -it shared-vault sh`

#### Unseal Vault
Run the following command with the unseal key.

`vault operator unseal <unseal-key>`


### Stop and Remove
1. `docker compose -f ./traefik/traefik-compose.yaml --env-file ./.env down`
2. `docker compose -f ./local/docker-compose.yaml --env-file ./.env down -v`
3. `docker network rm proxy`