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

package org.constructx.edc.policyhub.api;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.ServiceUnavailableException;
import org.constructx.edc.policyhub.client.PolicyHubAccessDeniedException;
import org.constructx.edc.policyhub.client.PolicyHubUnavailableException;
import org.constructx.edc.policyhub.service.PolicyHubImportService;
import org.constructx.edc.policyhub.service.PolicyHubSyncService;
import org.constructx.edc.policyhub.service.PolicyHubSyncService.Abort;
import org.constructx.edc.policyhub.service.PolicyHubSyncService.AbortCause;
import org.constructx.edc.policyhub.service.PolicyHubSyncService.SyncSummary;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.web.spi.ApiErrorDetail;
import org.eclipse.edc.web.spi.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static jakarta.ws.rs.core.Response.Status.BAD_GATEWAY;
import static jakarta.ws.rs.core.Response.Status.SERVICE_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyHubApiControllerTest {

    private static final String HUB_POLICY_ID = "00000000-0000-0000-0000-000000000002";

    private final PolicyHubImportService importService = mock();
    private final PolicyHubSyncService syncService = mock();
    private final TypeTransformerRegistry transformerRegistry = mock();
    private final Monitor monitor = mock();

    private final PolicyHubApiController controller =
            new PolicyHubApiController(importService, syncService, transformerRegistry, monitor);

    @Test
    void importPolicy_whenPolicyIsInvalid_shouldLogWarningAndAnswerBadRequest() {
        var reason = "Policy '%s' is not a valid policy definition: Left operand not allowed (at 'policy.permission')".formatted(HUB_POLICY_ID);
        when(importService.importPolicy(HUB_POLICY_ID)).thenReturn(ServiceResult.badRequest(reason));

        assertThatThrownBy(() -> controller.importPolicy(HUB_POLICY_ID)).isInstanceOf(InvalidRequestException.class);

        verify(monitor).warning("Policy Hub import of '%s' failed: %s".formatted(HUB_POLICY_ID, reason));
    }

    @Test
    void importPolicy_whenHubUnreachable_shouldLogWarningAndAnswerServiceUnavailable() {
        when(importService.importPolicy(HUB_POLICY_ID)).thenThrow(new PolicyHubUnavailableException("Connection refused"));

        assertThatThrownBy(() -> controller.importPolicy(HUB_POLICY_ID))
                .isInstanceOfSatisfying(ServiceUnavailableException.class, e -> {
                    assertThat(e.getResponse().getStatus()).isEqualTo(SERVICE_UNAVAILABLE.getStatusCode());
                    assertThat(e.getResponse().getEntity()).asList().singleElement()
                            .isInstanceOfSatisfying(ApiErrorDetail.class, detail ->
                                    assertThat(detail.getMessage()).contains(HUB_POLICY_ID).contains("Connection refused"));
                });

        verify(monitor).warning("Policy Hub import of '%s' failed: Policy Hub unreachable (Connection refused)".formatted(HUB_POLICY_ID));
    }

    @Test
    void importPolicy_whenHubRejectsCredentials_shouldLogWarningAndAnswerBadGateway() {
        when(importService.importPolicy(HUB_POLICY_ID)).thenThrow(new PolicyHubAccessDeniedException("status 401"));

        assertThatThrownBy(() -> controller.importPolicy(HUB_POLICY_ID))
                .isInstanceOfSatisfying(ServerErrorException.class, e -> {
                    assertThat(e.getResponse().getStatus()).isEqualTo(BAD_GATEWAY.getStatusCode());
                    assertThat(e.getResponse().getEntity()).asList().singleElement()
                            .isInstanceOfSatisfying(ApiErrorDetail.class, detail ->
                                    assertThat(detail.getMessage()).contains(HUB_POLICY_ID).contains("rejected the credentials (status 401)"));
                });

        verify(monitor).warning(contains("Policy Hub import of '%s' failed: Policy Hub rejected the credentials (status 401)".formatted(HUB_POLICY_ID)));
    }

    @Test
    void syncPolicies_whenHubRejectsCredentials_shouldAnswerBadGateway() {
        when(syncService.sync(PolicyHubSyncService.Trigger.MANUAL)).thenReturn(new SyncSummary(0, 0, 0, 0,
                new Abort(AbortCause.ACCESS_DENIED, "status 403")));

        assertThatThrownBy(controller::syncPolicies)
                .isInstanceOfSatisfying(ServerErrorException.class, e -> {
                    assertThat(e.getResponse().getStatus()).isEqualTo(BAD_GATEWAY.getStatusCode());
                    assertThat(e.getResponse().getEntity()).asList().singleElement()
                            .isInstanceOfSatisfying(ApiErrorDetail.class, detail ->
                                    assertThat(detail.getMessage()).contains("rejected the credentials (status 403)"));
                });
    }

    @Test
    void importPolicy_whenImportSucceeds_shouldReturnIdAndPolicyHubName() {
        var definition = PolicyDefinition.Builder.newInstance()
                .id(HUB_POLICY_ID)
                .policy(Policy.Builder.newInstance().build())
                .privateProperty(PolicyHubImportService.POLICY_HUB_NAME_PROPERTY, "my-policy")
                .build();
        when(importService.importPolicy(HUB_POLICY_ID)).thenReturn(ServiceResult.success(definition));
        when(transformerRegistry.transform(any(), eq(JsonObject.class)))
                .thenReturn(Result.success(Json.createObjectBuilder().add("@id", HUB_POLICY_ID).build()));

        var response = controller.importPolicy(HUB_POLICY_ID);

        assertThat(response.getString("@id")).isEqualTo(HUB_POLICY_ID);
        assertThat(response.getString(PolicyHubImportService.POLICY_HUB_NAME_PROPERTY)).isEqualTo("my-policy");
        verify(monitor, never()).warning(any(String.class));
    }

    @Test
    void syncPolicies_whenRunCompleted_shouldReturnSummary() {
        when(syncService.sync(PolicyHubSyncService.Trigger.MANUAL)).thenReturn(new SyncSummary(3, 1, 1, 1, null));

        var response = controller.syncPolicies();

        assertThat(response.getInt(EDC_NAMESPACE + "checked")).isEqualTo(3);
        assertThat(response.getInt(EDC_NAMESPACE + "updated")).isEqualTo(1);
        assertThat(response.getInt(EDC_NAMESPACE + "unchanged")).isEqualTo(1);
        assertThat(response.getInt(EDC_NAMESPACE + "failed")).isEqualTo(1);
    }

    @Test
    void syncPolicies_whenHubUnreachable_shouldAnswerServiceUnavailable() {
        when(syncService.sync(PolicyHubSyncService.Trigger.MANUAL))
                .thenReturn(new SyncSummary(1, 0, 1, 0, new Abort(AbortCause.UNREACHABLE, "Connection refused")));

        assertThatThrownBy(controller::syncPolicies)
                .isInstanceOfSatisfying(ServiceUnavailableException.class, e -> {
                    assertThat(e.getResponse().getStatus()).isEqualTo(SERVICE_UNAVAILABLE.getStatusCode());
                    assertThat(List.copyOf((List<?>) e.getResponse().getEntity())).singleElement()
                            .isInstanceOfSatisfying(ApiErrorDetail.class, detail ->
                                    assertThat(detail.getMessage()).contains("Connection refused").contains("1 policies checked"));
                });
    }
}
