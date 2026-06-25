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

package org.constructx.edc.policy.constructx.spi;

import org.constructx.edc.policy.constructx.registry.PolicyBinding;
import org.constructx.edc.policy.constructx.registry.PolicyFunctionRegistration;
import org.eclipse.edc.spi.monitor.Monitor;

import java.util.List;

/**
 * Contributes Construct-X policy functions and bindings to the central registry.
 * <p>
 * Add a new policy by implementing this interface and registering it in
 * {@link org.constructx.edc.policy.constructx.ConstructxPolicyRegistry}.
 */
public interface ConstructxPolicy {

    String name();

    List<PolicyFunctionRegistration<?, ?>> functionRegistrations(Monitor monitor);

    List<PolicyBinding> bindings();
}
