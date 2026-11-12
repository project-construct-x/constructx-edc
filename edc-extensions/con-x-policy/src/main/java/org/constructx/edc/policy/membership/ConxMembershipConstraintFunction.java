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

package org.constructx.edc.policy.membership;

import de.fraunhofer.isst.edc.extension.basic_abac.dev.BasicAbacCredentialConstraintFunction;
import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.tractusx.edc.policy.cx.membership.MembershipCredentialConstraintFunction;

import static org.eclipse.tractusx.edc.edr.spi.CoreConstants.CX_POLICY_2025_09_NS;

/**
 * Construct-X evaluation of the Catena-X {@code Membership} constraint ({@code Membership eq "active"}), as emitted by
 * the Construct-X Policy Hub.
 * <p>
 * Upstream ({@link MembershipCredentialConstraintFunction}) checks for a Catena-X {@code MembershipCredential}, which
 * Construct-X participants do not hold, so the constraint could never pass. This subclass keeps the upstream left
 * operand, operator and right operand handling ({@code canHandle}, {@code validate}), so hub policies stay importable
 * unchanged, but evaluates the membership through the {@code basic-abac} extension as the credential attribute
 * {@value #CONSTRUCTX_MEMBERSHIP_ATTRIBUTE} {@code eq true}.
 * <p>
 * {@code ConstructXMembershipCredential} is the {@code basic-abac} default credential, which the consumer presents in
 * every request (also for the catalog), so the constraint works in all scopes.
 *
 * @param <C> the type of the participant agent policy context
 */
public class ConxMembershipConstraintFunction<C extends ParticipantAgentPolicyContext> extends MembershipCredentialConstraintFunction<C> {

    public static final String CONSTRUCTX_MEMBERSHIP_ATTRIBUTE =
            "https://w3id.org/constructx/credentials/v1.0/ConstructXMembershipCredential.credentialSubject.isMember";

    private final BasicAbacCredentialConstraintFunction<C> abacFunction = new BasicAbacCredentialConstraintFunction<>();

    @Override
    public boolean evaluate(Object leftOperand, Operator operator, Object rightOperand, Permission permission, C context) {
        if (!ACTIVE.equals(rightOperand)) {
            context.reportProblem("Right-operand must be equal to '%s', but was '%s'".formatted(ACTIVE, rightOperand));
            return false;
        }
        if (!(CX_POLICY_2025_09_NS + MEMBERSHIP_LITERAL).equalsIgnoreCase(String.valueOf(leftOperand))) {
            context.reportProblem("Invalid left-operand: must be 'Membership', but was '%s'".formatted(leftOperand));
            return false;
        }
        var isMember = abacFunction.evaluate(CONSTRUCTX_MEMBERSHIP_ATTRIBUTE, Operator.EQ, Boolean.TRUE, permission, context);
        if (!isMember) {
            context.reportProblem("Membership requires '%s' to be true".formatted(CONSTRUCTX_MEMBERSHIP_ATTRIBUTE));
        }
        return isMember;
    }
}
