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

import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.tractusx.edc.policy.cx.framework.FrameworkAgreementConstraintFunction;

import static org.eclipse.tractusx.edc.edr.spi.CoreConstants.CX_POLICY_2025_09_NS;

/**
 * Construct-X variant of {@link FrameworkAgreementConstraintFunction}. Evaluation is inherited unchanged; only
 * {@link #validate(Object, Operator, Object, Permission)} is relaxed.
 * <p>
 * Upstream only accepts the single hardcoded value {@code "DataExchangeGovernance:1.0"}, which is stricter than what
 * {@code evaluate()} actually parses ("FrameworkAgreement EQ subtype[:version]", version optional, plus the legacy
 * notation "FrameworkAgreement.subtype EQ active[:version]"). The Construct-X Policy Hub emits the subtype without a
 * version, so this validation accepts every value {@code evaluate()} accepts, in both notations.
 */
public class ConxFrameworkAgreementConstraintFunction<C extends ParticipantAgentPolicyContext> extends FrameworkAgreementConstraintFunction<C> {

    private static final String LEGACY_PREFIX = CX_POLICY_2025_09_NS + FRAMEWORK_AGREEMENT_LITERAL + ".";

    @Override
    public Result<Void> validate(Object leftOperand, Operator operator, Object rightValue, Permission rule) {
        if (!Operator.EQ.equals(operator)) {
            return Result.failure("Invalid operator: this constraint only allows the following operators: %s, but received '%s'.".formatted(Operator.EQ, operator));
        }

        if (!(leftOperand instanceof String leftOperandValue) || !(rightValue instanceof String rightOperandValue)) {
            return Result.failure("Both the left- and right-operand must be of type String.");
        }

        return leftOperandValue.startsWith(LEGACY_PREFIX)
                ? validateLegacyNotation(leftOperandValue, rightOperandValue)
                : validateNotation(rightOperandValue);
    }

    /**
     * Mirrors the parsing of the new notation in {@link FrameworkAgreementConstraintFunction} (upstream method is private).
     */
    private Result<Void> validateNotation(String rightOperand) {
        var tokens = rightOperand.split(":");
        if (tokens.length > 2 || tokens.length == 0 || tokens[0] == null || tokens[0].isEmpty()) {
            return Result.failure("Right-operand must contain the sub-type followed by an optional version string: <subtype>[:version], but was '%s'.".formatted(rightOperand));
        }
        return Result.success();
    }

    /**
     * Mirrors the parsing of the legacy notation in {@link FrameworkAgreementConstraintFunction} (upstream method is private).
     */
    private Result<Void> validateLegacyNotation(String leftOperand, String rightOperand) {
        var subType = leftOperand.replace(LEGACY_PREFIX, "");
        if (subType.isEmpty()) {
            return Result.failure("Left-operand must contain the sub-type 'FrameworkAgreement.<subtype>'.");
        }
        if (!rightOperand.startsWith(ACTIVE)) {
            return Result.failure("Right-operand must contain the keyword 'active' followed by an optional version string: 'active'[:version], but was '%s'.".formatted(rightOperand));
        }
        return Result.success();
    }
}
