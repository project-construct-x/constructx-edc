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

package org.constructx.edc.policy.datausage;

import org.constructx.edc.policy.common.DateConstraints;
import org.eclipse.edc.connector.controlplane.catalog.spi.policy.CatalogPolicyContext;
import org.eclipse.edc.policy.engine.spi.AtomicConstraintRuleFunction;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.spi.result.Result;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * Catalog-scope evaluation of {@code DataUsageStartDate} in access policies: true if the current time is not before the start date.
 * Unlike the agreement-based functions, the catalog context has no contract agreement and therefore no
 * {@code now()}; the time comes from the injected {@link Clock}. A date constraint in an access policy is thus
 * enforced when the catalog is built and when an initial contract offer is validated.
 */
public class CatalogDataUsageStartDateConstraintFunction implements AtomicConstraintRuleFunction<Permission, CatalogPolicyContext> {
    private static final Set<Operator> ALLOWED_OPERATORS = Set.of(Operator.EQ, Operator.GEQ);

    private final Clock clock;

    public CatalogDataUsageStartDateConstraintFunction(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean evaluate(Operator operator, Object rightOperand, Permission rule, CatalogPolicyContext context) {
        var limit = DateConstraints.parseAsStartOfDay(rightOperand.toString());
        if (limit == null) {
            context.reportProblem(DateConstraints.invalidRightOperandMessage());
            return false;
        }
        return !clock.instant().truncatedTo(ChronoUnit.SECONDS).isBefore(limit);
    }

    @Override
    public Result<Void> validate(Operator operator, Object rightOperand, Permission rule) {
        return DateConstraints.validate(ALLOWED_OPERATORS, operator, rightOperand, DateConstraints::parseAsStartOfDay);
    }
}
