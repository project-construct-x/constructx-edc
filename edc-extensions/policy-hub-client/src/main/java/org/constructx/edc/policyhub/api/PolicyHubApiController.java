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
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import org.constructx.edc.policyhub.PolicyHubImportService;
import org.constructx.edc.policyhub.PolicyHubSyncService;
import org.eclipse.edc.api.model.IdResponse;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.web.spi.exception.ServiceResultHandler.exceptionMapper;

/**
 * Management API endpoints that pull policies from the Construct-X Policy Hub and store them as
 * local policy definitions.
 */
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path("/v1alpha/policyhub")
public class PolicyHubApiController {

    private final PolicyHubImportService importService;
    private final PolicyHubSyncService syncService;
    private final TypeTransformerRegistry transformerRegistry;
    private final Monitor monitor;

    public PolicyHubApiController(PolicyHubImportService importService, PolicyHubSyncService syncService,
                                  TypeTransformerRegistry transformerRegistry, Monitor monitor) {
        this.importService = importService;
        this.syncService = syncService;
        this.transformerRegistry = transformerRegistry;
        this.monitor = monitor;
    }

    /**
     * Re-imports every policy definition imported from the Policy Hub right away, the same way the
     * periodic sync does. Runs synchronously.
     *
     * @return the counts of checked, updated, unchanged and failed policy definitions
     */
    @POST
    @Path("/policies/sync")
    public JsonObject syncPolicies() {
        var summary = syncService.sync(PolicyHubSyncService.Trigger.MANUAL);
        return Json.createObjectBuilder()
                .add(TYPE, EDC_NAMESPACE + "PolicyHubSyncSummary")
                .add(EDC_NAMESPACE + "checked", summary.checked())
                .add(EDC_NAMESPACE + "updated", summary.updated())
                .add(EDC_NAMESPACE + "unchanged", summary.unchanged())
                .add(EDC_NAMESPACE + "failed", summary.failed())
                .build();
    }

    /**
     * Imports the given Policy Hub policy as a local policy definition. Repeating the import
     * updates the existing definition.
     *
     * @param hubPolicyId the id of the policy in the Policy Hub
     * @return an {@link IdResponse} carrying the id of the imported policy definition (the Policy
     *         Hub's UUID), plus a {@code policyHubName} field carrying the Policy Hub's own
     *         human-readable policy id
     */
    @POST
    @Path("/policies/{hubPolicyId}/import")
    public JsonObject importPolicy(@PathParam("hubPolicyId") String hubPolicyId) {
        var definition = importService.importPolicy(hubPolicyId)
                .orElseThrow(exceptionMapper(PolicyDefinition.class, hubPolicyId));

        monitor.debug("Policy definition '%s' imported from the Policy Hub".formatted(definition.getId()));

        var response = IdResponse.Builder.newInstance()
                .id(definition.getId())
                .createdAt(definition.getCreatedAt())
                .build();

        var json = transformerRegistry.transform(response, JsonObject.class)
                .orElseThrow(f -> new EdcException("Error creating response body: " + f.getFailureDetail()));

        var policyHubName = definition.getPrivateProperty(PolicyHubImportService.POLICY_HUB_NAME_PROPERTY);
        return Json.createObjectBuilder(json)
                .add(PolicyHubImportService.POLICY_HUB_NAME_PROPERTY, String.valueOf(policyHubName))
                .build();
    }
}
