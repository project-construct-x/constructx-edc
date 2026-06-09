#!/usr/bin/env bash

#
# Copyright (c) 2026 Fraunhofer-Gesellschaft zur Foerderung der angewandten Forschung e.V. (represented by Fraunhofer ISST)
# Copyright (c) 2026 Contributors to the Eclipse Foundation
#
# See the NOTICE file(s) distributed with this work for additional
# information regarding copyright ownership.
#
# This program and the accompanying materials are made available under the
# terms of the Apache License, Version 2.0 which is available at
# https://www.apache.org/licenses/LICENSE-2.0.
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations
# under the License.
#
# SPDX-License-Identifier: Apache-2.0
#

VAULT="${VAULT_ADDR:-http://shared-vault:8200}"
TOKEN="${VAULT_TOKEN:?missing VAULT_TOKEN}"

# function that creates and deploys a rsa keypair:

create_and_store_keypair() {
  local prefix=$1

  # create rsa keypair
  openssl genrsa -out /tmp/${prefix}_priv_pkcs1.pem 2048
  openssl pkcs8 -topk8 -nocrypt -in /tmp/${prefix}_priv_pkcs1.pem -out /tmp/${prefix}_priv.pem
  openssl rsa -in /tmp/${prefix}_priv_pkcs1.pem -pubout -out /tmp/${prefix}_pub.pem

  # deploy secrets to vault
  jq -n --rawfile content /tmp/${prefix}_priv.pem '{data:{content:$content}}' | \
    curl -fsS -H "X-Vault-Token: $TOKEN" -H "Content-Type: application/json" \
      -X POST --data-binary @- "$VAULT/v1/secret/data/${prefix}_priv"

  jq -n --rawfile content /tmp/${prefix}_pub.pem '{data:{content:$content}}' | \
    curl -fsS -H "X-Vault-Token: $TOKEN" -H "Content-Type: application/json" \
      -X POST --data-binary @- "$VAULT/v1/secret/data/${prefix}_pub"

  # cleanup temp files
  rm -f /tmp/${prefix}_priv_pkcs1.pem /tmp/${prefix}_priv.pem /tmp/${prefix}_pub.pem
}

# create keypair for consumer and provider dataplane:

create_and_store_keypair "cons"
create_and_store_keypair "prov"

create_and_store_aes_key() {
  local prefix=$1
  local aes_key

  # AES-Key erzeugen
  aes_key="$(openssl rand -base64 32 | tr -d '\n')"

  # write AES-Key to vault, bind path to prefix
  jq -n --arg content "$aes_key" '{data:{content:$content}}' | \
    curl -sSf \
      -H "X-Vault-Token: $TOKEN" \
      -H "Content-Type: application/json" \
      -X POST \
      --data-binary @- \
      "$VAULT/v1/secret/data/${prefix}-aes-key-alias" \
    || { echo "Failed to create aes key entry for ${prefix}"; exit 1; }

  echo "AES key stored at secret/data/${prefix}-aes-key-alias"
}

# create AES keys for wallets
create_and_store_aes_key "issuer-wallet"
create_and_store_aes_key "consumer-wallet"
create_and_store_aes_key "provider-wallet"