/*
 * Copyright (c) 2026 planen-bauen 4.0 GmbH
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

package org.constructx.edc.policy.constructx.policies.membership;

import org.constructx.edc.policy.constructx.registry.PolicyBinding;
import org.constructx.edc.policy.constructx.registry.PolicyFunctionRegistration;
import org.constructx.edc.policy.constructx.spi.ConstructxPolicy;
import org.eclipse.edc.connector.controlplane.catalog.spi.policy.CatalogPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.ContractNegotiationPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.TransferProcessPolicyContext;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.spi.monitor.Monitor;

import java.util.List;
import java.util.regex.Pattern;

import static org.constructx.edc.policy.constructx.common.ConstructxPolicyConstants.CONSTRUCTX_POLICY_NS;
import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_SCHEMA;

/**
 * Registers the Construct-X membership credential constraint.
 */
public class MembershipPolicy implements ConstructxPolicy {

    public static final String NAME = "Construct-X Membership Policy";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<PolicyFunctionRegistration<?, ?>> functionRegistrations(Monitor monitor) {
        var membershipCatalogFunction = new MembershipCredentialConstraintFunction<CatalogPolicyContext>(monitor);
        var membershipNegotiationFunction = new MembershipCredentialConstraintFunction<ContractNegotiationPolicyContext>(monitor);
        var membershipTransferFunction = new MembershipCredentialConstraintFunction<TransferProcessPolicyContext>(monitor);
        return List.of(
            new PolicyFunctionRegistration<>(PolicyScopes.CATALOG_SCOPE_CLASS, Permission.class, membershipCatalogFunction),
            new PolicyFunctionRegistration<>(PolicyScopes.NEGOTIATION_SCOPE_CLASS, Permission.class, membershipNegotiationFunction),
            new PolicyFunctionRegistration<>(PolicyScopes.TRANSFER_PROCESS_SCOPE_CLASS, Permission.class, membershipTransferFunction)
        );
    }

    @Override
    public List<PolicyBinding> bindings() {
        return List.of(
            new PolicyBinding.DynamicPrefix(
                    CONSTRUCTX_POLICY_NS,
                    Pattern.compile("^\\d+\\.[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)*$"),
                    PolicyScopes.ALL_RULE_SCOPES
            ),
            new PolicyBinding.StaticKey(ODRL_SCHEMA + "use", PolicyScopes.CATALOG_SCOPE),
            new PolicyBinding.StaticKey(ODRL_SCHEMA + "use", PolicyScopes.NEGOTIATION_SCOPE),
            new PolicyBinding.StaticKey(ODRL_SCHEMA + "use", PolicyScopes.TRANSFER_PROCESS_SCOPE)
        );
    }
}
