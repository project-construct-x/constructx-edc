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

package org.constructx.edc.policyhub.service;

import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.services.spi.policydefinition.PolicyDefinitionService;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.query.SortOrder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static org.constructx.edc.policyhub.service.PolicyHubImportService.POLICY_HUB_HASH_PROPERTY;
import static org.constructx.edc.policyhub.service.PolicyHubImportService.POLICY_HUB_NAME_PROPERTY;

/**
 * Re-imports every policy definition that was imported from the Policy Hub, so changes made in
 * the hub reach the connector. Only the connector's own imports are synced (one hub request each),
 * so the cost depends on what the connector uses, not on the size of the hub.
 *
 * <p>A policy the hub no longer serves (e.g. a {@code 404}) or that fails validation is kept as it
 * is and only reported: contract definitions may still reference it.
 */
public class PolicyHubSyncService {

    /**
     * What started a sync run.
     */
    public enum Trigger {
        SCHEDULED, MANUAL
    }

    /**
     * Outcome of one sync run.
     */
    public record SyncSummary(int checked, int updated, int unchanged, int failed) {
    }

    static final int PAGE_SIZE = 100;

    private final PolicyDefinitionService policyDefinitionService;
    private final PolicyHubImportService importService;
    private final Duration requestDelay;
    private final Monitor monitor;

    public PolicyHubSyncService(PolicyDefinitionService policyDefinitionService, PolicyHubImportService importService,
                                Duration requestDelay, Monitor monitor) {
        this.policyDefinitionService = policyDefinitionService;
        this.importService = importService;
        this.requestDelay = requestDelay;
        this.monitor = monitor;
    }

    /**
     * Runs one sync over all imported policy definitions.
     *
     * @param trigger what started the run; part of the log output, so scheduled and manual runs can
     *                be monitored separately
     * @return counts of the checked, updated, unchanged and failed definitions
     */
    public SyncSummary sync(Trigger trigger) {
        var label = trigger.name().toLowerCase(Locale.ROOT);
        var imported = findImportedDefinitions();
        int updated = 0;
        int unchanged = 0;
        int failed = 0;

        for (var definition : imported) {
            if (Thread.currentThread().isInterrupted()) {
                monitor.warning("Policy Hub sync (%s) interrupted, %d of %d policies not checked"
                        .formatted(label, imported.size() - updated - unchanged - failed, imported.size()));
                break;
            }

            var result = importService.importPolicy(definition.getId());
            if (result.failed()) {
                failed++;
                monitor.warning("Policy Hub sync (%s): keeping policy definition '%s' unchanged: %s"
                        .formatted(label, definition.getId(), result.getFailureDetail()));
            } else if (Objects.equals(definition.getPrivateProperty(POLICY_HUB_HASH_PROPERTY),
                    result.getContent().getPrivateProperty(POLICY_HUB_HASH_PROPERTY))) {
                unchanged++;
            } else {
                updated++;
            }

            pause();
        }

        var summary = new SyncSummary(imported.size(), updated, unchanged, failed);
        monitor.info("Policy Hub sync (%s) finished: %d checked, %d updated, %d unchanged, %d failed"
                .formatted(label, summary.checked(), summary.updated(), summary.unchanged(), summary.failed()));
        return summary;
    }

    /**
     * Pages through all local policy definitions and keeps those imported from the Policy Hub. The
     * filter is applied in memory: the marker property's key is an IRI containing dots, which the
     * stores' property-path syntax cannot address.
     */
    private List<PolicyDefinition> findImportedDefinitions() {
        var imported = new ArrayList<PolicyDefinition>();
        var offset = 0;
        while (true) {
            var query = QuerySpec.Builder.newInstance()
                    .offset(offset)
                    .limit(PAGE_SIZE)
                    .sortField("id")
                    .sortOrder(SortOrder.ASC)
                    .build();
            var page = policyDefinitionService.search(query);
            if (page.failed()) {
                monitor.warning("Policy Hub sync: could not list policy definitions: %s".formatted(page.getFailureDetail()));
                return imported;
            }
            page.getContent().stream()
                    .filter(definition -> definition.getPrivateProperty(POLICY_HUB_NAME_PROPERTY) != null)
                    .forEach(imported::add);
            if (page.getContent().size() < PAGE_SIZE) {
                return imported;
            }
            offset += PAGE_SIZE;
        }
    }

    private void pause() {
        if (requestDelay.isZero()) {
            return;
        }
        try {
            Thread.sleep(requestDelay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
