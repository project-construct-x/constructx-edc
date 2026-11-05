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

package org.constructx.edc.policy.datasubset;

import jakarta.json.Json;
import org.constructx.edc.policy.TestParticipantAgentPolicyContext;
import org.eclipse.edc.junit.assertions.AbstractResultAssert;
import org.eclipse.edc.participant.spi.ParticipantAgent;
import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.eclipse.edc.policy.model.Operator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DataSubsetConstraintFunctionTest {

    private final DataSubsetConstraintFunction<ParticipantAgentPolicyContext> function = new DataSubsetConstraintFunction<>();
    private final ParticipantAgentPolicyContext context = new TestParticipantAgentPolicyContext(mock(ParticipantAgent.class));

    @Test
    void validate_whenIsAnyOfAndExpandedJsonLdList_shouldSucceed() {
        var rightOperand = jsonLdList(
                "field:materialName",
                "attribute:color",
                "submodel:urn:samm:com.example.material:1.0.0#Material",
                "record:2027-Q1");

        var result = function.validate(Operator.IS_ANY_OF, rightOperand, null);

        AbstractResultAssert.assertThat(result).isSucceeded();
    }

    @Test
    void validate_whenIsAnyOfAndSingleString_shouldSucceed() {
        var result = function.validate(Operator.IS_ANY_OF, "field:materialName", null);

        AbstractResultAssert.assertThat(result).isSucceeded();
    }

    @Test
    void validate_whenOperatorIsNotIsAnyOf_shouldFail() {
        var result = function.validate(Operator.EQ, "field:materialName", null);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains("Invalid operator");
    }

    @ParameterizedTest
    @ValueSource(strings = { "column:materialName", "field:", "materialName", "Field:materialName" })
    void validate_whenValueDoesNotMatchPattern_shouldFail(String value) {
        var result = function.validate(Operator.IS_ANY_OF, jsonLdList("record:2027-Q1", value), null);

        assertThat(result.failed()).isTrue();
    }

    @Test
    void validate_whenDuplicateValues_shouldFail() {
        var result = function.validate(Operator.IS_ANY_OF, jsonLdList("field:materialName", "field:materialName"), null);

        assertThat(result.failed()).isTrue();
    }

    @Test
    void validate_whenEmptyList_shouldFail() {
        var result = function.validate(Operator.IS_ANY_OF, List.of(), null);

        assertThat(result.failed()).isTrue();
    }

    @Test
    void evaluate_whenCalled_shouldAlwaysReturnTrue() {
        assertThat(function.evaluate(Operator.IS_ANY_OF, jsonLdList("field:materialName"), null, context)).isTrue();
    }

    // shape of an array right operand after JSON-LD expansion and transformation
    private static List<Map<String, Object>> jsonLdList(String... values) {
        return Arrays.stream(values)
                .map(value -> Map.<String, Object>of("@value", Json.createValue(value)))
                .toList();
    }
}
