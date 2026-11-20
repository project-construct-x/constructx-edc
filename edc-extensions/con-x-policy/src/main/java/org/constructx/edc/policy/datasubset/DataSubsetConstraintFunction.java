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

package org.constructx.edc.policy.datasubset;

import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.tractusx.edc.policy.cx.common.ValueValidatingConstraintFunction;

import java.util.Set;

/**
 * Placeholder constraint function for the Construct-X Policy Hub's {@code DataSubset} constraint, which restricts an
 * offer to a subset of the data, e.g. {@code field:materialName}, {@code attribute:color},
 * {@code submodel:urn:samm:com.example.material:1.0.0#Material} or {@code record:2027-Q1}.
 * <p>
 * Like {@code UsagePurposeConstraintFunction}, evaluation always passes: the connector cannot filter the data itself,
 * it only carries the restriction in the offer and the contract agreement, and the provider's backend enforces it.
 * The right operand is validated strictly when a policy definition is created: operator {@code isAnyOf}, a list of
 * unique values, each prefixed with {@code field:}, {@code attribute:}, {@code submodel:} or {@code record:}.
 * <p>
 * Does not exist upstream; the left operand lives in the Construct-X namespace, not the Catena-X one.
 *
 * @param <C> the type of the participant agent policy context
 */
public class DataSubsetConstraintFunction<C extends ParticipantAgentPolicyContext> extends ValueValidatingConstraintFunction<Permission, C> {

    public static final String CONSTRUCT_X_POLICY_NS = "https://w3id.org/construct-x/policy/";
    public static final String DATA_SUBSET = CONSTRUCT_X_POLICY_NS + "DataSubset";

    private static final String DATA_SUBSET_VALUE_PATTERN = "(field|attribute|submodel|record):.+";

    public DataSubsetConstraintFunction() {
        super(Set.of(Operator.IS_ANY_OF), DATA_SUBSET_VALUE_PATTERN, true);
    }

    @Override
    public boolean evaluate(Operator operator, Object rightOperand, Permission permission, C context) {
        return true;
    }
}
