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

import org.constructx.edc.policy.TestParticipantAgentPolicyContext;
import org.eclipse.edc.junit.assertions.AbstractResultAssert;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.tractusx.edc.spi.identity.mapper.BdrsClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConnectorConstraintFunctionTest {

    private static final String RIGHT_OPERAND = "did:web:adesso.de,did:web:adesso-group.com";

    private final ParticipantAgent participantAgent = mock();
    private final BdrsClient bdrsClient = mock();
    private final ConnectorConstraintFunction<ParticipantAgentPolicyContext> function = new ConnectorConstraintFunction<>(bdrsClient);
    private final ParticipantAgentPolicyContext context = new TestParticipantAgentPolicyContext(participantAgent);

    @Test
    void evaluate_whenIdentityIsDidAndInList_shouldReturnTrue() {
        when(participantAgent.getIdentity()).thenReturn("did:web:adesso.de");

        assertThat(function.evaluate(Operator.EQ, RIGHT_OPERAND, null, context)).isTrue();
    }

    @Test
    void evaluate_whenIdentityIsDidAndNotInList_shouldReturnFalse() {
        when(participantAgent.getIdentity()).thenReturn("did:web:other.example");

        assertThat(function.evaluate(Operator.EQ, RIGHT_OPERAND, null, context)).isFalse();
    }

    @Test
    void evaluate_whenIdentityIsBpnAndResolvedDidInList_shouldReturnTrue() {
        var bpn = "BPNL00000000001A";
        when(participantAgent.getIdentity()).thenReturn(bpn);
        when(bdrsClient.resolveDid(bpn)).thenReturn("did:web:adesso-group.com");

        assertThat(function.evaluate(Operator.EQ, RIGHT_OPERAND, null, context)).isTrue();
    }

    @Test
    void evaluate_whenBpnCannotBeResolvedToDid_shouldReturnFalse() {
        var bpn = "BPNL00000000001A";
        when(participantAgent.getIdentity()).thenReturn(bpn);
        when(bdrsClient.resolveDid(bpn)).thenReturn(null);

        assertThat(function.evaluate(Operator.EQ, RIGHT_OPERAND, null, context)).isFalse();
    }

    @Test
    void evaluate_whenIdentityIsNull_shouldReturnFalse() {
        when(participantAgent.getIdentity()).thenReturn(null);

        assertThat(function.evaluate(Operator.EQ, RIGHT_OPERAND, null, context)).isFalse();
    }

    @Test
    void evaluate_whenOperatorIsNotEq_shouldReturnFalse() {
        when(participantAgent.getIdentity()).thenReturn("did:web:adesso.de");

        assertThat(function.evaluate(Operator.NEQ, RIGHT_OPERAND, null, context)).isFalse();
    }

    @Test
    void evaluate_whenRightOperandIsCollection_shouldMatch() {
        when(participantAgent.getIdentity()).thenReturn("did:web:adesso-group.com");

        assertThat(function.evaluate(Operator.EQ, List.of("did:web:adesso.de", "did:web:adesso-group.com"), null, context)).isTrue();
    }

    @Test
    void evaluate_whenRightOperandContainsWhitespace_shouldStillMatch() {
        when(participantAgent.getIdentity()).thenReturn("did:web:adesso-group.com");

        assertThat(function.evaluate(Operator.EQ, "did:web:adesso.de, did:web:adesso-group.com", null, context)).isTrue();
    }

    @Test
    void validate_whenEqAndValidDidList_shouldSucceed() {
        var result = function.validate(Operator.EQ, RIGHT_OPERAND, null);

        AbstractResultAssert.assertThat(result).isSucceeded();
    }

    @Test
    void validate_whenInvalidOperator_shouldFail() {
        var result = function.validate(Operator.IS_ANY_OF, RIGHT_OPERAND, null);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains("Invalid operator");
    }

    @Test
    void validate_whenRightOperandDoesNotMatchDidPattern_shouldFail() {
        var result = function.validate(Operator.EQ, "not-a-did", null);

        assertThat(result.failed()).isTrue();
    }

    @Test
    void validate_whenRightOperandUsesNonWebDidMethod_shouldFail() {
        var result = function.validate(Operator.EQ, "did:key:z6MkpTHR8VNsBxYAAWHut2Geadd9jSwuBV8xRoAnwWsdvktH", null);

        assertThat(result.failed()).isTrue();
    }

    @Test
    void validate_whenDuplicateDidsInList_shouldFail() {
        var result = function.validate(Operator.EQ, "did:web:adesso.de,did:web:adesso.de", null);

        assertThat(result.failed()).isTrue();
    }
}
