/*
 * Copyright (c) 2024 T-Systems International GmbH
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

package org.constructx.edc.policy.constructx.common;

import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.eclipse.edc.policy.engine.spi.DynamicAtomicConstraintRuleFunction;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.spi.result.Result;

import java.util.Collection;
import java.util.List;

/**
 * This is a base class for dynamically bound Construct-X policy evaluation functions that implements some basic common functionality and defines some
 * common constants
 */
public abstract class AbstractDynamicCredentialConstraintFunction<C extends ParticipantAgentPolicyContext> implements DynamicAtomicConstraintRuleFunction<Permission, C> {
    /**
     * Verifiable credential key to extract values
     */
    public static final String VC_CLAIM = "vc";

    /**
     * Credential Literal used to identify credentials and extract from credentialScopeExtractor
     */
    public static final String CREDENTIAL_LITERAL = "Credential";

    /**
     * Expected ODRL operators to check fx policy
     */
    protected Collection<Operator> allowedOperators() {
        return List.of(Operator.EQ, Operator.NEQ);
    }

    /**
     * extracts participant agent from the policy context.
     *
     * @param context policy context from which participant is extracted.
     * @return participant agent needed to validate policies.
     */
    protected Result<ParticipantAgent> extractParticipantAgent(C context) {
        // make sure the ParticipantAgent is there
        var participantAgent = context.participantAgent();
        if (participantAgent == null) {
            return Result.failure("Required PolicyContext data not found: " + ParticipantAgent.class.getName());
        }
        return Result.success(participantAgent);
    }

    /**
     * Extracts a {@link List} of {@link VerifiableCredential} objects from the {@link ParticipantAgent}. Credentials must be
     * stored in the agent's claims map using the "vc" key.
     *
     * @param agent participantAgent which contains information on the participant.
     * @return list of verifiable credentials extracted from the participant agent
     */
    protected Result<List<VerifiableCredential>> getCredentialList(ParticipantAgent agent) {
        var vcListClaim = agent.getClaims().get(VC_CLAIM);

        if (vcListClaim == null) {
            return Result.failure("ParticipantAgent did not contain a '%s' claim.".formatted(VC_CLAIM));
        }
        if (!(vcListClaim instanceof List)) {
            return Result.failure("ParticipantAgent contains a '%s' claim, but the type is incorrect. Expected %s, received %s.".formatted(VC_CLAIM, List.class.getName(), vcListClaim.getClass().getName()));
        }
        var vcList = (List<VerifiableCredential>) vcListClaim;
        if (vcList.isEmpty()) {
            return Result.failure("ParticipantAgent contains a '%s' claim but it did not contain any VerifiableCredentials.".formatted(VC_CLAIM));
        }
        return Result.success(vcList);
    }
}
