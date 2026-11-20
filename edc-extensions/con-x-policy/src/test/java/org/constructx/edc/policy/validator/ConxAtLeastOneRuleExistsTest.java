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

package org.constructx.edc.policy.validator;

import jakarta.json.Json;
import org.eclipse.edc.validator.jsonobject.JsonLdPath;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_OBLIGATION_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_PERMISSION_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_PROHIBITION_ATTRIBUTE;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyBuilderFixtures.atomicConstraint;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyBuilderFixtures.emptyRule;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyBuilderFixtures.policy;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyBuilderFixtures.rule;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.ACTION_USAGE;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.USAGE_PURPOSE_LITERAL;

class ConxAtLeastOneRuleExistsTest {

    private final ConxAtLeastOneRuleExists validator = new ConxAtLeastOneRuleExists(JsonLdPath.path());

    @Test
    void validate_whenPermissionWithConstraint_shouldSucceed() {
        var input = policy(ODRL_PERMISSION_ATTRIBUTE, rule(ACTION_USAGE, atomicConstraint(USAGE_PURPOSE_LITERAL)));

        assertThat(validator.validate(input)).isSucceeded();
    }

    @Test
    void validate_whenPermissionWithoutConstraint_shouldSucceed() {
        // Construct-X relaxation: an unconditional rule ("no restrictions") counts as a rule
        var input = policy(ODRL_PERMISSION_ATTRIBUTE, rule(ACTION_USAGE));

        assertThat(validator.validate(input)).isSucceeded();
    }

    @Test
    void validate_whenOnlyObligationOrProhibition_shouldSucceed() {
        assertThat(validator.validate(policy(ODRL_OBLIGATION_ATTRIBUTE, rule(ACTION_USAGE)))).isSucceeded();
        assertThat(validator.validate(policy(ODRL_PROHIBITION_ATTRIBUTE, rule(ACTION_USAGE)))).isSucceeded();
    }

    @Test
    void validate_whenNoRuleArrays_shouldFail() {
        var input = Json.createObjectBuilder().build();

        var result = validator.validate(input);

        assertThat(result).isFailed();
        assertThat(result.getFailureDetail()).contains("at least one permission, obligation, or prohibition");
    }

    @Test
    void validate_whenRuleArrayIsEmpty_shouldFail() {
        var input = Json.createObjectBuilder().add(ODRL_PERMISSION_ATTRIBUTE, Json.createArrayBuilder()).build();

        assertThat(validator.validate(input)).isFailed();
    }

    @Test
    void validate_whenRuleArrayHoldsNoObjects_shouldFail() {
        var input = Json.createObjectBuilder()
                .add(ODRL_PERMISSION_ATTRIBUTE, Json.createArrayBuilder().add("not-an-object"))
                .build();

        assertThat(validator.validate(input)).isFailed();
    }

    @Test
    void validate_whenOnlyEmptyRuleObject_shouldSucceed() {
        // an empty rule object is still an object; action presence is checked elsewhere (PolicyTypeResolver)
        var input = policy(ODRL_PERMISSION_ATTRIBUTE, emptyRule());

        assertThat(validator.validate(input)).isSucceeded();
    }
}
