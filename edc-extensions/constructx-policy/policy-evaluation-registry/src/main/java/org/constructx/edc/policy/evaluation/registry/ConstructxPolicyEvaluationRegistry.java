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

package org.constructx.edc.policy.evaluation.registry;

import org.constructx.edc.policy.evaluation.registry.evaluations.membership.MembershipPolicyEvaluation;
import org.constructx.edc.policy.evaluation.registry.registry.RegistryHelper;
import org.constructx.edc.policy.evaluation.registry.spi.ConstructxPolicyEvaluation;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.engine.spi.RuleBindingRegistry;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.util.List;

/**
 * Central Construct-X policy evaluation registry extension.
 *
 * Registers all {@link ConstructxPolicyEvaluation} implementations with the EDC policy engine.
 * To add a new policy evaluation, implement {@link ConstructxPolicyEvaluation} and append it to
 * {@link #POLICY_EVALUATIONS}.
 */
@Extension(ConstructxPolicyEvaluationRegistry.NAME)
public class ConstructxPolicyEvaluationRegistry implements ServiceExtension {

    public static final String NAME = "Construct-X Policy Evaluation Registry";

    /**
     * Add new Construct-X policies here.
     */
    private static final List<ConstructxPolicyEvaluation> POLICY_EVALUATIONS = List.of(
        new MembershipPolicyEvaluation()
    );

    @Inject
    private PolicyEngine policyEngine;

    @Inject
    private Monitor monitor;

    @Inject
    private RuleBindingRegistry bindingRegistry;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        new RegistryHelper(POLICY_EVALUATIONS).registerAll(policyEngine, bindingRegistry, monitor);
    }
}
