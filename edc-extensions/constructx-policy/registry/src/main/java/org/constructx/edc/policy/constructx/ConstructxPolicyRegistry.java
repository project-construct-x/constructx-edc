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

package org.constructx.edc.policy.constructx;

import org.constructx.edc.policy.constructx.policies.membership.MembershipPolicy;
import org.constructx.edc.policy.constructx.registry.RegistryHelper;
import org.constructx.edc.policy.constructx.spi.ConstructxPolicy;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.engine.spi.RuleBindingRegistry;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.util.List;

/**
 * Central Construct-X policy registry extension.
 *
 * Registers all {@link ConstructxPolicy} implementations with the EDC policy engine.
 * To add a new policy, implement {@link ConstructxPolicy} and append it to
 * {@link #POLICIES}.
 */
@Extension(ConstructxPolicyRegistry.NAME)
public class ConstructxPolicyRegistry implements ServiceExtension {

    public static final String NAME = "Construct-X Policy Registry";

    /**
     * Add new Construct-X policies here.
     */
    private static final List<ConstructxPolicy> POLICIES = List.of(
        new MembershipPolicy()
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
        new RegistryHelper(POLICIES).registerAll(policyEngine, bindingRegistry, monitor);
    }
}
