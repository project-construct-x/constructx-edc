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

package org.constructx.edc.policyhub.api;

import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import org.constructx.edc.policyhub.PolicyHubImportService;
import org.eclipse.edc.api.model.IdResponse;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.eclipse.edc.web.spi.exception.ServiceResultHandler.exceptionMapper;

/**
 * Management API endpoint that pulls a policy from the Construct-X Policy Hub and stores it as a
 * local policy definition.
 */
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path("/v1alpha/policyhub")
public class PolicyHubApiController {

    private final PolicyHubImportService importService;
    private final TypeTransformerRegistry transformerRegistry;
    private final Monitor monitor;

    public PolicyHubApiController(PolicyHubImportService importService, TypeTransformerRegistry transformerRegistry, Monitor monitor) {
        this.importService = importService;
        this.transformerRegistry = transformerRegistry;
        this.monitor = monitor;
    }

    /**
     * Imports the given Policy Hub policy as a local policy definition. Repeating the import
     * updates the existing definition.
     *
     * @param hubPolicyId the id of the policy in the Policy Hub
     * @return an {@link IdResponse} carrying the id of the imported policy definition
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

        return transformerRegistry.transform(response, JsonObject.class)
                .orElseThrow(f -> new EdcException("Error creating response body: " + f.getFailureDetail()));
    }
}
