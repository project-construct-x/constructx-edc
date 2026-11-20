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

package org.constructx.edc.policy.framework;

import org.eclipse.edc.junit.assertions.AbstractResultAssert;
import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.eclipse.edc.policy.model.Operator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.tractusx.edc.edr.spi.CoreConstants.CX_POLICY_2025_09_NS;

class ConxFrameworkAgreementConstraintFunctionTest {

    private final ConxFrameworkAgreementConstraintFunction<ParticipantAgentPolicyContext> function = new ConxFrameworkAgreementConstraintFunction<>();

    @Test
    void validate_whenInvalidOperator_shouldFail() {
        var result = function.validate(CX_POLICY_2025_09_NS + "FrameworkAgreement", Operator.HAS_PART, "dataExchangeGovernance", null);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains("Invalid operator");
    }

    @Test
    void validate_whenNewNotationSubtypeOnly_shouldSucceed() {
        // the Construct-X Policy Hub emits exactly this: no version suffix. evaluate() already treats this as valid;
        // validate() must agree, otherwise a policy that would work at negotiation/transfer time is rejected at creation.
        var result = function.validate(CX_POLICY_2025_09_NS + "FrameworkAgreement", Operator.EQ, "DataExchangeGovernance", null);

        AbstractResultAssert.assertThat(result).isSucceeded();
    }

    @Test
    void validate_whenNewNotationSubtypeWithVersion_shouldSucceed() {
        var result = function.validate(CX_POLICY_2025_09_NS + "FrameworkAgreement", Operator.EQ, "DataExchangeGovernance:1.0", null);

        AbstractResultAssert.assertThat(result).isSucceeded();
    }

    @Test
    void validate_whenNewNotationMissingSubtype_shouldFail() {
        var result = function.validate(CX_POLICY_2025_09_NS + "FrameworkAgreement", Operator.EQ, ":1.0", null);

        assertThat(result.failed()).isTrue();
    }

    @Test
    void validate_whenRightOperandNotString_shouldFail() {
        var result = function.validate(CX_POLICY_2025_09_NS + "FrameworkAgreement", Operator.EQ, 42, null);

        assertThat(result.failed()).isTrue();
    }

    @Test
    void validate_whenLegacyNotationActive_shouldSucceed() {
        var result = function.validate(CX_POLICY_2025_09_NS + "FrameworkAgreement.dataExchangeGovernance", Operator.EQ, "active", null);

        AbstractResultAssert.assertThat(result).isSucceeded();
    }

    @Test
    void validate_whenLegacyNotationActiveWithVersion_shouldSucceed() {
        var result = function.validate(CX_POLICY_2025_09_NS + "FrameworkAgreement.dataExchangeGovernance", Operator.EQ, "active:1.0.0", null);

        AbstractResultAssert.assertThat(result).isSucceeded();
    }

    @Test
    void validate_whenLegacyNotationNotActive_shouldFail() {
        var result = function.validate(CX_POLICY_2025_09_NS + "FrameworkAgreement.dataExchangeGovernance", Operator.EQ, "violates", null);

        assertThat(result.failed()).isTrue();
    }

    @Test
    void validate_whenLegacyNotationMissingSubtype_shouldFail() {
        var result = function.validate(CX_POLICY_2025_09_NS + "FrameworkAgreement.", Operator.EQ, "active", null);

        assertThat(result.failed()).isTrue();
    }
}
