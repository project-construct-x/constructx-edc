/*
 * Copyright (c) 2026 Fraunhofer-Gesellschaft zur Foerderung der angewandten Forschung e.V. (represented by Fraunhofer ISST)
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.constructx.edc.policyhub;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import okhttp3.Request;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.security.Vault;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * HTTP client for the Construct-X Policy Hub. Fetches the ODRL rendering of a policy from the
 * hub's {@code GET /api/v1/policies/{id}/odrl} endpoint. The basic-auth credentials are resolved
 * from the vault on every call, so the secret never lives in configuration and credential
 * rotation does not require a restart.
 */
public class PolicyHubClient {

    private final EdcHttpClient httpClient;
    private final String baseUrl;
    private final Vault vault;
    private final String credentialsAlias;

    public PolicyHubClient(EdcHttpClient httpClient, String baseUrl, Vault vault, String credentialsAlias) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.vault = vault;
        this.credentialsAlias = credentialsAlias;
    }

    /**
     * Fetches the ODRL policy definition for the given Policy Hub policy id.
     *
     * @param hubPolicyId the id of the policy in the Policy Hub
     * @return the raw JSON-LD policy definition as served by the hub, or a failure
     */
    public Result<JsonObject> fetchPolicyDefinition(String hubPolicyId) {
        var credentials = vault.resolveSecret(credentialsAlias);
        if (credentials == null) {
            return Result.failure("No Policy Hub credentials found in the vault under alias '%s'".formatted(credentialsAlias));
        }

        var request = new Request.Builder()
                .url("%s/api/v1/policies/%s/odrl".formatted(baseUrl, hubPolicyId))
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
                .get()
                .build();

        try (var response = httpClient.execute(request)) {
            if (!response.isSuccessful()) {
                return Result.failure("Policy Hub returned status %s for policy '%s'".formatted(response.code(), hubPolicyId));
            }
            var body = response.body();
            if (body == null) {
                return Result.failure("Policy Hub returned an empty body for policy '%s'".formatted(hubPolicyId));
            }
            try (var reader = Json.createReader(body.byteStream())) {
                return Result.success(reader.readObject());
            }
        } catch (IOException | JsonException e) {
            return Result.failure("Failed to fetch policy '%s' from the Policy Hub: %s".formatted(hubPolicyId, e.getMessage()));
        }
    }
}
