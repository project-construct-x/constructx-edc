/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

package org.eclipse.tractusx.edc.policy.cx.common;

import org.eclipse.edc.connector.controlplane.contract.spi.policy.AgreementPolicyContext;
import org.eclipse.edc.policy.engine.spi.AtomicConstraintRuleFunction;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Rule;
import org.eclipse.edc.spi.result.Result;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.regex.Pattern;


/**
 * This is an abstract constraint function for DataEndDate constraints. It evaluates to true if the current date is
 * not after the specified expiry date. The expiry date is expected to be either in ISO-8061 UTC date-time format,
 * truncated to seconds (e.g. "2024-12-31T23:59:59Z"), or a bare ISO-8601 date (e.g. "2024-12-31"), which is
 * interpreted as the end of that day, inclusive.
 *
 * @param <R> the type of the rule (e.g. Permission, Duty, etc.)
 * @param <C> the type of the agreement policy context
 */
public abstract class AbstractDataEndDateConstraintFunction<R extends Rule, C extends AgreementPolicyContext> implements AtomicConstraintRuleFunction<R, C> {
    private static final Set<Operator> ALLOWED_OPERATORS = Set.of(
            Operator.EQ,
            Operator.LEQ
    );

    private static final String DATE_TIME_PATTERN = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(Z|[+-]\\d{2}:\\d{2})$";
    private static final String DATE_PATTERN = "^\\d{4}-\\d{2}-\\d{2}$";

    @Override
    public boolean evaluate(Operator operator, Object rightOperand, R rule, C context) {
        var expiryDate = parseAsEndOfDayInstant(rightOperand.toString());
        if (expiryDate == null) {
            context.reportProblem("Invalid right-operand: right operand must match pattern '%s' or '%s'".formatted(DATE_TIME_PATTERN, DATE_PATTERN));
            return false;
        }
        return !context.now().truncatedTo(ChronoUnit.SECONDS).isAfter(expiryDate);
    }

    @Override
    public Result<Void> validate(Operator operator, Object rightOperand, R rule) {
        if (operator == null) {
            return Result.failure("Invalid operator: this constraint only allows the following operators: %s, but received null.".formatted(ALLOWED_OPERATORS));
        }

        if (!ALLOWED_OPERATORS.contains(operator)) {
            return Result.failure("Invalid operator: this constraint only allows the following operators: %s, but received '%s'.".formatted(ALLOWED_OPERATORS, operator));
        }

        if (!(rightOperand instanceof String rightValue) || parseAsEndOfDayInstant(rightValue) == null) {
            return Result.failure("Invalid right-operand: right operand must match pattern '%s' or '%s'".formatted(DATE_TIME_PATTERN, DATE_PATTERN));
        }

        return Result.success();
    }

    /**
     * Parses the given value as an {@link Instant}, either directly (full ISO-8601 date-time) or, for a bare
     * ISO-8601 date, as the end of that day (23:59:59 UTC), so that the whole day counts as still within range.
     *
     * @return the parsed instant, or {@code null} if the value matches neither format
     */
    private Instant parseAsEndOfDayInstant(String value) {
        if (Pattern.matches(DATE_TIME_PATTERN, value)) {
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        if (Pattern.matches(DATE_PATTERN, value)) {
            try {
                return LocalDate.parse(value).atTime(23, 59, 59).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        return null;
    }
}
