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

import org.constructx.edc.policyhub.api.PolicyHubApiController;
import org.constructx.edc.policyhub.client.PolicyHubClient;
import org.constructx.edc.policyhub.service.PolicyHubImportService;
import org.constructx.edc.policyhub.service.PolicyHubSyncService;
import org.eclipse.edc.connector.controlplane.services.spi.policydefinition.PolicyDefinitionService;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.participantcontext.single.spi.SingleParticipantContextSupplier;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.web.jersey.providers.jsonld.JerseyJsonLdInterceptor;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;

/**
 * Registers the Policy Hub import endpoints on the Management API and periodically re-imports
 * all policies imported from the hub, so hub-side changes reach the connector. If no Policy Hub
 * URL is configured, the extension stays passive, so consumer-side deployments of the same
 * distribution boot without any Policy Hub settings.
 */
@Extension(value = PolicyHubClientExtension.NAME)
public class PolicyHubClientExtension implements ServiceExtension {

    public static final String NAME = "Construct-X Policy Hub Client";

    private static final String DEFAULT_CREDENTIALS_ALIAS = "policy-hub-credentials";
    private static final String DEFAULT_SYNC_INTERVAL_SECONDS = "3600";
    private static final String DEFAULT_SYNC_REQUEST_DELAY_MILLIS = "100";

    @Setting(description = "Base URL of the Construct-X Policy Hub. If not set, the Policy Hub import API is not registered.",
            key = "constructx.policyhub.url", required = false)
    private String policyHubUrl;

    @Setting(description = "Vault alias under which the Policy Hub basic-auth credentials are stored in 'user:password' format",
            key = "constructx.policyhub.credentials.alias", defaultValue = DEFAULT_CREDENTIALS_ALIAS)
    private String credentialsAlias;

    @Setting(description = "Whether policies imported from the Policy Hub are periodically re-imported",
            key = "constructx.policyhub.sync.enabled", defaultValue = "true")
    private boolean syncEnabled;

    @Setting(description = "Time in seconds between the end of one Policy Hub sync run and the start of the next",
            key = "constructx.policyhub.sync.interval", defaultValue = DEFAULT_SYNC_INTERVAL_SECONDS)
    private long syncIntervalSeconds;

    @Setting(description = "Delay in seconds before the first Policy Hub sync run. If negative, a random delay between 0 and the interval is used, so that many connectors do not hit the hub at the same time.",
            key = "constructx.policyhub.sync.initial-delay", defaultValue = "-1")
    private long syncInitialDelaySeconds;

    @Setting(description = "Pause in milliseconds between two Policy Hub requests of a sync run",
            key = "constructx.policyhub.sync.request-delay", defaultValue = DEFAULT_SYNC_REQUEST_DELAY_MILLIS)
    private long syncRequestDelayMillis;

    @Inject
    private WebService webService;
    @Inject
    private EdcHttpClient httpClient;
    @Inject
    private Vault vault;
    @Inject
    private JsonLd jsonLd;
    @Inject
    private TypeManager typeManager;
    @Inject
    private TypeTransformerRegistry transformerRegistry;
    @Inject
    private JsonObjectValidatorRegistry validatorRegistry;
    @Inject
    private PolicyDefinitionService policyDefinitionService;
    @Inject
    private SingleParticipantContextSupplier participantContextSupplier;
    @Inject
    private ExecutorInstrumentation executorInstrumentation;
    @Inject
    private Clock clock;

    private Monitor monitor;
    private PolicyHubSyncService syncService;
    private ScheduledExecutorService syncExecutor;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        monitor = context.getMonitor();
        if (policyHubUrl == null) {
            monitor.info("No Policy Hub URL configured ('constructx.policyhub.url'), the Policy Hub import API is disabled");
            return;
        }

        var managementTransformerRegistry = transformerRegistry.forContext("management-api");
        var client = new PolicyHubClient(httpClient, policyHubUrl, vault, credentialsAlias);
        var importService = new PolicyHubImportService(client, jsonLd, managementTransformerRegistry,
                validatorRegistry, policyDefinitionService, participantContextSupplier, clock, monitor);
        syncService = new PolicyHubSyncService(policyDefinitionService, importService,
                Duration.ofMillis(syncRequestDelayMillis), monitor);

        webService.registerResource(ApiContext.MANAGEMENT,
                new PolicyHubApiController(importService, syncService, managementTransformerRegistry, monitor));
        webService.registerDynamicResource(ApiContext.MANAGEMENT, PolicyHubApiController.class,
                new JerseyJsonLdInterceptor(jsonLd, typeManager, JSON_LD, "MANAGEMENT_API"));
    }

    @Override
    public void start() {
        if (syncService == null) {
            return;
        }
        if (!syncEnabled) {
            monitor.info("Periodic Policy Hub sync is disabled ('constructx.policyhub.sync.enabled')");
            return;
        }

        var initialDelay = syncInitialDelaySeconds >= 0
                ? syncInitialDelaySeconds
                : ThreadLocalRandom.current().nextLong(syncIntervalSeconds + 1);
        syncExecutor = executorInstrumentation.instrument(Executors.newSingleThreadScheduledExecutor(), "policy-hub-sync");
        // fixed delay rather than fixed rate: a slow run never overlaps with the next one
        syncExecutor.scheduleWithFixedDelay(this::runSync, initialDelay, syncIntervalSeconds, TimeUnit.SECONDS);
        monitor.info("Periodic Policy Hub sync scheduled every %d s, first run in %d s".formatted(syncIntervalSeconds, initialDelay));
    }

    @Override
    public void shutdown() {
        if (syncExecutor != null) {
            syncExecutor.shutdownNow();
        }
    }

    private void runSync() {
        // an exception escaping a scheduled task would silently cancel all further runs
        try {
            syncService.sync(PolicyHubSyncService.Trigger.SCHEDULED);
        } catch (Exception e) {
            monitor.severe("Policy Hub sync failed", e);
        }
    }
}
