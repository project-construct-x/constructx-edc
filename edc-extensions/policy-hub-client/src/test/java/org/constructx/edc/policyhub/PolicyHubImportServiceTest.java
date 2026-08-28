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
import jakarta.json.JsonObject;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.services.spi.policydefinition.PolicyDefinitionService;
import org.eclipse.edc.connector.controlplane.transform.edc.policy.to.JsonObjectToPolicyDefinitionTransformer;
import org.eclipse.edc.connector.controlplane.transform.odrl.OdrlTransformersFactory;
import org.eclipse.edc.jsonld.TitaniumJsonLd;
import org.eclipse.edc.participant.spi.ParticipantIdMapper;
import org.eclipse.edc.participantcontext.single.spi.SingleParticipantContextSupplier;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.policy.model.AndConstraint;
import org.eclipse.edc.policy.model.AtomicConstraint;
import org.eclipse.edc.policy.model.LiteralExpression;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.transform.TypeTransformerRegistryImpl;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.eclipse.edc.validator.spi.Violation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URISyntaxException;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition.EDC_POLICY_DEFINITION_TYPE;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PolicyHubImportServiceTest {

    private static final String HUB_POLICY_ID = "00000000-0000-0000-0000-000000000002";
    private static final String PARTICIPANT_CONTEXT_ID = "did:web:provider-wallet:user:provider";
    private static final String CX_POLICY_NS = "https://w3id.org/catenax/2025/9/policy/";

    private final PolicyHubClient client = mock();
    private final PolicyDefinitionService policyDefinitionService = mock();
    private final JsonObjectValidatorRegistry validatorRegistry = mock();
    private final Monitor monitor = mock();

    private PolicyHubImportService service;

    @BeforeEach
    void setUp() throws URISyntaxException {
        var jsonLd = new TitaniumJsonLd(monitor);
        // mirrors the runtime, where JsonLdExtension registers the same cached ODRL context
        jsonLd.registerCachedDocument("http://www.w3.org/ns/odrl.jsonld",
                Objects.requireNonNull(getClass().getClassLoader().getResource("document/odrl.jsonld")).toURI());

        TypeTransformerRegistry transformerRegistry = new TypeTransformerRegistryImpl();
        transformerRegistry.register(new JsonObjectToPolicyDefinitionTransformer());
        OdrlTransformersFactory.jsonObjectToOdrlTransformers(new IdentityParticipantIdMapper())
                .forEach(transformerRegistry::register);

        when(validatorRegistry.validate(eq(EDC_POLICY_DEFINITION_TYPE), any())).thenReturn(ValidationResult.success());

        SingleParticipantContextSupplier participantContextSupplier = () -> ServiceResult.success(
                ParticipantContext.Builder.newInstance()
                        .participantContextId(PARTICIPANT_CONTEXT_ID)
                        .identity(PARTICIPANT_CONTEXT_ID)
                        .build());

        service = new PolicyHubImportService(client, jsonLd, transformerRegistry, validatorRegistry,
                policyDefinitionService, participantContextSupplier, monitor);
    }

    @Test
    void importPolicy_whenHubReturnsMembershipPolicy_shouldCreatePolicyDefinition() {
        when(client.fetchPolicyDefinition(HUB_POLICY_ID)).thenReturn(Result.success(membershipPolicy()));
        when(policyDefinitionService.create(any())).thenAnswer(a -> ServiceResult.success(a.getArgument(0)));

        var result = service.importPolicy(HUB_POLICY_ID);

        assertThat(result).isSucceeded();
        var captor = ArgumentCaptor.forClass(PolicyDefinition.class);
        verify(policyDefinitionService).create(captor.capture());
        var definition = captor.getValue();
        assertThat(definition.getId()).isEqualTo("zugriff-konsortium-mitglieder");
        assertThat(definition.getParticipantContextId()).isEqualTo(PARTICIPANT_CONTEXT_ID);

        var permissions = definition.getPolicy().getPermissions();
        assertThat(permissions).hasSize(1);
        assertThat(permissions.get(0).getAction().getType()).isEqualTo(CX_POLICY_NS + "access");
        var andConstraint = (AndConstraint) permissions.get(0).getConstraints().get(0);
        var atomicConstraint = (AtomicConstraint) andConstraint.getConstraints().get(0);
        assertThat(((LiteralExpression) atomicConstraint.getLeftExpression()).getValue())
                .isEqualTo(CX_POLICY_NS + "Membership");
        assertThat(((LiteralExpression) atomicConstraint.getRightExpression()).getValue()).isEqualTo("active");
    }

    @Test
    void importPolicy_whenPolicyAlreadyExists_shouldUpdateDefinition() {
        when(client.fetchPolicyDefinition(HUB_POLICY_ID)).thenReturn(Result.success(membershipPolicy()));
        when(policyDefinitionService.create(any())).thenReturn(ServiceResult.conflict("already exists"));
        when(policyDefinitionService.update(any())).thenAnswer(a -> ServiceResult.success(a.getArgument(0)));

        var result = service.importPolicy(HUB_POLICY_ID);

        assertThat(result).isSucceeded();
        verify(policyDefinitionService).update(any());
    }

    @Test
    void importPolicy_whenHubUnreachable_shouldReturnFailure() {
        when(client.fetchPolicyDefinition(HUB_POLICY_ID)).thenReturn(Result.failure("connection refused"));

        var result = service.importPolicy(HUB_POLICY_ID);

        assertThat(result).isFailed();
        verifyNoInteractions(policyDefinitionService);
    }

    @Test
    void importPolicy_whenValidationFails_shouldReturnFailureWithoutCreating() {
        when(client.fetchPolicyDefinition(HUB_POLICY_ID)).thenReturn(Result.success(membershipPolicy()));
        when(validatorRegistry.validate(eq(EDC_POLICY_DEFINITION_TYPE), any()))
                .thenReturn(ValidationResult.failure(Violation.violation("invalid", "policy")));

        var result = service.importPolicy(HUB_POLICY_ID);

        assertThat(result).isFailed();
        verifyNoInteractions(policyDefinitionService);
    }

    private JsonObject membershipPolicy() {
        try (var reader = Json.createReader(Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream("membership-policy.json")))) {
            return reader.readObject();
        }
    }

    private static class IdentityParticipantIdMapper implements ParticipantIdMapper {
        @Override
        public String toIri(String participantId) {
            return participantId;
        }

        @Override
        public String fromIri(String iriParticipantId) {
            return iriParticipantId;
        }
    }
}
