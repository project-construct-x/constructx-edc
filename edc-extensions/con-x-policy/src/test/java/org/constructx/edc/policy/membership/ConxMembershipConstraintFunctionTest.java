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

import org.constructx.edc.policy.TestParticipantAgentPolicyContext;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSubject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.eclipse.edc.policy.model.Operator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.tractusx.edc.edr.spi.CoreConstants.CX_CREDENTIAL_NS;
import static org.eclipse.tractusx.edc.edr.spi.CoreConstants.CX_POLICY_2025_09_NS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConxMembershipConstraintFunctionTest {

    private static final String MEMBERSHIP = CX_POLICY_2025_09_NS + "Membership";
    private static final String CONSTRUCTX_MEMBERSHIP_CREDENTIAL =
            "https://w3id.org/constructx/credentials/v1.0/ConstructXMembershipCredential";

    private final ParticipantAgent participantAgent = mock();
    private final ConxMembershipConstraintFunction<ParticipantAgentPolicyContext> function = new ConxMembershipConstraintFunction<>();
    private final ParticipantAgentPolicyContext context = new TestParticipantAgentPolicyContext(participantAgent);

    @Test
    void evaluate_whenConstructxMembershipCredentialIsMember_shouldReturnTrue() {
        givenCredentials(credential(CONSTRUCTX_MEMBERSHIP_CREDENTIAL, Map.of("isMember", true)));

        assertThat(function.evaluate(MEMBERSHIP, Operator.EQ, "active", null, context)).isTrue();
    }

    @Test
    void evaluate_whenIsMemberIsStringTrue_shouldReturnTrue() {
        givenCredentials(credential(CONSTRUCTX_MEMBERSHIP_CREDENTIAL, Map.of("isMember", "true")));

        assertThat(function.evaluate(MEMBERSHIP, Operator.EQ, "active", null, context)).isTrue();
    }

    @Test
    void evaluate_whenIsMemberIsFalse_shouldReturnFalse() {
        givenCredentials(credential(CONSTRUCTX_MEMBERSHIP_CREDENTIAL, Map.of("isMember", false)));

        assertThat(function.evaluate(MEMBERSHIP, Operator.EQ, "active", null, context)).isFalse();
        assertThat(context.getProblems()).anyMatch(problem -> problem.contains("isMember"));
    }

    @Test
    void evaluate_whenOnlyCatenaxMembershipCredential_shouldReturnFalse() {
        givenCredentials(credential(CX_CREDENTIAL_NS + "MembershipCredential", Map.of("holderIdentifier", "did:web:holder")));

        assertThat(function.evaluate(MEMBERSHIP, Operator.EQ, "active", null, context)).isFalse();
    }

    @Test
    void evaluate_whenNoCredentials_shouldReturnFalse() {
        when(participantAgent.getClaims()).thenReturn(Map.of());

        assertThat(function.evaluate(MEMBERSHIP, Operator.EQ, "active", null, context)).isFalse();
    }

    @Test
    void evaluate_whenRightOperandIsNotActive_shouldReturnFalse() {
        givenCredentials(credential(CONSTRUCTX_MEMBERSHIP_CREDENTIAL, Map.of("isMember", true)));

        assertThat(function.evaluate(MEMBERSHIP, Operator.EQ, "inactive", null, context)).isFalse();
        assertThat(context.getProblems()).anyMatch(problem -> problem.startsWith("Right-operand must be equal to 'active'"));
    }

    @Test
    void evaluate_whenLeftOperandIsNotMembership_shouldReturnFalse() {
        givenCredentials(credential(CONSTRUCTX_MEMBERSHIP_CREDENTIAL, Map.of("isMember", true)));

        assertThat(function.evaluate(CX_POLICY_2025_09_NS + "foobar", Operator.EQ, "active", null, context)).isFalse();
        assertThat(context.getProblems()).anyMatch(problem -> problem.startsWith("Invalid left-operand"));
    }

    @Test
    void canHandle_whenMembershipLeftOperand_shouldReturnTrue() {
        assertThat(function.canHandle(MEMBERSHIP)).isTrue();
        assertThat(function.canHandle(CX_POLICY_2025_09_NS + "FrameworkAgreement")).isFalse();
    }

    @Test
    void validate_whenUpstreamNotation_shouldSucceed() {
        assertThat(function.validate(MEMBERSHIP, Operator.EQ, "active", null).succeeded()).isTrue();
        assertThat(function.validate(MEMBERSHIP, Operator.NEQ, "active", null).succeeded()).isFalse();
    }

    private void givenCredentials(VerifiableCredential... credentials) {
        when(participantAgent.getClaims()).thenReturn(Map.of("vc", List.of(credentials)));
    }

    private static VerifiableCredential credential(String type, Map<String, Object> claims) {
        var subject = CredentialSubject.Builder.newInstance().id("did:web:consumer");
        claims.forEach(subject::claim);
        return VerifiableCredential.Builder.newInstance()
                .types(List.of("VerifiableCredential", type))
                .id(UUID.randomUUID().toString())
                .issuer(new Issuer("did:web:issuer", Map.of()))
                .issuanceDate(Instant.now())
                .credentialSubject(subject.build())
                .build();
    }
}
