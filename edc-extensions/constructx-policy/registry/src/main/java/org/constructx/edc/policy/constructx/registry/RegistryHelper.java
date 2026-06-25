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

package org.constructx.edc.policy.constructx.registry;

import org.constructx.edc.policy.constructx.spi.ConstructxPolicy;
import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.engine.spi.RuleBindingRegistry;
import org.eclipse.edc.policy.model.Rule;
import org.eclipse.edc.spi.monitor.Monitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Applies contributed policy functions and bindings to the EDC policy engine.
 */
public class RegistryHelper {

    private final List<ConstructxPolicy> policies;

    public RegistryHelper(List<ConstructxPolicy> policies) {
        this.policies = List.copyOf(policies);
    }

    public void registerAll(PolicyEngine engine, RuleBindingRegistry bindingRegistry, Monitor monitor) {
        registerFunctions(engine, monitor);
        registerBindings(bindingRegistry);
    }

    public void registerFunctions(PolicyEngine engine, Monitor monitor) {
        functionRegistrations(monitor).forEach(registration -> registerOne(engine, registration));
    }

    public void registerBindings(RuleBindingRegistry registry) {
        bindings().forEach(binding -> applyBinding(registry, binding));
    }

    private List<PolicyFunctionRegistration<?, ?>> functionRegistrations(Monitor monitor) {
        var registrations = new ArrayList<PolicyFunctionRegistration<?, ?>>();
        policies.forEach(policy -> registrations.addAll(policy.functionRegistrations(monitor)));
        return registrations;
    }

    private List<PolicyBinding> bindings() {
        var bindings = new ArrayList<PolicyBinding>();
        policies.forEach(policy -> bindings.addAll(policy.bindings()));
        return bindings;
    }

    private <C extends ParticipantAgentPolicyContext, R extends Rule> void registerOne(PolicyEngine engine, PolicyFunctionRegistration<C, R> registration) {
        engine.registerFunction(registration.scope(), registration.ruleType(), registration.function());
    }

    private void applyBinding(RuleBindingRegistry registry, PolicyBinding binding) {
        if (binding instanceof PolicyBinding.DynamicPrefix dynamicPrefix) {
            registry.dynamicBind(constraintKey -> {
                if (constraintKey.startsWith(dynamicPrefix.namespace())) {
                    String credSubKey = constraintKey.substring(constraintKey.lastIndexOf("/")+1);
                    if(dynamicPrefix.credSubKeyPattern().matcher(credSubKey).matches()) {
                        return dynamicPrefix.scopes();
                    }
                }
                return Set.of();
            });
        } else if (binding instanceof PolicyBinding.StaticKey staticKey) {
            registry.bind(staticKey.key(), staticKey.scope());
        }
    }
}
