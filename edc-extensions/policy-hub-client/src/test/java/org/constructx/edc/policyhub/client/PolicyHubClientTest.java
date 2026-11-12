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

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.eclipse.edc.spi.security.Vault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyHubClientTest {

    private static final String HUB_POLICY_ID = "00000000-0000-0000-0000-000000000002";
    private static final String CREDENTIALS_ALIAS = "policy-hub-credentials";

    private final OkHttpClient httpClient = mock();
    private final Call call = mock();
    private final Vault vault = mock();

    private final PolicyHubClient client = new PolicyHubClient(httpClient, "http://policy-hub:8080/", vault, CREDENTIALS_ALIAS);

    @BeforeEach
    void setUp() {
        when(vault.resolveSecret(CREDENTIALS_ALIAS)).thenReturn("user:password");
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
    }

    @Test
    void fetchPolicyDefinition_whenHubReturnsPolicy_shouldReturnIt() throws IOException {
        givenResponse(200, "{\"@id\":\"my-policy\"}");

        var result = client.fetchPolicyDefinition(HUB_POLICY_ID);

        assertThat(result).isSucceeded();
        assertThat(result.getContent().getString("@id")).isEqualTo("my-policy");
    }

    @Test
    void fetchPolicyDefinition_whenConnectionFails_shouldThrowUnavailable() throws IOException {
        when(call.execute()).thenThrow(new ConnectException("Connection refused"));

        assertThatThrownBy(() -> client.fetchPolicyDefinition(HUB_POLICY_ID))
                .isInstanceOfSatisfying(PolicyHubUnavailableException.class,
                        e -> assertThat(e.getReason()).isEqualTo("Connection refused"));
    }

    @ParameterizedTest
    @ValueSource(ints = { 500, 502, 503, 408, 429 })
    void fetchPolicyDefinition_whenHubReturnsOutageStatus_shouldThrowUnavailable(int status) throws IOException {
        givenResponse(status, "{}");

        assertThatThrownBy(() -> client.fetchPolicyDefinition(HUB_POLICY_ID))
                .isInstanceOfSatisfying(PolicyHubUnavailableException.class,
                        e -> assertThat(e.getReason()).isEqualTo("status " + status));
    }

    @ParameterizedTest
    @ValueSource(ints = { 401, 403 })
    void fetchPolicyDefinition_whenHubRejectsCredentials_shouldThrowAccessDenied(int status) throws IOException {
        givenResponse(status, "{}");

        assertThatThrownBy(() -> client.fetchPolicyDefinition(HUB_POLICY_ID))
                .isInstanceOfSatisfying(PolicyHubAccessDeniedException.class, e -> {
                    assertThat(e.getReason()).isEqualTo("status " + status);
                    assertThat(e.getMessage()).contains("rejected the credentials").contains("vault");
                });
    }

    @ParameterizedTest
    @ValueSource(ints = { 400, 404 })
    void fetchPolicyDefinition_whenHubRejectsThisPolicy_shouldReturnFailure(int status) throws IOException {
        givenResponse(status, "{}");

        var result = client.fetchPolicyDefinition(HUB_POLICY_ID);

        assertThat(result).isFailed();
        assertThat(result.getFailureDetail()).contains("status " + status);
    }

    @Test
    void fetchPolicyDefinition_whenBodyIsNoJson_shouldReturnFailure() throws IOException {
        givenResponse(200, "not json");

        var result = client.fetchPolicyDefinition(HUB_POLICY_ID);

        assertThat(result).isFailed();
    }

    @Test
    void fetchPolicyDefinition_whenHubUnreachable_shouldTryExactlyOnce() throws IOException {
        when(call.execute()).thenThrow(new ConnectException("Connection refused"));

        assertThatThrownBy(() -> client.fetchPolicyDefinition(HUB_POLICY_ID)).isInstanceOf(PolicyHubUnavailableException.class);

        // no hidden retries: waiting for the hub is the sync's job, with its own log lines
        verify(call, times(1)).execute();
    }

    private void givenResponse(int code, String body) throws IOException {
        when(call.execute()).thenReturn(new Response.Builder()
                .request(new Request.Builder().url("http://policy-hub:8080/").build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("status " + code)
                .body(ResponseBody.create(body, MediaType.get("application/json")))
                .build());
    }
}
