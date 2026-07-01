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

package org.constructx.edc.policy.evaluation.registry.spi;

import org.constructx.edc.policy.evaluation.registry.ConstructxPolicyEvaluationRegistry;
import org.constructx.edc.policy.evaluation.registry.registry.PolicyEvaluationBinding;
import org.constructx.edc.policy.evaluation.registry.registry.PolicyFunctionRegistration;
import org.eclipse.edc.spi.monitor.Monitor;

import java.util.List;

/**
 * Contributes Construct-X policy evaluation -- functions and bindings to the central registry.
 * <p>
 * Add a new policy evaluation by implementing this interface and registering it in
 * {@link ConstructxPolicyEvaluationRegistry}.
 */
public interface ConstructxPolicyEvaluation {

    String name();

    List<PolicyFunctionRegistration<?, ?>> functionRegistrations(Monitor monitor);

    List<PolicyEvaluationBinding> bindings();
}
