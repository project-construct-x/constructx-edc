/*
 * Copyright (c) 2024 T-Systems International GmbH
 * Copyright (c) 2025 SAP SE
 * Copyright (c) 2026 Materna SE
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

package org.constructx.edc.policy.evaluation.registry.policy_evaluations.membership;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.constructx.edc.policy.constructx.common.AbstractDynamicCredentialConstraintFunction;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSubject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.tractusx.edc.core.utils.credentials.CredentialTypePredicate;

import java.util.List;

import static org.constructx.edc.policy.evaluation.registry.common.ConstructxPolicyEvalConstants.CONSTRUCTX_CREDENTIAL_NS;
import static org.constructx.edc.policy.evaluation.registry.common.ConstructxPolicyEvalConstants.CONSTRUCTX_POLICY_NS;


/**
 * This policy function checks that a MembershipCredential is present and evaluates it.
 * objects extracted from a {@link ParticipantAgent} which is expected to be present on the {@link ParticipantAgentPolicyContext}.
 */
public class MembershipCredentialConstraintFunction<C extends ParticipantAgentPolicyContext> extends AbstractDynamicCredentialConstraintFunction<C> {

    /**
     * key of the membership credential
     *
     * @deprecated Use {@value CONSTRUCTX_MEMBERSHIP_LITERAL} instead.
     */
    @Deprecated(since = "0.0.4", forRemoval = true)
    public static final String MEMBERSHIP_LITERAL = "Membership";

    /**
     * key for constructx-membership credential
     */
    public static final String CONSTRUCTX_MEMBERSHIP_LITERAL = "ConstructXMembership";

    private final Monitor monitor;

    public MembershipCredentialConstraintFunction(Monitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public boolean evaluate(Object leftOperand, Operator operator, Object rightOperand, Permission permission, C context) {
        if (!this.canHandle(leftOperand)) {
            context.reportProblem("Left-operand must be a valid JSON path");
            return false;
        }

        var participantAgent = extractParticipantAgent(context);
        if (participantAgent.failed()) {
            context.reportProblem(participantAgent.getFailureDetail());
            return false;
        }

        var credentialResult = getCredentialList(participantAgent.getContent());
        if (credentialResult.failed()) {
            context.reportProblem(credentialResult.getFailureDetail());
            return false;
        }

        List<VerifiableCredential> expectedVcs = credentialResult.getContent().stream()
                .filter(vc -> {
                    return
                        new CredentialTypePredicate(CONSTRUCTX_CREDENTIAL_NS, CONSTRUCTX_MEMBERSHIP_LITERAL + CREDENTIAL_LITERAL).test(vc) ||
                        new CredentialTypePredicate(CONSTRUCTX_CREDENTIAL_NS, MEMBERSHIP_LITERAL + CREDENTIAL_LITERAL).test(vc);
                }).toList();
        if (expectedVcs.isEmpty()) {
            context.reportProblem("ParticipantAgent does not contain a credential of type %s or %s".formatted(CONSTRUCTX_MEMBERSHIP_LITERAL, MEMBERSHIP_LITERAL));
            return false;
        }

        return expectedVcs.stream().anyMatch(vc -> {
            JsonMapper mapper = new JsonMapper();
            List<CredentialSubject> credentialSubject = vc.getCredentialSubject();

            String leftOperandStr = leftOperand.toString();
            String key = leftOperandStr.substring(leftOperandStr.lastIndexOf("/")).replace(".", "/");
            JsonNode vcContent = mapper.valueToTree(credentialSubject);
            JsonNode value = vcContent.at(key);

            if (operator.equals(Operator.EQ)) {
                return rightOperand.equals(value.asText());
            } else if (operator.equals(Operator.NEQ)) {
                return !rightOperand.equals(value.asText());
            } else return false;
        });
    }

    @Override
    public boolean canHandle(Object leftOperand) {
        int nameSpaceKeySeparatorIndex = leftOperand.toString().lastIndexOf("/") + 1;
        String namespace = leftOperand.toString().substring(0, nameSpaceKeySeparatorIndex);
        String key = leftOperand.toString().substring(nameSpaceKeySeparatorIndex);
        String[] keyArray = key.split("\\.");
        return
            namespace.equals(CONSTRUCTX_POLICY_NS) &&
                keyArray.length > 1 && keyArray[0].matches("^\\d+$") && !keyArray[1].isEmpty();
    }

    @Override
    public Result<Void> validate(Object leftValue, Operator operator, Object rightValue, Permission rule) {
        if (!Operator.EQ.equals(operator) && !Operator.NEQ.equals(operator)) {
            return Result.failure("Just eq or neq allowed");
        }
        if (!"true".equals(String.valueOf(rightValue))) {
            return Result.failure("rightOperand must be 'true'");
        }
        return Result.success();
    }
}
