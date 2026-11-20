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
import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.Response;
import org.constructx.edc.policyhub.client.PolicyHubAccessDeniedException;
import org.constructx.edc.policyhub.client.PolicyHubUnavailableException;
import org.constructx.edc.policyhub.service.PolicyHubImportService;
import org.constructx.edc.policyhub.service.PolicyHubSyncService;
import org.eclipse.edc.api.model.IdResponse;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.web.spi.ApiErrorDetail;

import java.util.List;

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
     * @throws ServiceUnavailableException {@code 503} if the Policy Hub is unreachable; the run is
     *         aborted right away, without waiting for the hub
     * @throws ServerErrorException {@code 502} if the Policy Hub rejects the connector's credentials
     */
    @POST
    @Path("/policies/sync")
    public JsonObject syncPolicies() {
        var summary = syncService.sync(PolicyHubSyncService.Trigger.MANUAL);
        if (summary.aborted()) {
            var abort = summary.abort();
            if (abort.cause() == PolicyHubSyncService.AbortCause.ACCESS_DENIED) {
                throw badGateway("Policy Hub sync aborted: Policy Hub rejected the credentials (%s), %d policies checked before; check the Policy Hub credentials in the vault"
                        .formatted(abort.reason(), summary.checked()));
            }
            throw serviceUnavailable("Policy Hub sync aborted: Policy Hub unreachable (%s), %d policies checked before"
                    .formatted(abort.reason(), summary.checked()));
        }
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
     * @throws ServiceUnavailableException {@code 503} if the Policy Hub is unreachable
     * @throws ServerErrorException {@code 502} if the Policy Hub rejects the connector's credentials
     */
    @POST
    @Path("/policies/{hubPolicyId}/import")
    public JsonObject importPolicy(@PathParam("hubPolicyId") String hubPolicyId) {
        ServiceResult<PolicyDefinition> result;
        try {
            result = importService.importPolicy(hubPolicyId);
        } catch (PolicyHubUnavailableException e) {
            monitor.warning("Policy Hub import of '%s' failed: %s".formatted(hubPolicyId, e.getMessage()));
            throw serviceUnavailable("Could not import policy '%s': %s".formatted(hubPolicyId, e.getMessage()));
        } catch (PolicyHubAccessDeniedException e) {
            monitor.warning("Policy Hub import of '%s' failed: %s".formatted(hubPolicyId, e.getMessage()));
            throw badGateway("Could not import policy '%s': %s".formatted(hubPolicyId, e.getMessage()));
        }
        if (result.failed()) {
            // the sync logs its failures itself; here the reason would otherwise only reach the HTTP client
            monitor.warning("Policy Hub import of '%s' failed: %s".formatted(hubPolicyId, result.getFailureDetail()));
        }
        var definition = result.orElseThrow(exceptionMapper(PolicyDefinition.class, hubPolicyId));

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

    private static ServiceUnavailableException serviceUnavailable(String message) {
        return new ServiceUnavailableException(errorResponse(Response.Status.SERVICE_UNAVAILABLE, "ServiceUnavailable", message));
    }

    /**
     * The hub rejected the connector's credentials: not a mistake of the caller of this API, but a
     * problem between this connector and the hub, hence {@code 502} rather than a 4xx.
     */
    private static ServerErrorException badGateway(String message) {
        return new ServerErrorException(errorResponse(Response.Status.BAD_GATEWAY, "BadGateway", message));
    }

    /**
     * Builds a response with the same error body as the Management API's other errors. EDC's
     * exception mappers pass a {@link jakarta.ws.rs.WebApplicationException}'s response through
     * unchanged.
     */
    private static Response errorResponse(Response.Status status, String type, String message) {
        var error = ApiErrorDetail.Builder.newInstance()
                .message(message)
                .type(type)
                .build();
        return Response.status(status)
                .type(APPLICATION_JSON)
                .entity(List.of(error))
                .build();
    }
}
