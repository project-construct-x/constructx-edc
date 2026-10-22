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
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.participantcontext.single.spi.SingleParticipantContextSupplier;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.ServiceFailure;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;

import static org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition.EDC_POLICY_DEFINITION_TYPE;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;

/**
 * Imports ODRL policy definitions from the Construct-X Policy Hub into the local
 * {@link PolicyDefinitionService}. Deliberately reuses the exact JSON-LD expansion, validation
 * and transformation steps that the Management API's policy definition endpoint applies, so an
 * imported policy behaves identically to one created via {@code POST /v3/policydefinitions}.
 */
public class PolicyHubImportService {

    /**
     * Private property key under which the Policy Hub's own (human-readable) policy id is
     * stored, since the local {@link PolicyDefinition#getId()} is set to the Policy Hub's UUID
     * instead. Namespaced under {@code EDC_NAMESPACE} (rather than a bare "policyHubName") so it
     * survives the JSON-LD expand/compact round trip the create/update/read paths apply to
     * private properties - a bare, non-IRI key has no term mapping and is dropped during
     * expansion. Because the namespace matches the response's own "@vocab", it still compacts
     * back down to the short form "policyHubName" in the JSON output.
     */
    public static final String POLICY_HUB_NAME_PROPERTY = EDC_NAMESPACE + "policyHubName";

    /**
     * Private property key holding a SHA-256 hash of the Policy Hub's ODRL rendering at the time of
     * the last import. A repeated import (manual or by the periodic sync) with an unchanged hash
     * leaves the stored definition untouched.
     */
    public static final String POLICY_HUB_HASH_PROPERTY = EDC_NAMESPACE + "policyHubHash";

    /**
     * Private property key holding the time (epoch millis) at which the definition's content was
     * last taken over from the Policy Hub, i.e. created or updated. An unchanged re-import does not
     * touch it.
     */
    public static final String POLICY_HUB_IMPORTED_AT_PROPERTY = EDC_NAMESPACE + "policyHubImportedAt";

    private final PolicyHubClient client;
    private final JsonLd jsonLd;
    private final TypeTransformerRegistry transformerRegistry;
    private final JsonObjectValidatorRegistry validatorRegistry;
    private final PolicyDefinitionService policyDefinitionService;
    private final SingleParticipantContextSupplier participantContextSupplier;
    private final Clock clock;
    private final Monitor monitor;

    public PolicyHubImportService(PolicyHubClient client, JsonLd jsonLd, TypeTransformerRegistry transformerRegistry,
                                  JsonObjectValidatorRegistry validatorRegistry, PolicyDefinitionService policyDefinitionService,
                                  SingleParticipantContextSupplier participantContextSupplier, Clock clock, Monitor monitor) {
        this.client = client;
        this.jsonLd = jsonLd;
        this.transformerRegistry = transformerRegistry;
        this.validatorRegistry = validatorRegistry;
        this.policyDefinitionService = policyDefinitionService;
        this.participantContextSupplier = participantContextSupplier;
        this.clock = clock;
        this.monitor = monitor;
    }

    /**
     * Fetches the given policy from the Policy Hub and stores it as a local {@link PolicyDefinition}.
     * If a definition with the same id already exists, it is updated instead, so the import is
     * safe to repeat. If the hub's content is unchanged since the last import, nothing is written
     * and the existing definition is returned. On any failure the stored definition stays as it is.
     *
     * @param hubPolicyId the id of the policy in the Policy Hub
     * @return the created, updated or unchanged policy definition, or a failure
     */
    public ServiceResult<PolicyDefinition> importPolicy(String hubPolicyId) {
        var fetched = client.fetchPolicyDefinition(hubPolicyId);
        if (fetched.failed()) {
            return ServiceResult.badRequest("Could not fetch policy '%s' from the Policy Hub: %s"
                    .formatted(hubPolicyId, fetched.getFailureDetail()));
        }

        var hash = hash(fetched.getContent());
        var existing = policyDefinitionService.findById(hubPolicyId);
        if (existing != null && hash.equals(existing.getPrivateProperty(POLICY_HUB_HASH_PROPERTY))) {
            monitor.debug("Policy definition '%s' is unchanged in the Policy Hub".formatted(hubPolicyId));
            return ServiceResult.success(existing);
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

        var policyHubName = transformed.getContent().getId();
        var definition = transformed.getContent().toBuilder()
                .id(hubPolicyId)
                .privateProperty(POLICY_HUB_NAME_PROPERTY, policyHubName)
                .privateProperty(POLICY_HUB_HASH_PROPERTY, hash)
                .privateProperty(POLICY_HUB_IMPORTED_AT_PROPERTY, clock.millis())
                .participantContextId(participantContext.getContent().getParticipantContextId())
                .build();

        if (existing != null) {
            return update(definition);
        }

        var created = policyDefinitionService.create(definition);
        if (created.succeeded()) {
            monitor.info("Imported policy definition '%s' from the Policy Hub".formatted(definition.getId()));
            return created;
        }
        if (created.reason() == ServiceFailure.Reason.CONFLICT) {
            return update(definition);
        }
        return created;
    }

    private ServiceResult<PolicyDefinition> update(PolicyDefinition definition) {
        var updated = policyDefinitionService.update(definition);
        if (updated.succeeded()) {
            monitor.info("Updated policy definition '%s' from the Policy Hub".formatted(definition.getId()));
        }
        return updated;
    }

    /**
     * Hashes the hub's response in a canonical form (object keys sorted), so a mere reordering of
     * keys on the hub side does not count as a change.
     */
    private static String hash(JsonObject policy) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalize(policy).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String canonicalize(JsonValue value) {
        return switch (value.getValueType()) {
            case OBJECT -> value.asJsonObject().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> Json.createValue(entry.getKey()) + ":" + canonicalize(entry.getValue()))
                    .collect(Collectors.joining(",", "{", "}"));
            case ARRAY -> value.asJsonArray().stream()
                    .map(PolicyHubImportService::canonicalize)
                    .collect(Collectors.joining(",", "[", "]"));
            default -> value.toString();
        };
    }
}
