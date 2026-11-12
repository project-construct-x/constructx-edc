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

import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.spi.result.Result;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Parsing and validation shared by all Construct-X date constraint functions, no matter in which policy context they
 * are evaluated (agreement contexts such as transfer process and policy monitor, or the catalog context).
 * Accepted right operands are full ISO-8601 date-times with offset, truncated to seconds
 * (e.g. "2024-12-31T23:59:59Z"), and bare ISO-8601 dates (e.g. "2024-12-31").
 */
public final class DateConstraints {

    static final String DATE_TIME_PATTERN = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(Z|[+-]\\d{2}:\\d{2})$";
    static final String DATE_PATTERN = "^\\d{4}-\\d{2}-\\d{2}$";

    private DateConstraints() {
    }

    /**
     * Parses a start date: a bare date is the beginning of that day (00:00:00 UTC).
     *
     * @return the instant, or {@code null} if the value matches neither format
     */
    public static Instant parseAsStartOfDay(String value) {
        return parse(value, date -> date.atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    /**
     * Parses an end date: a bare date is the end of that day (23:59:59 UTC), so the whole day is still in range.
     *
     * @return the instant, or {@code null} if the value matches neither format
     */
    public static Instant parseAsEndOfDay(String value) {
        return parse(value, date -> date.atTime(23, 59, 59).toInstant(ZoneOffset.UTC));
    }

    public static String invalidRightOperandMessage() {
        return "Invalid right-operand: right operand must match pattern '%s' or '%s'".formatted(DATE_TIME_PATTERN, DATE_PATTERN);
    }

    /**
     * Checks operator and right operand of a date constraint.
     *
     * @param allowedOperators operators the constraint accepts
     * @param parser           {@link #parseAsStartOfDay} or {@link #parseAsEndOfDay}
     */
    public static Result<Void> validate(Set<Operator> allowedOperators, Operator operator, Object rightOperand,
                                        Function<String, Instant> parser) {
        if (operator == null) {
            return Result.failure("Invalid operator: this constraint only allows the following operators: %s, but received null.".formatted(allowedOperators));
        }
        if (!allowedOperators.contains(operator)) {
            return Result.failure("Invalid operator: this constraint only allows the following operators: %s, but received '%s'.".formatted(allowedOperators, operator));
        }
        if (!(rightOperand instanceof String rightValue) || parser.apply(rightValue) == null) {
            return Result.failure(invalidRightOperandMessage());
        }
        return Result.success();
    }

    private static Instant parse(String value, Function<LocalDate, Instant> dateToInstant) {
        try {
            if (Pattern.matches(DATE_TIME_PATTERN, value)) {
                return Instant.parse(value);
            }
            if (Pattern.matches(DATE_PATTERN, value)) {
                return dateToInstant.apply(LocalDate.parse(value));
            }
        } catch (DateTimeParseException e) {
            return null;
        }
        return null;
    }
}
