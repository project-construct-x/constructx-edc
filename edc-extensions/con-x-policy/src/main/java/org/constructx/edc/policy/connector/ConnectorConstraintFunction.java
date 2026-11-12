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

package org.constructx.edc.policy.connector;

import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.spi.constants.CoreConstants;
import org.eclipse.tractusx.edc.policy.cx.common.ValueValidatingConstraintFunction;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * Evaluates the Construct-X Policy Hub's "Participant selection" constraint: grants access only to participants
 * whose DID is contained in a comma-separated list of {@code did:...} values.
 * <p>
 * Uses EDC's own left operand {@code https://w3id.org/edc/v0.0.1/ns/connector} (does not exist upstream — no
 * {@code Conx}-prefixed counterpart to patch).
 * <p>
 * The participant agent's identity must itself be a DID (Construct-X uses DIDs as participant ids). There is
 * deliberately no BPN-to-DID resolution: Construct-X does not depend on the Catena-X BDRS.
 *
 * @param <C> the type of the participant agent policy context
 */
public class ConnectorConstraintFunction<C extends ParticipantAgentPolicyContext> extends ValueValidatingConstraintFunction<Permission, C> {

    public static final String CONNECTOR = CoreConstants.EDC_NAMESPACE + "connector";

    // Restricted to did:web on purpose: did:web is the only DID method the Policy Hub emits and the project has
    // agreed to accept for this constraint, so a right operand using another method is rejected at policy-creation
    // time instead of silently never matching at evaluation time.
    private static final String DID_WEB_PATTERN = "did:web:.+";
    private static final String DID_PREFIX = "did:";

    public ConnectorConstraintFunction() {
        super(Set.of(Operator.EQ), DID_WEB_PATTERN, true);
    }

    @Override
    public boolean evaluate(Operator operator, Object rightOperand, Permission permission, C context) {
        if (operator != Operator.EQ) {
            context.reportProblem(format("Operator must be '%s' but was '%s'", Operator.EQ.name(), operator.name()));
            return false;
        }

        var identity = context.participantAgent().getIdentity();
        if (identity == null) {
            context.reportProblem("Identity of the participant agent cannot be null");
            return false;
        }

        if (!identity.startsWith(DID_PREFIX)) {
            context.reportProblem(format("Identity of the participant agent must be a DID, but was '%s'", identity));
            return false;
        }

        var allowedDids = parseRightOperand(rightOperand, context);
        return allowedDids != null && allowedDids.contains(identity);
    }

    private Set<String> parseRightOperand(Object rightValue, PolicyContext context) {
        if (rightValue instanceof String value) {
            return Arrays.stream(value.split(",")).map(String::trim).collect(Collectors.toSet());
        }
        if (rightValue instanceof Collection<?> values) {
            return values.stream().map(Object::toString).map(String::trim).collect(Collectors.toSet());
        }

        context.reportProblem(format("Right operand expected to be either String or a Collection, but was %s", rightValue.getClass()));
        return null;
    }
}
