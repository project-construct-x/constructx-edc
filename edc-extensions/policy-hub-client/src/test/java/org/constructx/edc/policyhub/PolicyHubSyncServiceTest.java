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

package org.constructx.edc.policyhub;

import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.services.spi.policydefinition.PolicyDefinitionService;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.ServiceResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.constructx.edc.policyhub.PolicyHubImportService.POLICY_HUB_HASH_PROPERTY;
import static org.constructx.edc.policyhub.PolicyHubImportService.POLICY_HUB_NAME_PROPERTY;
import static org.constructx.edc.policyhub.PolicyHubSyncService.PAGE_SIZE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyHubSyncServiceTest {

    private final PolicyDefinitionService policyDefinitionService = mock();
    private final PolicyHubImportService importService = mock();
    private final Monitor monitor = mock();

    private final PolicyHubSyncService service =
            new PolicyHubSyncService(policyDefinitionService, importService, Duration.ZERO, monitor);

    @Test
    void sync_whenDefinitionsWereImported_shouldReimportOnlyThose() {
        var imported = importedDefinition("hub-1", "hash-1");
        var manual = definition("manual").build();
        when(policyDefinitionService.search(any())).thenReturn(ServiceResult.success(List.of(imported, manual)));
        when(importService.importPolicy("hub-1")).thenReturn(ServiceResult.success(imported));

        var summary = service.sync(PolicyHubSyncService.Trigger.SCHEDULED);

        verify(importService).importPolicy("hub-1");
        verify(importService, never()).importPolicy("manual");
        assertThat(summary).isEqualTo(new PolicyHubSyncService.SyncSummary(1, 0, 1, 0));
    }

    @Test
    void sync_whenHubContentChanged_shouldCountAsUpdated() {
        var imported = importedDefinition("hub-1", "old-hash");
        when(policyDefinitionService.search(any())).thenReturn(ServiceResult.success(List.of(imported)));
        when(importService.importPolicy("hub-1")).thenReturn(ServiceResult.success(importedDefinition("hub-1", "new-hash")));

        var summary = service.sync(PolicyHubSyncService.Trigger.SCHEDULED);

        assertThat(summary).isEqualTo(new PolicyHubSyncService.SyncSummary(1, 1, 0, 0));
    }

    @Test
    void sync_whenHubReturnsNotFound_shouldKeepDefinitionAndContinue() {
        var missing = importedDefinition("hub-1", "hash-1");
        var present = importedDefinition("hub-2", "hash-2");
        when(policyDefinitionService.search(any())).thenReturn(ServiceResult.success(List.of(missing, present)));
        when(importService.importPolicy("hub-1")).thenReturn(ServiceResult.badRequest(
                "Could not fetch policy 'hub-1' from the Policy Hub: Policy Hub returned status 404 for policy 'hub-1'"));
        when(importService.importPolicy("hub-2")).thenReturn(ServiceResult.success(present));

        var summary = service.sync(PolicyHubSyncService.Trigger.SCHEDULED);

        verify(importService).importPolicy("hub-2");
        verify(policyDefinitionService, never()).deleteById(anyString());
        assertThat(summary).isEqualTo(new PolicyHubSyncService.SyncSummary(2, 0, 1, 1));
    }

    @Test
    void sync_whenMoreDefinitionsThanOnePage_shouldReadAllPages() {
        var firstPage = IntStream.range(0, PAGE_SIZE)
                .mapToObj(i -> importedDefinition("hub-" + i, "hash"))
                .toList();
        var secondPage = List.of(importedDefinition("hub-last", "hash"));
        when(policyDefinitionService.search(argThat(query -> query != null && query.getOffset() == 0)))
                .thenReturn(ServiceResult.success(firstPage));
        when(policyDefinitionService.search(argThat(query -> query != null && query.getOffset() == PAGE_SIZE)))
                .thenReturn(ServiceResult.success(secondPage));
        when(importService.importPolicy(anyString()))
                .thenAnswer(a -> ServiceResult.success(importedDefinition(a.getArgument(0), "hash")));

        var summary = service.sync(PolicyHubSyncService.Trigger.SCHEDULED);

        verify(policyDefinitionService, times(2)).search(any(QuerySpec.class));
        verify(importService).importPolicy("hub-last");
        assertThat(summary.checked()).isEqualTo(PAGE_SIZE + 1);
    }

    @Test
    void sync_whenTriggeredManually_shouldLogTriggerInSummary() {
        when(policyDefinitionService.search(any())).thenReturn(ServiceResult.success(List.of()));

        service.sync(PolicyHubSyncService.Trigger.MANUAL);

        verify(monitor).info("Policy Hub sync (manual) finished: 0 checked, 0 updated, 0 unchanged, 0 failed");
    }

    @Test
    void sync_whenListingFails_shouldImportNothing() {
        when(policyDefinitionService.search(any())).thenReturn(ServiceResult.unexpected("database down"));

        var summary = service.sync(PolicyHubSyncService.Trigger.SCHEDULED);

        verify(importService, never()).importPolicy(anyString());
        assertThat(summary.checked()).isZero();
    }

    private static PolicyDefinition importedDefinition(String id, String hash) {
        return definition(id)
                .privateProperty(POLICY_HUB_NAME_PROPERTY, "name-" + id)
                .privateProperty(POLICY_HUB_HASH_PROPERTY, hash)
                .build();
    }

    private static PolicyDefinition.Builder definition(String id) {
        return PolicyDefinition.Builder.newInstance()
                .id(id)
                .policy(Policy.Builder.newInstance().build());
    }
}
