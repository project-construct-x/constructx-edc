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

import org.constructx.edc.policyhub.client.PolicyHubUnavailableException;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.services.spi.policydefinition.PolicyDefinitionService;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.query.SortOrder;
import org.eclipse.edc.spi.result.ServiceResult;
import org.jetbrains.annotations.Nullable;

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
 *
 * <p>If the hub as a whole is unreachable, a scheduled run retries the same policy with a
 * doubling pause until {@link OutageRetry#maxWait()} is used up, then aborts. A manual run aborts
 * right away, since it blocks an HTTP request. Either way the remaining policies are not requested.
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
     *
     * @param checked    policy definitions processed, i.e. updated + unchanged + failed
     * @param aborted    whether the run was aborted because the Policy Hub was unreachable
     * @param abortReason the cause of the abort, e.g. {@code Connection refused}; {@code null} if
     *                   not aborted
     */
    public record SyncSummary(int checked, int updated, int unchanged, int failed,
                              boolean aborted, @Nullable String abortReason) {
    }

    /**
     * How a scheduled run waits for an unreachable Policy Hub: first pause, doubled on every
     * further retry, until the pauses add up to {@code maxWait}.
     */
    public record OutageRetry(Duration initialDelay, Duration maxWait) {
    }

    /**
     * Waits for the given duration; replaced in tests so they do not actually wait.
     */
    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    static final int PAGE_SIZE = 100;

    private final PolicyDefinitionService policyDefinitionService;
    private final PolicyHubImportService importService;
    private final Duration requestDelay;
    private final OutageRetry outageRetry;
    private final Sleeper sleeper;
    private final Monitor monitor;

    public PolicyHubSyncService(PolicyDefinitionService policyDefinitionService, PolicyHubImportService importService,
                                Duration requestDelay, OutageRetry outageRetry, Monitor monitor) {
        this(policyDefinitionService, importService, requestDelay, outageRetry, duration -> Thread.sleep(duration.toMillis()), monitor);
    }

    PolicyHubSyncService(PolicyDefinitionService policyDefinitionService, PolicyHubImportService importService,
                         Duration requestDelay, OutageRetry outageRetry, Sleeper sleeper, Monitor monitor) {
        this.policyDefinitionService = policyDefinitionService;
        this.importService = importService;
        this.requestDelay = requestDelay;
        this.outageRetry = outageRetry;
        this.sleeper = sleeper;
        this.monitor = monitor;
    }

    /**
     * Runs one sync over all imported policy definitions.
     *
     * @param trigger what started the run; part of the log output, so scheduled and manual runs can
     *                be monitored separately. Only scheduled runs wait for an unreachable hub.
     * @return counts of the checked, updated, unchanged and failed definitions, and whether the run
     *         was aborted because the Policy Hub was unreachable
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

            var attempt = importWithRetry(definition.getId(), trigger, label);
            if (attempt.outage() != null) {
                var checked = updated + unchanged + failed;
                var unreachableFor = attempt.waited().isZero() ? "" : " for %d s".formatted(attempt.waited().toSeconds());
                monitor.warning("Policy Hub sync (%s) aborted: Policy Hub unreachable%s (%s), %d of %d policies checked"
                        .formatted(label, unreachableFor, attempt.outage().getReason(), checked, imported.size()));
                return new SyncSummary(checked, updated, unchanged, failed, true, attempt.outage().getReason());
            }

            var result = attempt.result();
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

            pause(requestDelay);
        }

        var summary = new SyncSummary(updated + unchanged + failed, updated, unchanged, failed, false, null);
        monitor.info("Policy Hub sync (%s) finished: %d checked, %d updated, %d unchanged, %d failed"
                .formatted(label, summary.checked(), summary.updated(), summary.unchanged(), summary.failed()));
        return summary;
    }

    /**
     * Imports one policy. If the hub is unreachable, a scheduled run waits and tries again with a
     * doubling pause; the last pause is cut short so the pauses never exceed the retry budget.
     */
    private Attempt importWithRetry(String hubPolicyId, Trigger trigger, String label) {
        var waited = Duration.ZERO;
        var delay = outageRetry.initialDelay();
        var retries = 0;
        while (true) {
            try {
                var result = importService.importPolicy(hubPolicyId);
                if (retries > 0) {
                    monitor.info("Policy Hub sync (%s): Policy Hub reachable again after %d %s"
                            .formatted(label, retries, retries == 1 ? "retry" : "retries"));
                }
                return new Attempt(result, null, waited);
            } catch (PolicyHubUnavailableException e) {
                var remaining = outageRetry.maxWait().minus(waited);
                if (trigger == Trigger.MANUAL || remaining.isZero() || remaining.isNegative()) {
                    return new Attempt(null, e, waited);
                }
                var wait = delay.compareTo(remaining) > 0 ? remaining : delay;
                retries++;
                monitor.warning("Policy Hub sync (%s): Policy Hub unreachable (%s), retry %d in %d s"
                        .formatted(label, e.getReason(), retries, wait.toSeconds()));
                if (!pause(wait)) {
                    return new Attempt(null, e, waited);
                }
                waited = waited.plus(wait);
                delay = delay.multipliedBy(2);
            }
        }
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

    /**
     * Waits for the given duration.
     *
     * @return {@code false} if the wait was interrupted, i.e. the runtime is shutting down
     */
    private boolean pause(Duration duration) {
        if (duration.isZero()) {
            return true;
        }
        try {
            sleeper.sleep(duration);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Result of importing one policy: either the import's own result, or the outage that made the
     * run give up, plus how long it waited for the hub.
     */
    private record Attempt(@Nullable ServiceResult<PolicyDefinition> result,
                           @Nullable PolicyHubUnavailableException outage, Duration waited) {
    }
}
