/********************************************************************************
 * Copyright (c) 2025 Fraunhofer-Gesellschaft zur Förderung der angewandten Forschung e.V.
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
 ********************************************************************************/

package org.constructx.edc.policy.validator;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.edc.junit.assertions.FailureAssert;
import org.eclipse.edc.validator.jsonobject.JsonLdPath;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_ACTION_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_OBLIGATION_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_OR_CONSTRAINT_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_PERMISSION_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_PROHIBITION_ATTRIBUTE;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyBuilderFixtures.appendRulesToPolicy;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyBuilderFixtures.atomicConstraint;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyBuilderFixtures.emptyRule;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyBuilderFixtures.logicalConstraint;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyBuilderFixtures.policy;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyBuilderFixtures.rule;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.ACTION_ACCESS;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.ACTION_USAGE;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.DATA_PROVISIONING_END_DURATION_LITERAL;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.DATA_USAGE_END_DATE_LITERAL;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.DATA_USAGE_END_DURATION_LITERAL;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.FRAMEWORK_AGREEMENT_LITERAL;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.LIABILITY_LITERAL;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.USAGE_PURPOSE_LITERAL;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.USAGE_RESTRICTION_LITERAL;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.VERSION_CHANGES_LITERAL;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.WARRANTY_DEFINITION_LITERAL;

class ConxUsagePolicyValidatorTest {

    private final JsonLdPath path = JsonLdPath.path();

    private ConxUsagePolicyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ConxUsagePolicyValidator(path);
    }

    @Test
    void shouldReturnSuccess_whenAllRequiredPermissionConstraintsPresent() {
        var permission = rule(ACTION_USAGE,
                atomicConstraint(USAGE_PURPOSE_LITERAL),
                atomicConstraint(FRAMEWORK_AGREEMENT_LITERAL),
                atomicConstraint(LIABILITY_LITERAL),
                atomicConstraint(WARRANTY_DEFINITION_LITERAL),
                atomicConstraint(VERSION_CHANGES_LITERAL)
        );
        var input = policy(ODRL_PERMISSION_ATTRIBUTE, permission);

        var result = validator.validate(input);

        assertThat(result).isSucceeded();
    }

    @Test
    void shouldReturnFailure_whenNoRequiredPermissionConstraintPresent() {
        var permission = rule(ACTION_USAGE,
                atomicConstraint(LIABILITY_LITERAL),
                atomicConstraint(WARRANTY_DEFINITION_LITERAL),
                atomicConstraint(VERSION_CHANGES_LITERAL)
        );
        var input = policy(ODRL_PERMISSION_ATTRIBUTE, permission);

        var result = validator.validate(input);

        assertThat(result).isFailed();
        FailureAssert.assertThat(result.getFailure()).messages().anyMatch(msg ->
                msg.contains("Usage policy permission must include at least one of the following constraints") &&
                        msg.contains(USAGE_PURPOSE_LITERAL) &&
                        msg.contains(FRAMEWORK_AGREEMENT_LITERAL));
    }

    @Test
    void shouldReturnSuccess_whenOnlyFrameworkAgreementOfRequiredPermissionConstraintsPresent() {
        // Construct-X fix: "at least one of FrameworkAgreement/UsagePurpose" used to actually
        // require BOTH, even though the failure message already said "at least one of".
        var permission = rule(ACTION_USAGE,
                atomicConstraint(FRAMEWORK_AGREEMENT_LITERAL),
                atomicConstraint(LIABILITY_LITERAL)
        );
        var input = policy(ODRL_PERMISSION_ATTRIBUTE, permission);

        var result = validator.validate(input);

        assertThat(result).isSucceeded();
    }

    @Test
    void shouldReturnSuccess_whenOnlyUsagePurposeOfRequiredPermissionConstraintsPresent() {
        var permission = rule(ACTION_USAGE,
                atomicConstraint(USAGE_PURPOSE_LITERAL),
                atomicConstraint(LIABILITY_LITERAL)
        );
        var input = policy(ODRL_PERMISSION_ATTRIBUTE, permission);

        var result = validator.validate(input);

        assertThat(result).isSucceeded();
    }

    @Test
    void shouldReturnSuccess_whenRequiredPermissionConstraintsCombinedWithOr() {
        // Construct-X: odrl:or is allowed (upstream allows odrl:and only)
        var permission = rule(ACTION_USAGE,
                logicalConstraint(ODRL_OR_CONSTRAINT_ATTRIBUTE,
                        atomicConstraint(FRAMEWORK_AGREEMENT_LITERAL),
                        atomicConstraint(USAGE_PURPOSE_LITERAL))
        );
        var input = policy(ODRL_PERMISSION_ATTRIBUTE, permission);

        var result = validator.validate(input);

        assertThat(result).isSucceeded();
    }

    @Test
    void shouldReturnSuccess_whenValidUsagePermissionWithOnlyRequiredConstraints() {
        var permission = rule(ACTION_USAGE,
                atomicConstraint(USAGE_PURPOSE_LITERAL),
                atomicConstraint(FRAMEWORK_AGREEMENT_LITERAL),
                atomicConstraint(LIABILITY_LITERAL),
                atomicConstraint(WARRANTY_DEFINITION_LITERAL),
                atomicConstraint(VERSION_CHANGES_LITERAL)
        );
        var input = policy(ODRL_PERMISSION_ATTRIBUTE, permission);

        var result = validator.validate(input);

        assertThat(result).isSucceeded();
    }

    @Test
    void shouldReturnFailure_whenPermissionHasWrongActionType() {
        JsonObject constraint = atomicConstraint(FRAMEWORK_AGREEMENT_LITERAL);
        JsonObject permission = rule(ACTION_ACCESS, constraint);
        JsonObject input = policy(ODRL_PERMISSION_ATTRIBUTE, permission);

        ValidationResult result = validator.validate(input);

        assertThat(result).isFailed();
        FailureAssert.assertThat(result.getFailure()).messages().anyMatch(msg ->
                msg.contains("Action was expected to be") && msg.contains(ACTION_USAGE));
    }

    @Test
    void shouldReturnSuccess_whenValidUsageProhibitionWithConstraints() {
        JsonObject permission = rule(ACTION_USAGE, atomicConstraint(USAGE_PURPOSE_LITERAL), atomicConstraint(FRAMEWORK_AGREEMENT_LITERAL));

        JsonObject constraint = atomicConstraint(USAGE_RESTRICTION_LITERAL);
        JsonObject prohibition = rule(ACTION_USAGE, constraint);
        JsonObject input = policy(ODRL_PROHIBITION_ATTRIBUTE, prohibition);
        input = appendRulesToPolicy(input, ODRL_PERMISSION_ATTRIBUTE, permission);

        ValidationResult result = validator.validate(input);

        assertThat(result).isSucceeded();
    }

    @Test
    void shouldReturnSuccess_whenValidUsageProhibitionWithoutConstraints() {
        JsonObject permission = rule(ACTION_USAGE, atomicConstraint(USAGE_PURPOSE_LITERAL), atomicConstraint(FRAMEWORK_AGREEMENT_LITERAL));

        JsonObject prohibition = rule(ACTION_USAGE);
        JsonObject input = policy(ODRL_PROHIBITION_ATTRIBUTE, prohibition);
        input = appendRulesToPolicy(input, ODRL_PERMISSION_ATTRIBUTE, permission);

        ValidationResult result = validator.validate(input);

        assertThat(result).isSucceeded();
    }

    @Test
    void shouldReturnFailure_whenProhibitionHasWrongActionType() {
        JsonObject permission = rule(ACTION_USAGE, atomicConstraint(USAGE_PURPOSE_LITERAL), atomicConstraint(FRAMEWORK_AGREEMENT_LITERAL));

        JsonObject constraint = atomicConstraint(USAGE_PURPOSE_LITERAL);
        JsonObject prohibition = rule(ACTION_ACCESS, constraint);
        JsonObject input = policy(ODRL_PROHIBITION_ATTRIBUTE, prohibition);
        input = appendRulesToPolicy(input, ODRL_PERMISSION_ATTRIBUTE, permission);

        ValidationResult result = validator.validate(input);

        assertThat(result).isFailed();
    }

    @Test
    void shouldReturnSuccess_whenValidUsageObligationWithConstraints() {
        JsonObject permission = rule(ACTION_USAGE, atomicConstraint(USAGE_PURPOSE_LITERAL), atomicConstraint(FRAMEWORK_AGREEMENT_LITERAL));

        JsonObject constraint = atomicConstraint(DATA_PROVISIONING_END_DURATION_LITERAL);
        JsonObject obligation = rule(ACTION_USAGE, constraint);
        JsonObject input = policy(ODRL_OBLIGATION_ATTRIBUTE, obligation);
        input = appendRulesToPolicy(input, ODRL_PERMISSION_ATTRIBUTE, permission);

        ValidationResult result = validator.validate(input);

        assertThat(result).isSucceeded();
    }

    @Test
    void shouldReturnSuccess_whenValidUsageObligationWithoutConstraints() {
        JsonObject permission = rule(ACTION_USAGE, atomicConstraint(USAGE_PURPOSE_LITERAL), atomicConstraint(FRAMEWORK_AGREEMENT_LITERAL));

        JsonObject obligation = rule(ACTION_USAGE);
        JsonObject input = policy(ODRL_OBLIGATION_ATTRIBUTE, obligation);
        input = appendRulesToPolicy(input, ODRL_PERMISSION_ATTRIBUTE, permission);

        ValidationResult result = validator.validate(input);

        assertThat(result).isSucceeded();
    }

    @Test
    void shouldReturnFailure_whenObligationHasWrongActionType() {
        JsonObject permission = rule(ACTION_USAGE, atomicConstraint(USAGE_PURPOSE_LITERAL), atomicConstraint(FRAMEWORK_AGREEMENT_LITERAL));

        JsonObject constraint = atomicConstraint(USAGE_PURPOSE_LITERAL);
        JsonObject obligation = rule(ACTION_ACCESS, constraint);
        JsonObject input = policy(ODRL_OBLIGATION_ATTRIBUTE, obligation);
        input = appendRulesToPolicy(input, ODRL_PERMISSION_ATTRIBUTE, permission);

        ValidationResult result = validator.validate(input);

        assertThat(result).isFailed();
    }

    @Test
    void shouldReturnSuccess_whenPermissionHasActionButNoConstraintKeyAtAll() {
        // Construct-X fix: the Policy Hub omits the "constraint" key entirely for an
        // unconditional permission (@JsonInclude.NON_NULL on a null constraint) - unlike this
        // file's own rule()/emptyRule() fixtures, which always include a "constraint" array (at
        // worst empty). This exact shape (real, valid ODRL - the CONTRACT-category equivalent of
        // a constraint-free ACCESS policy) is now accepted end-to-end:
        // AtLeastOneRuleExists recognizes it as a rule (no longer requires a "constraint" key),
        // and PermissionContainsRequiredConstraints no longer demands a FrameworkAgreement/
        // UsagePurpose anchor for a permission that carries no constraints at all.
        JsonObject permissionWithoutConstraintKey = Json.createObjectBuilder()
                .add(ODRL_ACTION_ATTRIBUTE, ACTION_USAGE)
                .build();
        JsonObject input = policy(ODRL_PERMISSION_ATTRIBUTE, permissionWithoutConstraintKey);

        var result = validator.validate(input);

        assertThat(result).isSucceeded();
    }

    @Test
    void shouldReturnSuccess_whenPermissionHasEmptyConstraintArray() {
        // Same as above, but for the "constraint": [] shape this file's own rule() fixture
        // produces for zero constraint arguments - an empty array should be treated the same as
        // an absent key, not as "has constraints".
        var permission = rule(ACTION_USAGE);
        var input = policy(ODRL_PERMISSION_ATTRIBUTE, permission);

        var result = validator.validate(input);

        assertThat(result).isSucceeded();
    }

    @Test
    void shouldReturnFailure_whenEmptyArrays() {
        JsonObject input = Json.createObjectBuilder()
                .add(ODRL_PERMISSION_ATTRIBUTE, Json.createArrayBuilder())
                .add(ODRL_PROHIBITION_ATTRIBUTE, Json.createArrayBuilder())
                .add(ODRL_OBLIGATION_ATTRIBUTE, Json.createArrayBuilder())
                .build();

        ValidationResult result = validator.validate(input);

        assertThat(result).isFailed();
        FailureAssert.assertThat(result.getFailure()).messages().anyMatch(msg ->
                msg.contains("Policy must contain at least one permission, obligation, or prohibition"));
    }

    @Test
    void shouldReturnSuccess_whenCompleteValidUsagePolicy() {
        JsonObject permission = rule(ACTION_USAGE, atomicConstraint(USAGE_PURPOSE_LITERAL), atomicConstraint(FRAMEWORK_AGREEMENT_LITERAL));
        JsonObject prohibition = rule(ACTION_USAGE, atomicConstraint(USAGE_RESTRICTION_LITERAL));
        JsonObject obligation = rule(ACTION_USAGE, atomicConstraint(DATA_PROVISIONING_END_DURATION_LITERAL));

        JsonObject input = Json.createObjectBuilder()
                .add(ODRL_PERMISSION_ATTRIBUTE, Json.createArrayBuilder().add(permission))
                .add(ODRL_PROHIBITION_ATTRIBUTE, Json.createArrayBuilder().add(prohibition))
                .add(ODRL_OBLIGATION_ATTRIBUTE, Json.createArrayBuilder().add(obligation))
                .build();

        ValidationResult result = validator.validate(input);

        assertThat(result).isSucceeded();
    }

    @Test
    void shouldReturnFailure_whenEmptyPolicy() {
        JsonObject input = Json.createObjectBuilder().build();

        ValidationResult result = validator.validate(input);

        assertThat(result).isFailed();
        FailureAssert.assertThat(result.getFailure()).messages().anyMatch(msg ->
                msg.contains("Policy must contain at least one permission, obligation, or prohibition"));
    }

    @Test
    void shouldReturnFailure_whenRuleMissingAction() {
        JsonObject permission = emptyRule();
        JsonObject input = policy(ODRL_PERMISSION_ATTRIBUTE, permission);

        ValidationResult result = validator.validate(input);

        assertThat(result).isFailed();
        FailureAssert.assertThat(result.getFailure()).messages().anyMatch(msg ->
                msg.contains("Action property is missing"));
    }

    @Test
    void shouldReturnFailure_whenMutuallyExclusiveConstraintsInPolicy() {
        JsonObject permission = rule(ACTION_USAGE,
                atomicConstraint(DATA_USAGE_END_DURATION_LITERAL),
                atomicConstraint(DATA_USAGE_END_DATE_LITERAL));
        JsonObject input = policy(ODRL_PERMISSION_ATTRIBUTE, permission);

        ValidationResult result = validator.validate(input);

        assertThat(result).isFailed();
        FailureAssert.assertThat(result.getFailure()).messages().anyMatch(msg ->
                msg.contains("is mutually exclusive"));
    }
}
