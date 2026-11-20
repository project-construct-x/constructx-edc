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

import org.eclipse.edc.connector.controlplane.catalog.spi.policy.CatalogPolicyContext;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CatalogDataUsageStartDateConstraintFunctionTest {

    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    private final CatalogDataUsageStartDateConstraintFunction function = new CatalogDataUsageStartDateConstraintFunction(Clock.fixed(NOW, ZoneOffset.UTC));
    private final CatalogPolicyContext context = new CatalogPolicyContext(mock(ParticipantAgent.class));
    private final Permission permission = Permission.Builder.newInstance().build();

    private boolean evaluate(Operator operator, String rightOperand) {
        return function.evaluate(operator, rightOperand, permission, context);
    }

    @Test
    void evaluate_whenStartDateIsInThePast_shouldBeTrue() {
        assertThat(evaluate(Operator.GEQ, "2026-06-14")).isTrue();
        assertThat(evaluate(Operator.GEQ, "2026-06-15")).isTrue(); // bare date = start of that day
        assertThat(evaluate(Operator.GEQ, "2026-06-15T12:00:00Z")).isTrue(); // exactly now
    }

    @Test
    void evaluate_whenStartDateIsInTheFuture_shouldBeFalse() {
        assertThat(evaluate(Operator.GEQ, "2026-06-16")).isFalse();
        assertThat(evaluate(Operator.GEQ, "2026-06-15T12:00:01Z")).isFalse();
    }

    @Test
    void evaluate_whenRightOperandIsMalformed_shouldFailAndReportProblem() {
        assertThat(evaluate(Operator.GEQ, "next week")).isFalse();

        assertThat(context.getProblems()).hasSize(1);
    }

    @Test
    void validate_whenOperatorNotAllowed_shouldFail() {
        assertThat(function.validate(Operator.LEQ, "2026-06-15", permission).failed()).isTrue();
        assertThat(function.validate(null, "2026-06-15", permission).failed()).isTrue();
    }

    @Test
    void validate_whenOperatorAndDateAreValid_shouldSucceed() {
        assertThat(function.validate(Operator.GEQ, "2026-06-15", permission).succeeded()).isTrue();
        assertThat(function.validate(Operator.EQ, "2026-06-15T10:00:00Z", permission).succeeded()).isTrue();
    }

    @Test
    void validate_whenRightOperandIsNotValidDate_shouldFail() {
        assertThat(function.validate(Operator.GEQ, "tomorrow", permission).failed()).isTrue();
        assertThat(function.validate(Operator.GEQ, 42, permission).failed()).isTrue();
    }
}
