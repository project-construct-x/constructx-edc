/********************************************************************************
 * Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 * Copyright (c) 2025 Fraunhofer-Gesellschaft zur Förderung der angewandten Forschung e.V.
 * Copyright (c) 2025 Cofinity-X GmbH
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
 ********************************************************************************/

package org.eclipse.constructx.edc.iam.dcp.scope;

import org.eclipse.edc.connector.controlplane.catalog.spi.CatalogRequestMessage;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractRequestMessage;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.protocol.TransferRequestMessage;
import org.eclipse.edc.iam.decentralizedclaims.spi.scope.ScopeExtractor;
import org.eclipse.edc.policy.context.request.spi.RequestPolicyContext;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.spi.iam.RequestContext;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.domain.message.RemoteMessage;

import java.util.Optional;
import java.util.Set;

import static java.util.Collections.emptySet;
import static org.eclipse.constructx.edc.iam.dcp.DcpConstants.POLICY_NS;

/**
 * Extract credentials from the policy constraints
 * <p>
 * Extract and map from the Credential DSL the required credential type
 */
public class CredentialScopeExtractor implements ScopeExtractor {
    public static final String SCOPE_FORMAT = "%s:%s:read";
    public static final String CREDENTIAL_FORMAT = "%sCredential";

    private static final Set<Class<? extends RemoteMessage>> SUPPORTED_MESSAGES = 
            Set.of(CatalogRequestMessage.class, ContractRequestMessage.class, TransferRequestMessage.class);
    private static final Set<String> CON_X_CREDENTIALS = Set.of(
            "MembershipCredential"
    );

    private final Monitor monitor;

    public CredentialScopeExtractor(Monitor monitor) {
        this.monitor = monitor.withPrefix(getClass().getSimpleName());
    }

    @Override
    public Set<String> extractScopes(Object leftValue, Operator operator, Object rightValue, RequestPolicyContext context) {
        var requestContext = context.requestContext();

        if (requestContext != null) {

            if (leftValue instanceof String leftOperand && this.isMessageSupported(requestContext)) {
                String namespace = "";
                if (leftOperand.startsWith(POLICY_NS)) {
                    String realValue = leftOperand.replace(POLICY_NS, "");
                    String namespaceCredType = realValue.substring(0, realValue.lastIndexOf("/"));
                    namespace = namespaceCredType.substring(0, namespaceCredType.lastIndexOf("/"));
                    leftOperand = namespaceCredType.substring(namespaceCredType.lastIndexOf("/") + 1);
                } else {
                    return emptySet();
                } 

                var credentialType = leftOperand;
                var scope = SCOPE_FORMAT.formatted(namespace, CREDENTIAL_FORMAT.formatted(capitalize(credentialType)));
                if (isSupportedScope(scope)) {
                    return Set.of(scope);
                }
                return emptySet();
            }

        } else {
            monitor.warning("RequestContext not found in the PolicyContext: scope cannot be extracted from the policy. Defaulting to empty scopes");
        }

        return emptySet();
    }

    private boolean isSupportedScope(String scope) {
        var matchedType = CON_X_CREDENTIALS.stream()
                .filter(scope::contains)
                .findFirst()
                .orElse(null);

        return matchedType != null;
    }

    private boolean isMessageSupported(RequestContext ctx) {
        return Optional.ofNullable(ctx.getMessage())
                .map(RemoteMessage::getClass)
                .map(SUPPORTED_MESSAGES::contains)
                .orElse(false);
    }

    private String capitalize(String input) {
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }
}
