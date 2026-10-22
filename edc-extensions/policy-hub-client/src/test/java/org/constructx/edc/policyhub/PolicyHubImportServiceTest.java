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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
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
import org.eclipse.edc.policy.model.Policy;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition.EDC_POLICY_DEFINITION_TYPE;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PolicyHubImportServiceTest {

    private static final String HUB_POLICY_ID = "00000000-0000-0000-0000-000000000002";
    private static final String PARTICIPANT_CONTEXT_ID = "did:web:provider-wallet:user:provider";
    private static final String CX_POLICY_NS = "https://w3id.org/catenax/2025/9/policy/";
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(1_760_000_000_000L), ZoneOffset.UTC);

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
                policyDefinitionService, participantContextSupplier, CLOCK, monitor);
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
        assertThat(definition.getId()).isEqualTo(HUB_POLICY_ID);
        assertThat(definition.getPrivateProperty(PolicyHubImportService.POLICY_HUB_NAME_PROPERTY))
                .isEqualTo("zugriff-konsortium-mitglieder");
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
    void importPolicy_whenHubContentUnchanged_shouldNotWrite() {
        when(client.fetchPolicyDefinition(HUB_POLICY_ID)).thenReturn(Result.success(membershipPolicy()));
        when(policyDefinitionService.create(any())).thenAnswer(a -> ServiceResult.success(a.getArgument(0)));
        var firstImport = service.importPolicy(HUB_POLICY_ID).getContent();
        when(policyDefinitionService.findById(HUB_POLICY_ID)).thenReturn(firstImport);

        var result = service.importPolicy(HUB_POLICY_ID);

        assertThat(result).isSucceeded();
        assertThat(result.getContent()).isSameAs(firstImport);
        verify(policyDefinitionService).create(any());
        verify(policyDefinitionService, never()).update(any());
    }

    @Test
    void importPolicy_whenHubContentChanged_shouldUpdateWithNewHash() {
        var stale = PolicyDefinition.Builder.newInstance()
                .id(HUB_POLICY_ID)
                .policy(Policy.Builder.newInstance().build())
                .privateProperty(PolicyHubImportService.POLICY_HUB_HASH_PROPERTY, "outdated-hash")
                .build();
        when(policyDefinitionService.findById(HUB_POLICY_ID)).thenReturn(stale);
        when(client.fetchPolicyDefinition(HUB_POLICY_ID)).thenReturn(Result.success(membershipPolicy()));
        when(policyDefinitionService.update(any())).thenAnswer(a -> ServiceResult.success(a.getArgument(0)));

        var result = service.importPolicy(HUB_POLICY_ID);

        assertThat(result).isSucceeded();
        verify(policyDefinitionService, never()).create(any());
        var captor = ArgumentCaptor.forClass(PolicyDefinition.class);
        verify(policyDefinitionService).update(captor.capture());
        assertThat(captor.getValue().getPrivateProperty(PolicyHubImportService.POLICY_HUB_HASH_PROPERTY))
                .isNotNull().isNotEqualTo("outdated-hash");
        assertThat(captor.getValue().getPrivateProperty(PolicyHubImportService.POLICY_HUB_IMPORTED_AT_PROPERTY))
                .isEqualTo(CLOCK.millis());
    }

    @Test
    void importPolicy_whenUpdateSucceeds_shouldLogUpdate() {
        when(policyDefinitionService.findById(HUB_POLICY_ID)).thenReturn(staleDefinition());
        when(client.fetchPolicyDefinition(HUB_POLICY_ID)).thenReturn(Result.success(membershipPolicy()));
        when(policyDefinitionService.update(any())).thenAnswer(a -> ServiceResult.success(a.getArgument(0)));

        service.importPolicy(HUB_POLICY_ID);

        verify(monitor).info("Updated policy definition '%s' from the Policy Hub".formatted(HUB_POLICY_ID));
    }

    @Test
    void importPolicy_whenUpdateFails_shouldNotLogUpdate() {
        when(policyDefinitionService.findById(HUB_POLICY_ID)).thenReturn(staleDefinition());
        when(client.fetchPolicyDefinition(HUB_POLICY_ID)).thenReturn(Result.success(membershipPolicy()));
        when(policyDefinitionService.update(any())).thenReturn(ServiceResult.unexpected("database down"));

        var result = service.importPolicy(HUB_POLICY_ID);

        assertThat(result).isFailed();
        verify(monitor, never()).info(startsWith("Updated policy definition"));
    }

    @Test
    void importPolicy_whenOnlyKeyOrderChanges_shouldNotWrite() {
        when(client.fetchPolicyDefinition(HUB_POLICY_ID)).thenReturn(Result.success(membershipPolicy()));
        when(policyDefinitionService.create(any())).thenAnswer(a -> ServiceResult.success(a.getArgument(0)));
        var firstImport = service.importPolicy(HUB_POLICY_ID).getContent();
        when(policyDefinitionService.findById(HUB_POLICY_ID)).thenReturn(firstImport);
        var reordered = Json.createObjectBuilder();
        membershipPolicy().entrySet().stream()
                .sorted(Map.Entry.<String, JsonValue>comparingByKey().reversed())
                .forEach(entry -> reordered.add(entry.getKey(), entry.getValue()));
        when(client.fetchPolicyDefinition(HUB_POLICY_ID)).thenReturn(Result.success(reordered.build()));

        service.importPolicy(HUB_POLICY_ID);

        verify(policyDefinitionService, never()).update(any());
    }

    @Test
    void importPolicy_whenUpdatedPolicyFailsValidation_shouldKeepExistingDefinition() {
        var existing = PolicyDefinition.Builder.newInstance()
                .id(HUB_POLICY_ID)
                .policy(Policy.Builder.newInstance().build())
                .privateProperty(PolicyHubImportService.POLICY_HUB_HASH_PROPERTY, "outdated-hash")
                .build();
        when(policyDefinitionService.findById(HUB_POLICY_ID)).thenReturn(existing);
        when(client.fetchPolicyDefinition(HUB_POLICY_ID)).thenReturn(Result.success(membershipPolicy()));
        when(validatorRegistry.validate(eq(EDC_POLICY_DEFINITION_TYPE), any()))
                .thenReturn(ValidationResult.failure(Violation.violation("invalid", "policy")));

        var result = service.importPolicy(HUB_POLICY_ID);

        assertThat(result).isFailed();
        verify(policyDefinitionService, never()).update(any());
        verify(policyDefinitionService, never()).create(any());
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
        verify(policyDefinitionService, never()).create(any());
        verify(policyDefinitionService, never()).update(any());
    }

    private static PolicyDefinition staleDefinition() {
        return PolicyDefinition.Builder.newInstance()
                .id(HUB_POLICY_ID)
                .policy(Policy.Builder.newInstance().build())
                .privateProperty(PolicyHubImportService.POLICY_HUB_HASH_PROPERTY, "outdated-hash")
                .build();
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
