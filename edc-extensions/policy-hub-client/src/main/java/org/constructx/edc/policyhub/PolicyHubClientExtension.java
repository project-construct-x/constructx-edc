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

import org.constructx.edc.policyhub.api.PolicyHubApiController;
import org.eclipse.edc.connector.controlplane.services.spi.policydefinition.PolicyDefinitionService;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.participantcontext.single.spi.SingleParticipantContextSupplier;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.web.jersey.providers.jsonld.JerseyJsonLdInterceptor;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;

import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;

/**
 * Registers the Policy Hub import endpoint on the Management API. If no Policy Hub URL is
 * configured, the extension stays passive, so consumer-side deployments of the same distribution
 * boot without any Policy Hub settings.
 */
@Extension(value = PolicyHubClientExtension.NAME)
public class PolicyHubClientExtension implements ServiceExtension {

    public static final String NAME = "Construct-X Policy Hub Client";

    private static final String DEFAULT_CREDENTIALS_ALIAS = "policy-hub-credentials";

    @Setting(description = "Base URL of the Construct-X Policy Hub. If not set, the Policy Hub import API is not registered.",
            key = "constructx.policyhub.url", required = false)
    private String policyHubUrl;

    @Setting(description = "Vault alias under which the Policy Hub basic-auth credentials are stored in 'user:password' format",
            key = "constructx.policyhub.credentials.alias", defaultValue = DEFAULT_CREDENTIALS_ALIAS)
    private String credentialsAlias;

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

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();
        if (policyHubUrl == null) {
            monitor.info("No Policy Hub URL configured ('constructx.policyhub.url'), the Policy Hub import API is disabled");
            return;
        }

        var managementTransformerRegistry = transformerRegistry.forContext("management-api");
        var client = new PolicyHubClient(httpClient, policyHubUrl, vault, credentialsAlias);
        var importService = new PolicyHubImportService(client, jsonLd, managementTransformerRegistry,
                validatorRegistry, policyDefinitionService, participantContextSupplier, monitor);

        webService.registerResource(ApiContext.MANAGEMENT,
                new PolicyHubApiController(importService, managementTransformerRegistry, monitor));
        webService.registerDynamicResource(ApiContext.MANAGEMENT, PolicyHubApiController.class,
                new JerseyJsonLdInterceptor(jsonLd, typeManager, JSON_LD, "MANAGEMENT_API"));
    }
}
