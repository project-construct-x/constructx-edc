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

package org.constructx.edc.policy.common;

import org.eclipse.edc.connector.controlplane.contract.spi.policy.AgreementPolicyContext;
import org.eclipse.edc.policy.engine.spi.AtomicConstraintRuleFunction;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Rule;
import org.eclipse.edc.spi.result.Result;

import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * This is an abstract constraint function for DataStartDate constraints. It evaluates to true if the current date is
 * not before the specified start date. The start date is expected to be either in ISO-8061 UTC date-time format,
 * truncated to seconds (e.g. "2024-12-31T23:59:59Z"), or a bare ISO-8601 date (e.g. "2024-12-31"), which is
 * interpreted as the beginning of that day.
 *
 * @param <R> the type of the rule (e.g. Permission, Duty, etc.)
 * @param <C> the type of the agreement policy context
 */
public abstract class AbstractDataStartDateConstraintFunction<R extends Rule, C extends AgreementPolicyContext> implements AtomicConstraintRuleFunction<R, C> {
    private static final Set<Operator> ALLOWED_OPERATORS = Set.of(
            Operator.EQ,
            Operator.GEQ
    );

    @Override
    public boolean evaluate(Operator operator, Object rightOperand, R rule, C context) {
        var limit = DateConstraints.parseAsStartOfDay(rightOperand.toString());
        if (limit == null) {
            context.reportProblem(DateConstraints.invalidRightOperandMessage());
            return false;
        }
        return !context.now().truncatedTo(ChronoUnit.SECONDS).isBefore(limit);
    }

    @Override
    public Result<Void> validate(Operator operator, Object rightOperand, R rule) {
        return DateConstraints.validate(ALLOWED_OPERATORS, operator, rightOperand, DateConstraints::parseAsStartOfDay);
    }
}
