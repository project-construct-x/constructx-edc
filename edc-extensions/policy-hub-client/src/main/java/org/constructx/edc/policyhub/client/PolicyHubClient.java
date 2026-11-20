/*
 * Copyright (c) 2026 adesso SE. All rights reserved.
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

package org.constructx.edc.policyhub.client;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
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
 *
 * <p>Uses the runtime's plain {@link OkHttpClient} (same timeouts and configuration as the
 * {@code EdcHttpClient}) rather than the {@code EdcHttpClient} itself, whose built-in retry policy
 * ({@code edc.core.retry.*}) would silently retry every request for ~15 s. Each call makes exactly
 * one attempt; waiting for an unreachable hub is left to the sync's own, logged backoff, and the
 * management endpoints answer {@code 503} right away.
 */
public class PolicyHubClient {

    private final OkHttpClient httpClient;
    private final String baseUrl;
    private final Vault vault;
    private final String credentialsAlias;

    public PolicyHubClient(OkHttpClient httpClient, String baseUrl, Vault vault, String credentialsAlias) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.vault = vault;
        this.credentialsAlias = credentialsAlias;
    }

    /**
     * Fetches the ODRL policy definition for the given Policy Hub policy id.
     *
     * @param hubPolicyId the id of the policy in the Policy Hub
     * @return the raw JSON-LD policy definition as served by the hub, or a failure that concerns
     *         this policy only (e.g. the hub returned {@code 404})
     * @throws PolicyHubUnavailableException if the hub itself cannot be reached
     * @throws PolicyHubAccessDeniedException if the hub rejects the credentials ({@code 401}/{@code 403})
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

        try (var response = httpClient.newCall(request).execute()) {
            if (response.code() == 401 || response.code() == 403) {
                throw new PolicyHubAccessDeniedException("status %d".formatted(response.code()));
            }
            if (isHubUnavailable(response.code())) {
                throw new PolicyHubUnavailableException("status %d".formatted(response.code()));
            }
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
        } catch (IOException e) {
            throw new PolicyHubUnavailableException(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        } catch (JsonException e) {
            return Result.failure("Failed to read policy '%s' from the Policy Hub: %s".formatted(hubPolicyId, e.getMessage()));
        }
    }

    private static boolean isHubUnavailable(int status) {
        return status >= 500 || status == 408 || status == 429;
    }
}
