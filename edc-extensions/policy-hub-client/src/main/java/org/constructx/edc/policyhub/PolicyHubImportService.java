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

import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.services.spi.policydefinition.PolicyDefinitionService;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.participantcontext.single.spi.SingleParticipantContextSupplier;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.ServiceFailure;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;

import static org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition.EDC_POLICY_DEFINITION_TYPE;

/**
 * Imports ODRL policy definitions from the Construct-X Policy Hub into the local
 * {@link PolicyDefinitionService}. Deliberately reuses the exact JSON-LD expansion, validation
 * and transformation steps that the Management API's policy definition endpoint applies, so an
 * imported policy behaves identically to one created via {@code POST /v3/policydefinitions}.
 */
public class PolicyHubImportService {

    private final PolicyHubClient client;
    private final JsonLd jsonLd;
    private final TypeTransformerRegistry transformerRegistry;
    private final JsonObjectValidatorRegistry validatorRegistry;
    private final PolicyDefinitionService policyDefinitionService;
    private final SingleParticipantContextSupplier participantContextSupplier;
    private final Monitor monitor;

    public PolicyHubImportService(PolicyHubClient client, JsonLd jsonLd, TypeTransformerRegistry transformerRegistry,
                                  JsonObjectValidatorRegistry validatorRegistry, PolicyDefinitionService policyDefinitionService,
                                  SingleParticipantContextSupplier participantContextSupplier, Monitor monitor) {
        this.client = client;
        this.jsonLd = jsonLd;
        this.transformerRegistry = transformerRegistry;
        this.validatorRegistry = validatorRegistry;
        this.policyDefinitionService = policyDefinitionService;
        this.participantContextSupplier = participantContextSupplier;
        this.monitor = monitor;
    }

    /**
     * Fetches the given policy from the Policy Hub and stores it as a local {@link PolicyDefinition}.
     * If a definition with the same id already exists, it is updated instead, so the import is
     * safe to repeat.
     *
     * @param hubPolicyId the id of the policy in the Policy Hub
     * @return the created or updated policy definition, or a failure
     */
    public ServiceResult<PolicyDefinition> importPolicy(String hubPolicyId) {
        var fetched = client.fetchPolicyDefinition(hubPolicyId);
        if (fetched.failed()) {
            return ServiceResult.badRequest("Could not fetch policy '%s' from the Policy Hub: %s"
                    .formatted(hubPolicyId, fetched.getFailureDetail()));
        }

        var expanded = jsonLd.expand(fetched.getContent());
        if (expanded.failed()) {
            return ServiceResult.badRequest("Could not expand policy '%s': %s"
                    .formatted(hubPolicyId, expanded.getFailureDetail()));
        }

        var validation = validatorRegistry.validate(EDC_POLICY_DEFINITION_TYPE, expanded.getContent());
        if (validation.failed()) {
            return ServiceResult.badRequest("Policy '%s' is not a valid policy definition: %s"
                    .formatted(hubPolicyId, validation.getFailureDetail()));
        }

        var participantContext = participantContextSupplier.get();
        if (participantContext.failed()) {
            return ServiceResult.unexpected("Could not determine the participant context: %s"
                    .formatted(participantContext.getFailureDetail()));
        }

        var transformed = transformerRegistry.transform(expanded.getContent(), PolicyDefinition.class);
        if (transformed.failed()) {
            return ServiceResult.badRequest("Could not transform policy '%s': %s"
                    .formatted(hubPolicyId, transformed.getFailureDetail()));
        }

        var definition = transformed.getContent().toBuilder()
                .participantContextId(participantContext.getContent().getParticipantContextId())
                .build();

        var created = policyDefinitionService.create(definition);
        if (created.succeeded()) {
            monitor.info("Imported policy definition '%s' from the Policy Hub".formatted(definition.getId()));
            return created;
        }
        if (created.reason() == ServiceFailure.Reason.CONFLICT) {
            monitor.info("Policy definition '%s' already exists, updating it from the Policy Hub".formatted(definition.getId()));
            return policyDefinitionService.update(definition);
        }
        return created;
    }
}
