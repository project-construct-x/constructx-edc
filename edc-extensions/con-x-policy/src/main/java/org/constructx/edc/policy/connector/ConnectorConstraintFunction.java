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
import org.eclipse.tractusx.edc.spi.identity.mapper.BdrsClient;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.eclipse.tractusx.edc.spi.identity.mapper.BdrsConstants.DID_PREFIX;

/**
 * Evaluates the Construct-X Policy Hub's "Participant selection" constraint: grants access only to participants
 * whose DID is contained in a comma-separated list of {@code did:...} values.
 * <p>
 * Uses EDC's own left operand {@code https://w3id.org/edc/v0.0.1/ns/connector} (does not exist upstream — no
 * {@code Conx}-prefixed counterpart to patch).
 * <p>
 * Unlike {@code BusinessPartnerGroupConstraintFunction}/{@code BusinessPartnerNumberConstraintFunction} (which
 * resolve a DID identity down to a BPN), this resolves a BPN identity up to a DID, since the right-operand values
 * here are DIDs.
 *
 * @param <C> the type of the participant agent policy context
 */
public class ConnectorConstraintFunction<C extends ParticipantAgentPolicyContext> extends ValueValidatingConstraintFunction<Permission, C> {

    public static final String CONNECTOR = CoreConstants.EDC_NAMESPACE + "connector";

    // Restricted to did:web on purpose: did:web is the only DID method the Policy Hub emits and the project has
    // agreed to accept for this constraint, so a right operand using another method is rejected at policy-creation
    // time instead of silently never matching at evaluation time.
    private static final String DID_WEB_PATTERN = "did:web:.+";

    private final BdrsClient bdrsClient;

    public ConnectorConstraintFunction(BdrsClient bdrsClient) {
        super(Set.of(Operator.EQ), DID_WEB_PATTERN, true);
        this.bdrsClient = bdrsClient;
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

        var did = identity.startsWith(DID_PREFIX) ? identity : bdrsClient.resolveDid(identity);
        if (did == null) {
            context.reportProblem(format("Could not resolve a DID for identity '%s'", identity));
            return false;
        }

        var allowedDids = parseRightOperand(rightOperand, context);
        return allowedDids != null && allowedDids.contains(did);
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
