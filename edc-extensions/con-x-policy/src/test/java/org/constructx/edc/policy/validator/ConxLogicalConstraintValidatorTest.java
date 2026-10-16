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

import org.eclipse.edc.junit.assertions.FailureAssert;
import org.eclipse.edc.validator.jsonobject.JsonLdPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.constructx.edc.policy.validator.ConxPolicyValidationConstants.DATA_USAGE_START_DATE_LITERAL;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_AND_CONSTRAINT_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_AND_SEQUENCE_CONSTRAINT_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_OR_CONSTRAINT_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_PERMISSION_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_XONE_CONSTRAINT_ATTRIBUTE;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyBuilderFixtures.atomicConstraint;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyBuilderFixtures.logicalConstraint;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.ACTION_ACCESS;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.DATA_USAGE_END_DATE_LITERAL;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.FRAMEWORK_AGREEMENT_LITERAL;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.MEMBERSHIP_LITERAL;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.USAGE_PURPOSE_LITERAL;

class ConxLogicalConstraintValidatorTest {

    private final JsonLdPath path = JsonLdPath.path();

    private ConxLogicalConstraintValidator validator() {
        return ConxLogicalConstraintValidator.instance(path, ACTION_ACCESS, ODRL_PERMISSION_ATTRIBUTE);
    }

    @ParameterizedTest
    @ValueSource(strings = { ODRL_AND_CONSTRAINT_ATTRIBUTE, ODRL_OR_CONSTRAINT_ATTRIBUTE })
    void validate_whenAllowedLogicalConstraint_shouldSucceed(String logicalType) {
        var input = logicalConstraint(logicalType,
                atomicConstraint(MEMBERSHIP_LITERAL),
                atomicConstraint(FRAMEWORK_AGREEMENT_LITERAL));

        var result = validator().validate(input);

        assertThat(result).isSucceeded();
    }

    @Test
    void validate_whenOrContainsNestedAnd_shouldSucceed() {
        var input = logicalConstraint(ODRL_OR_CONSTRAINT_ATTRIBUTE,
                atomicConstraint(MEMBERSHIP_LITERAL, "odrl:eq", "active"),
                logicalConstraint(ODRL_AND_CONSTRAINT_ATTRIBUTE,
                        atomicConstraint(DATA_USAGE_START_DATE_LITERAL, "odrl:gteq", "2026-10-14"),
                        atomicConstraint(DATA_USAGE_END_DATE_LITERAL, "odrl:lteq", "2026-10-16")),
                atomicConstraint(FRAMEWORK_AGREEMENT_LITERAL, "odrl:eq", "DataExchangeGovernance"));

        var result = validator().validate(input);

        assertThat(result).isSucceeded();
    }

    @ParameterizedTest
    @ValueSource(strings = { ODRL_XONE_CONSTRAINT_ATTRIBUTE, ODRL_AND_SEQUENCE_CONSTRAINT_ATTRIBUTE })
    void validate_whenNotAllowedLogicalConstraint_shouldFail(String logicalType) {
        var input = logicalConstraint(logicalType,
                atomicConstraint(MEMBERSHIP_LITERAL),
                atomicConstraint(FRAMEWORK_AGREEMENT_LITERAL));

        var result = validator().validate(input);

        assertThat(result).isFailed();
        FailureAssert.assertThat(result.getFailure()).messages().anyMatch(msg -> msg.contains("not allowed logical constraints"));
    }

    @Test
    void validate_whenOrContainsNotAllowedLeftOperand_shouldFail() {
        var input = logicalConstraint(ODRL_OR_CONSTRAINT_ATTRIBUTE,
                atomicConstraint(MEMBERSHIP_LITERAL),
                atomicConstraint(USAGE_PURPOSE_LITERAL));

        var result = validator().validate(input);

        assertThat(result).isFailed();
        FailureAssert.assertThat(result.getFailure()).messages().anyMatch(msg -> msg.contains("is not allowed"));
    }
}
