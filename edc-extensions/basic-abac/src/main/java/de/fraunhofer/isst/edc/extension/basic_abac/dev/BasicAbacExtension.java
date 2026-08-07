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

package de.fraunhofer.isst.edc.extension.basic_abac.dev;

import org.eclipse.edc.connector.controlplane.catalog.spi.policy.CatalogPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.ContractNegotiationPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.TransferProcessPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestCatalogPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestContractNegotiationPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestTransferProcessPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.engine.spi.RuleBindingRegistry;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.util.Set;

import static de.fraunhofer.isst.edc.extension.basic_abac.dev.BasicAbacUtils.BASIC_ABAC_PATTERN;
import static org.eclipse.edc.connector.controlplane.catalog.spi.policy.CatalogPolicyContext.CATALOG_SCOPE;
import static org.eclipse.edc.connector.controlplane.contract.spi.policy.ContractNegotiationPolicyContext.NEGOTIATION_SCOPE;
import static org.eclipse.edc.connector.controlplane.contract.spi.policy.TransferProcessPolicyContext.TRANSFER_SCOPE;
import static org.eclipse.edc.policy.context.request.spi.RequestCatalogPolicyContext.CATALOGING_REQUEST_SCOPE;
import static org.eclipse.edc.policy.context.request.spi.RequestContractNegotiationPolicyContext.CONTRACT_NEGOTIATION_REQUEST_SCOPE;
import static org.eclipse.edc.policy.context.request.spi.RequestTransferProcessPolicyContext.TRANSFER_PROCESS_REQUEST_SCOPE;
import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_SCHEMA;


@Extension("Basic Abac Extension")
public class BasicAbacExtension implements ServiceExtension {

    @Inject
    private PolicyEngine policyEngine;

    @Inject
    private RuleBindingRegistry ruleBindingRegistry;


    @Override
    public String name() {
        return "Basic Abac Extension";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();
        policyEngine.registerPostValidator(RequestCatalogPolicyContext.class, new BasicAbacPolicyPostValidator<>(monitor));
        policyEngine.registerPostValidator(RequestContractNegotiationPolicyContext.class, new BasicAbacPolicyPostValidator<>(monitor));
        policyEngine.registerPostValidator(RequestTransferProcessPolicyContext.class, new BasicAbacPolicyPostValidator<>(monitor));

        for (var clazz : new Class[]{
                CatalogPolicyContext.class,
                ContractNegotiationPolicyContext.class,
                TransferProcessPolicyContext.class}) {

            policyEngine.registerFunction(clazz, Permission.class, new BasicAbacCredentialConstraintFunction<>());
        }

        ruleBindingRegistry.dynamicBind(str -> {
            if (BASIC_ABAC_PATTERN.matcher(str).matches()) {
                return Set.of(
                        CATALOGING_REQUEST_SCOPE,
                        CONTRACT_NEGOTIATION_REQUEST_SCOPE,
                        TRANSFER_PROCESS_REQUEST_SCOPE,
                        CATALOG_SCOPE,
                        NEGOTIATION_SCOPE,
                        TRANSFER_SCOPE
                );
            }
            return Set.of();
        });

        String ODRL_USE = ODRL_SCHEMA + "use";
        ruleBindingRegistry.bind(ODRL_USE, CATALOG_SCOPE);
        ruleBindingRegistry.bind(ODRL_USE, NEGOTIATION_SCOPE);
        ruleBindingRegistry.bind(ODRL_USE, TRANSFER_SCOPE);
    }
}
