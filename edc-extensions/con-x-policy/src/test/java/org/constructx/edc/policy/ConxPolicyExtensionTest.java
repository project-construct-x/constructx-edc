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

package org.constructx.edc.policy;

import org.constructx.edc.policy.datausage.ConxDataUsageEndDateConstraintFunction;
import org.constructx.edc.policy.datausage.DataUsageStartDateConstraintFunction;
import org.constructx.edc.policy.framework.ConxFrameworkAgreementConstraintFunction;
import org.eclipse.edc.connector.controlplane.catalog.spi.policy.CatalogPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.ContractNegotiationPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.TransferProcessPolicyContext;
import org.eclipse.edc.connector.policy.monitor.spi.PolicyMonitorContext;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.policy.engine.spi.DynamicAtomicConstraintRuleFunction;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.engine.spi.RuleBindingRegistry;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.tractusx.edc.policy.cx.framework.FrameworkAgreementConstraintFunction;
import org.eclipse.tractusx.edc.policy.cx.usage.UsagePurposeConstraintFunction;
import org.eclipse.tractusx.edc.spi.identity.mapper.BdrsClient;
import org.eclipse.tractusx.edc.validation.businesspartner.spi.store.BusinessPartnerStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition.EDC_POLICY_DEFINITION_TYPE;
import static org.eclipse.edc.connector.policy.monitor.spi.PolicyMonitorContext.POLICY_MONITOR_SCOPE;
import static org.eclipse.tractusx.edc.edr.spi.CoreConstants.CX_POLICY_2025_09_NS;
import static org.eclipse.tractusx.edc.policy.cx.common.PolicyScopes.TRANSFER_PROCESS_SCOPE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(DependencyInjectionExtension.class)
class ConxPolicyExtensionTest {

    private final PolicyEngine policyEngine = mock();
    private final RuleBindingRegistry bindingRegistry = mock();
    private final JsonObjectValidatorRegistry validatorRegistry = mock();

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        context.registerService(PolicyEngine.class, policyEngine);
        context.registerService(RuleBindingRegistry.class, bindingRegistry);
        context.registerService(JsonObjectValidatorRegistry.class, validatorRegistry);
        context.registerService(BusinessPartnerStore.class, mock());
        context.registerService(BdrsClient.class, mock());
    }

    @Test
    void prepare_shouldRegisterExactlyOnePolicyDefinitionValidator(ConxPolicyExtension extension) {
        extension.prepare();

        verify(validatorRegistry, times(1)).register(eq(EDC_POLICY_DEFINITION_TYPE), any());
    }

    @Test
    void initialize_shouldRegisterDataUsageStartDateFunctionsAndBindings(ConxPolicyExtension extension, ServiceExtensionContext context) {
        extension.initialize(context);

        var leftOperand = CX_POLICY_2025_09_NS + DataUsageStartDateConstraintFunction.DATA_USAGE_START_DATE;
        verify(policyEngine).registerFunction(eq(PolicyMonitorContext.class), eq(Permission.class), eq(leftOperand), isA(DataUsageStartDateConstraintFunction.class));
        verify(policyEngine).registerFunction(eq(TransferProcessPolicyContext.class), eq(Permission.class), eq(leftOperand), isA(DataUsageStartDateConstraintFunction.class));
        verify(bindingRegistry).bind(leftOperand, TRANSFER_PROCESS_SCOPE);
        verify(bindingRegistry).bind(leftOperand, POLICY_MONITOR_SCOPE);
    }

    @Test
    void initialize_shouldRegisterConxEndDateFunction(ConxPolicyExtension extension, ServiceExtensionContext context) {
        extension.initialize(context);

        var leftOperand = CX_POLICY_2025_09_NS + "DataUsageEndDate";
        verify(policyEngine).registerFunction(eq(PolicyMonitorContext.class), eq(Permission.class), eq(leftOperand), isA(ConxDataUsageEndDateConstraintFunction.class));
        verify(policyEngine).registerFunction(eq(TransferProcessPolicyContext.class), eq(Permission.class), eq(leftOperand), isA(ConxDataUsageEndDateConstraintFunction.class));
    }

    @Test
    void initialize_shouldRegisterUsagePurposeForCatalogContext(ConxPolicyExtension extension, ServiceExtensionContext context) {
        extension.initialize(context);

        verify(policyEngine).registerFunction(eq(CatalogPolicyContext.class), eq(Permission.class), eq(CX_POLICY_2025_09_NS + "UsagePurpose"), isA(UsagePurposeConstraintFunction.class));
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void initialize_shouldRegisterOnlyConxFrameworkAgreementFunction(ConxPolicyExtension extension, ServiceExtensionContext context) {
        extension.initialize(context);

        ArgumentCaptor<DynamicAtomicConstraintRuleFunction> captor = ArgumentCaptor.forClass(DynamicAtomicConstraintRuleFunction.class);
        verify(policyEngine, atLeastOnce()).registerFunction(any(Class.class), any(Class.class), captor.capture());

        List<DynamicAtomicConstraintRuleFunction> frameworkAgreementFunctions = captor.getAllValues().stream()
                .filter(FrameworkAgreementConstraintFunction.class::isInstance)
                .toList();
        assertThat(frameworkAgreementFunctions).hasSize(3).allMatch(ConxFrameworkAgreementConstraintFunction.class::isInstance);
        verify(policyEngine).registerFunction(eq(ContractNegotiationPolicyContext.class), eq(Permission.class), isA(ConxFrameworkAgreementConstraintFunction.class));
    }
}
