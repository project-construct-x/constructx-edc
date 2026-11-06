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
import org.eclipse.edc.validator.spi.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_OBLIGATION_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_PERMISSION_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_PROHIBITION_ATTRIBUTE;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyBuilderFixtures.atomicConstraint;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyBuilderFixtures.policy;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyBuilderFixtures.policyDefinition;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyBuilderFixtures.rule;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.ACTION_ACCESS;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.ACTION_USAGE;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.FRAMEWORK_AGREEMENT_LITERAL;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.MEMBERSHIP_LITERAL;

/**
 * Runs whole policy definitions through {@link ConxPolicyDefinitionValidator#instance()}, i.e. the validator the
 * Policy Hub import (and {@code POST /v3/policydefinitions}) uses. Guards the wiring between the Construct-X validators
 * and EDC's JSON object validator, which the single-validator tests do not cover.
 */
class ConxPolicyDefinitionValidatorTest {

    private static final String DEFINITION_ID = "00000000-0000-0000-0000-000000000002";
    private static final String UNKNOWN_ACTION = "https://example.com/not-a-policy-type";
    private static final String ABAC_LEFT_OPERAND =
            "https://w3id.org/constructx/credentials/v1.0/ConstructXMembershipCredential.credentialSubject.isMember";

    private final Validator<jakarta.json.JsonObject> validator = ConxPolicyDefinitionValidator.instance();

    @Test
    void validate_whenAccessPolicyWithMembership_shouldSucceed() {
        var permission = rule(ACTION_ACCESS, atomicConstraint(MEMBERSHIP_LITERAL, "odrl:eq", "active"));
        var input = policyDefinition(policy(ODRL_PERMISSION_ATTRIBUTE, permission), DEFINITION_ID);

        var result = validator.validate(input);

        assertThat(result).isSucceeded();
    }

    @Test
    void validate_whenPolicyHasNoRules_shouldBeTreatedAsAccessPolicyAndSucceed() {
        // the "aPolicy" of the Bruno collection: empty permission/prohibition/obligation arrays
        var emptyPolicy = Json.createObjectBuilder()
                .add(TYPE, Json.createArrayBuilder().add("http://www.w3.org/ns/odrl/2/Set"))
                .build();

        var result = validator.validate(policyDefinition(emptyPolicy, DEFINITION_ID));

        assertThat(result).isSucceeded();
    }

    @Test
    void validate_whenUsagePolicyWithFrameworkAgreementWithoutVersion_shouldSucceed() {
        // Construct-X relaxation: a FrameworkAgreement subtype without version is valid
        var permission = rule(ACTION_USAGE, atomicConstraint(FRAMEWORK_AGREEMENT_LITERAL, "odrl:eq", "DataExchangeGovernance"));
        var input = policyDefinition(policy(ODRL_PERMISSION_ATTRIBUTE, permission), DEFINITION_ID);

        var result = validator.validate(input);

        assertThat(result).isSucceeded();
    }

    @Test
    void validate_whenAccessPolicyWithAbacConstraint_shouldSucceed() {
        var permission = rule(ACTION_ACCESS, atomicConstraint(ABAC_LEFT_OPERAND, "odrl:eq", "true"));
        var input = policyDefinition(policy(ODRL_PERMISSION_ATTRIBUTE, permission), DEFINITION_ID);

        var result = validator.validate(input);

        assertThat(result).isSucceeded();
    }

    @Test
    void validate_whenUsagePolicyWithOnlyAbacConstraint_shouldSucceed() {
        // the example of the basic-abac README: a contract policy that needs no FrameworkAgreement/UsagePurpose
        var permission = rule(ACTION_USAGE, atomicConstraint(ABAC_LEFT_OPERAND, "odrl:eq", "true"));
        var input = policyDefinition(policy(ODRL_PERMISSION_ATTRIBUTE, permission), DEFINITION_ID);

        var result = validator.validate(input);

        assertThat(result).isSucceeded();
    }

    @Test
    void validate_whenAbacConstraintCombinedWithMembership_shouldSucceedForAccessAndUsage() {
        var access = rule(ACTION_ACCESS,
                atomicConstraint(MEMBERSHIP_LITERAL, "odrl:eq", "active"),
                atomicConstraint(ABAC_LEFT_OPERAND, "odrl:gteq", 4));
        var usage = rule(ACTION_USAGE,
                atomicConstraint(FRAMEWORK_AGREEMENT_LITERAL, "odrl:eq", "DataExchangeGovernance"),
                atomicConstraint(ABAC_LEFT_OPERAND, "odrl:gteq", 4));

        assertThat(validator.validate(policyDefinition(policy(ODRL_PERMISSION_ATTRIBUTE, access), DEFINITION_ID))).isSucceeded();
        assertThat(validator.validate(policyDefinition(policy(ODRL_PERMISSION_ATTRIBUTE, usage), DEFINITION_ID))).isSucceeded();
    }

    @Test
    void validate_whenAbacConstraintInUsageProhibition_shouldFail() {
        var prohibition = rule(ACTION_USAGE, atomicConstraint(ABAC_LEFT_OPERAND, "odrl:eq", "true"));
        var input = policyDefinition(policy(ODRL_PROHIBITION_ATTRIBUTE, prohibition), DEFINITION_ID);

        var result = validator.validate(input);

        assertThat(result).isFailed();
    }

    @Test
    void validate_whenLeftOperandLooksLikeAbacButIsMalformed_shouldFail() {
        // missing the mandatory .credentialSubject segment
        var permission = rule(ACTION_USAGE,
                atomicConstraint("https://w3id.org/constructx/credentials/v1.0/ConstructXMembershipCredential.isMember", "odrl:eq", "true"));
        var input = policyDefinition(policy(ODRL_PERMISSION_ATTRIBUTE, permission), DEFINITION_ID);

        var result = validator.validate(input);

        assertThat(result).isFailed();
    }

    @Test
    void validate_whenLeftOperandNotAllowedForAccessPolicy_shouldFailWithMessageAndPath() {
        var permission = rule(ACTION_ACCESS, atomicConstraint("https://example.com/UnknownOperand", "odrl:eq", "x"));
        var input = policyDefinition(policy(ODRL_PERMISSION_ATTRIBUTE, permission), DEFINITION_ID);

        var result = validator.validate(input);

        assertThat(result).isFailed();
        assertThat(result.toResult().getFailureDetail())
                .contains("https://example.com/UnknownOperand")
                .contains("Path:");
    }

    @Test
    void validate_whenAccessPolicyHasProhibition_shouldFail() {
        var input = policyDefinition(policy(ODRL_PROHIBITION_ATTRIBUTE, rule(ACTION_ACCESS)), DEFINITION_ID);

        var result = validator.validate(input);

        assertThat(result).isFailed();
    }

    @Test
    void validate_whenAccessPolicyHasObligation_shouldFail() {
        var input = policyDefinition(policy(ODRL_OBLIGATION_ATTRIBUTE, rule(ACTION_ACCESS)), DEFINITION_ID);

        var result = validator.validate(input);

        assertThat(result).isFailed();
    }

    @Test
    void validate_whenRuleActionIsNeitherAccessNorUse_shouldFailNamingTheAction() {
        var input = policyDefinition(policy(ODRL_PERMISSION_ATTRIBUTE, rule(UNKNOWN_ACTION)), DEFINITION_ID);

        var result = validator.validate(input);

        assertThat(result).isFailed();
        assertThat(result.getFailureDetail()).contains("Failed to resolve policy type").contains(UNKNOWN_ACTION);
    }

    @Test
    void validate_whenRulesMixAccessAndUsage_shouldFailAsInconsistent() {
        var input = policyDefinition(policy(ODRL_PERMISSION_ATTRIBUTE, rule(ACTION_ACCESS), rule(ACTION_USAGE)), DEFINITION_ID);

        var result = validator.validate(input);

        assertThat(result).isFailed();
        assertThat(result.getFailureDetail()).contains("inconsistent policy types");
    }

    @Test
    void validate_whenPolicyObjectIsMissing_shouldFail() {
        var input = Json.createObjectBuilder().add(ID, DEFINITION_ID).build();

        var result = validator.validate(input);

        assertThat(result).isFailed();
    }

    @Test
    void validate_whenPolicyIsNotAnOdrlSet_shouldFail() {
        var offerPolicy = Json.createObjectBuilder(policy(ODRL_PERMISSION_ATTRIBUTE, rule(ACTION_ACCESS)))
                .add(TYPE, Json.createArrayBuilder().add("http://www.w3.org/ns/odrl/2/Offer"))
                .build();
        var input = Json.createObjectBuilder()
                .add(ID, DEFINITION_ID)
                .add(EDC_NAMESPACE + "policy", Json.createArrayBuilder().add(offerPolicy))
                .build();

        var result = validator.validate(input);

        assertThat(result).isFailed();
    }

    @Test
    void validate_whenDefinitionIdIsBlank_shouldFail() {
        var input = policyDefinition(policy(ODRL_PERMISSION_ATTRIBUTE, rule(ACTION_ACCESS)), " ");

        var result = validator.validate(input);

        assertThat(result).isFailed();
        assertThat(result.getFailureDetail()).isNotBlank();
    }
}
