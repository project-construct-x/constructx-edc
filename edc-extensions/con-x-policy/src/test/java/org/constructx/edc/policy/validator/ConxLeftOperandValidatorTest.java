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
import org.eclipse.edc.validator.jsonobject.JsonObjectValidator;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.constructx.edc.policy.validator.ConxPolicyValidationConstants.CONNECTOR_LITERAL;
import static org.constructx.edc.policy.validator.ConxPolicyValidationConstants.DATA_SUBSET_LITERAL;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_OBLIGATION_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_PERMISSION_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_PROHIBITION_ATTRIBUTE;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.ACTION_ACCESS;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.ACTION_USAGE;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.DATA_PROVISIONING_END_DURATION_LITERAL;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.FRAMEWORK_AGREEMENT_LITERAL;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.MEMBERSHIP_LITERAL;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.USAGE_PURPOSE_LITERAL;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.USAGE_RESTRICTION_LITERAL;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.WARRANTY_DEFINITION_LITERAL;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.WARRANTY_DURATION_MONTHS_LITERAL;

class ConxLeftOperandValidatorTest {

    private static final String ABAC_LEFT_OPERAND =
            "https://w3id.org/constructx/credentials/v1.0/ConstructXMembershipCredential.credentialSubject.isMember";

    private final JsonLdPath path = JsonLdPath.path();

    @Test
    void shouldReturnSuccess_whenValidAccessPermissionPolicyLeftOperand() {
        JsonObject input = Json.createObjectBuilder()
                .add(ID, FRAMEWORK_AGREEMENT_LITERAL)
                .build();

        ValidationResult result = validateLeftOperand(input, ACTION_ACCESS, ODRL_PERMISSION_ATTRIBUTE);

        assertThat(result).isSucceeded();
    }

    @Test
    void shouldReturnSuccess_whenValidUsagePermissionPolicyLeftOperand() {
        JsonObject input = Json.createObjectBuilder()
                .add(ID, USAGE_PURPOSE_LITERAL)
                .build();

        ValidationResult result = validateLeftOperand(input, ACTION_USAGE, ODRL_PERMISSION_ATTRIBUTE);

        assertThat(result).isSucceeded();
    }

    @Test
    void shouldReturnSuccess_whenValidUsageProhibitionPolicyLeftOperand() {
        JsonObject input = Json.createObjectBuilder()
                .add(ID, USAGE_RESTRICTION_LITERAL)
                .build();

        ValidationResult result = validateLeftOperand(input, ACTION_USAGE, ODRL_PROHIBITION_ATTRIBUTE);

        assertThat(result).isSucceeded();
    }

    @Test
    void shouldReturnSuccess_whenValidUsageObligationPolicyLeftOperand() {
        JsonObject input = Json.createObjectBuilder()
                .add(ID, DATA_PROVISIONING_END_DURATION_LITERAL)
                .build();

        ValidationResult result = validateLeftOperand(input, ACTION_USAGE, ODRL_OBLIGATION_ATTRIBUTE);

        assertThat(result).isSucceeded();
    }

    @Test
    void shouldReturnSuccess_whenConnectorLeftOperandUsedInAccessPermissionPolicy() {
        JsonObject input = Json.createObjectBuilder()
                .add(ID, CONNECTOR_LITERAL)
                .build();

        ValidationResult result = validateLeftOperand(input, ACTION_ACCESS, ODRL_PERMISSION_ATTRIBUTE);

        assertThat(result).isSucceeded();
    }

    @Test
    void shouldReturnFailure_whenConnectorLeftOperandUsedInUsagePermissionPolicy() {
        JsonObject input = Json.createObjectBuilder()
                .add(ID, CONNECTOR_LITERAL)
                .build();

        ValidationResult result = validateLeftOperand(input, ACTION_USAGE, ODRL_PERMISSION_ATTRIBUTE);

        assertThat(result).isFailed();
    }

    @Test
    void shouldReturnSuccess_whenDataSubsetLeftOperandUsedInAccessPermissionPolicy() {
        JsonObject input = Json.createObjectBuilder()
                .add(ID, DATA_SUBSET_LITERAL)
                .build();

        ValidationResult result = validateLeftOperand(input, ACTION_ACCESS, ODRL_PERMISSION_ATTRIBUTE);

        assertThat(result).isSucceeded();
    }

    @Test
    void shouldReturnSuccess_whenDataSubsetLeftOperandUsedInUsagePermissionPolicy() {
        JsonObject input = Json.createObjectBuilder()
                .add(ID, DATA_SUBSET_LITERAL)
                .build();

        ValidationResult result = validateLeftOperand(input, ACTION_USAGE, ODRL_PERMISSION_ATTRIBUTE);

        assertThat(result).isSucceeded();
    }

    @Test
    void shouldReturnFailure_whenIdIsMissing() {
        JsonObject input = Json.createObjectBuilder().build();

        ValidationResult result = validateLeftOperand(input, ACTION_ACCESS, ODRL_PERMISSION_ATTRIBUTE);

        assertThat(result).isFailed();
    }

    @Test
    void shouldReturnFailure_whenLeftOperandNotAllowedForAccessPermissionPolicy() {
        JsonObject input = Json.createObjectBuilder()
                .add(ID, DATA_PROVISIONING_END_DURATION_LITERAL)
                .build();

        ValidationResult result = validateLeftOperand(input, ACTION_ACCESS, ODRL_PERMISSION_ATTRIBUTE);

        assertThat(result).isFailed();
        FailureAssert.assertThat(result.getFailure()).messages().anyMatch(msg -> msg.contains("is not allowed"));
    }

    @Test
    void shouldReturnFailure_whenLeftOperandNotAllowedForUsagePermissionPolicy() {
        JsonObject input = Json.createObjectBuilder()
                .add(ID, DATA_PROVISIONING_END_DURATION_LITERAL)
                .build();

        ValidationResult result = validateLeftOperand(input, ACTION_USAGE, ODRL_PERMISSION_ATTRIBUTE);

        assertThat(result).isFailed();
        FailureAssert.assertThat(result.getFailure()).messages().anyMatch(msg -> msg.contains("is not allowed"));
    }

    @Test
    void shouldReturnFailure_whenLeftOperandNotAllowedForUsageProhibitionPolicy() {
        JsonObject input = Json.createObjectBuilder()
                .add(ID, USAGE_PURPOSE_LITERAL)
                .build();

        ValidationResult result = validateLeftOperand(input, ACTION_USAGE, ODRL_PROHIBITION_ATTRIBUTE);

        assertThat(result).isFailed();
        FailureAssert.assertThat(result.getFailure()).messages().anyMatch(msg -> msg.contains("is not allowed"));
    }

    @Test
    void shouldReturnFailure_whenLeftOperandNotAllowedForUsageObligationPolicy() {
        JsonObject input = Json.createObjectBuilder()
                .add(ID, USAGE_PURPOSE_LITERAL)
                .build();

        ValidationResult result = validateLeftOperand(input, ACTION_USAGE, ODRL_OBLIGATION_ATTRIBUTE);

        assertThat(result).isFailed();
        FailureAssert.assertThat(result.getFailure()).messages().anyMatch(msg -> msg.contains("is not allowed"));
    }

    @Test
    void shouldReturnFailure_whenLeftOperandIsCompletelyInvalid() {
        JsonObject input = Json.createObjectBuilder()
                .add(ID, "invalid-operand")
                .build();

        ValidationResult result = validateLeftOperand(input, ACTION_ACCESS, ODRL_PERMISSION_ATTRIBUTE);

        assertThat(result).isFailed();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   ", "https://example.com/" })
    void shouldReturnFailure_whenIdEndsWithSlashOrEmpty(String id) {
        JsonObject input = Json.createObjectBuilder()
                .add(ID, id)
                .build();

        ValidationResult result = validateLeftOperand(input, ACTION_ACCESS, ODRL_PERMISSION_ATTRIBUTE);

        assertThat(result).isFailed();
    }

    @Test
    void shouldReturnSuccess_whenNotMutuallyExclusiveConstraintsPresent() {
        JsonObject input = Json.createObjectBuilder()
                .add(ID, FRAMEWORK_AGREEMENT_LITERAL)
                .build();

        ValidationResult result = ConxLeftOperandValidator
                .instance(JsonObjectValidator.newValidator(), ACTION_USAGE, ODRL_PERMISSION_ATTRIBUTE, new HashSet<>(Set.of(MEMBERSHIP_LITERAL)))
                .build()
                .validate(input);

        assertThat(result.succeeded()).isTrue();
    }

    @Test
    void shouldReturnFailure_whenMutuallyExclusiveConstraintsPresent() {
        JsonObject input = Json.createObjectBuilder()
                .add(ID, WARRANTY_DURATION_MONTHS_LITERAL)
                .build();

        ValidationResult result = ConxLeftOperandValidator
                .instance(JsonObjectValidator.newValidator(), ACTION_USAGE, ODRL_PERMISSION_ATTRIBUTE, new HashSet<>(Set.of(WARRANTY_DEFINITION_LITERAL)))
                .build()
                .validate(input);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureMessages()).anyMatch(msg ->
                msg.contains("is mutually exclusive"));
    }

    @ParameterizedTest
    @ValueSource(strings = { ACTION_ACCESS, ACTION_USAGE })
    void shouldReturnSuccess_whenAbacLeftOperandUsedInPermission(String policyType) {
        JsonObject input = Json.createObjectBuilder().add(ID, ABAC_LEFT_OPERAND).build();

        ValidationResult result = validateLeftOperand(input, policyType, ODRL_PERMISSION_ATTRIBUTE);

        assertThat(result).isSucceeded();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://w3id.org/constructx/credentials/v1.0/Baustelle123Credential.credentialSubject.accessLevels[0].role",
            "https://my-domain.com:8080/some/arbitrary/path/Foo.credentialSubject.nestedObject.fooLevel"
    })
    void shouldReturnSuccess_whenAbacLeftOperandHasNestedPathOrPort(String leftOperand) {
        JsonObject input = Json.createObjectBuilder().add(ID, leftOperand).build();

        assertThat(validateLeftOperand(input, ACTION_ACCESS, ODRL_PERMISSION_ATTRIBUTE)).isSucceeded();
        assertThat(validateLeftOperand(input, ACTION_USAGE, ODRL_PERMISSION_ATTRIBUTE)).isSucceeded();
    }

    @ParameterizedTest
    @ValueSource(strings = { ODRL_PROHIBITION_ATTRIBUTE, ODRL_OBLIGATION_ATTRIBUTE })
    void shouldReturnFailure_whenAbacLeftOperandUsedOutsidePermission(String ruleType) {
        // basic-abac only evaluates permissions, so anywhere else the constraint would validate but never be enforced
        JsonObject input = Json.createObjectBuilder().add(ID, ABAC_LEFT_OPERAND).build();

        assertThat(validateLeftOperand(input, ACTION_ACCESS, ruleType)).isFailed();
        assertThat(validateLeftOperand(input, ACTION_USAGE, ruleType)).isFailed();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // HTTP instead of HTTPS
            "http://w3id.org/constructx/credentials/v1.0/Foo.credentialSubject.fooLevel",
            // no .credentialSubject segment
            "https://w3id.org/constructx/credentials/v1.0/Foo.fooLevel",
            // nothing after .credentialSubject
            "https://w3id.org/constructx/credentials/v1.0/Foo.credentialSubject",
            // credential type does not start with a capital letter
            "https://w3id.org/constructx/credentials/v1.0/foo.credentialSubject.fooLevel",
            // not a credential attribute at all
            "https://example.com/UnknownOperand"
    })
    void shouldReturnFailure_whenLeftOperandDoesNotMatchAbacPattern(String leftOperand) {
        JsonObject input = Json.createObjectBuilder().add(ID, leftOperand).build();

        ValidationResult result = validateLeftOperand(input, ACTION_ACCESS, ODRL_PERMISSION_ATTRIBUTE);

        assertThat(result).isFailed();
        FailureAssert.assertThat(result.getFailure()).messages().anyMatch(msg ->
                msg.contains("is not allowed") && msg.contains("credentialSubject"));
    }

    @Test
    void shouldNotMentionAbacInFailure_whenRuleTypeCannotUseIt() {
        JsonObject input = Json.createObjectBuilder().add(ID, "https://example.com/UnknownOperand").build();

        ValidationResult result = validateLeftOperand(input, ACTION_USAGE, ODRL_PROHIBITION_ATTRIBUTE);

        assertThat(result).isFailed();
        FailureAssert.assertThat(result.getFailure()).messages().noneMatch(msg -> msg.contains("credentialSubject"));
    }

    @Test
    void shouldRecordAbacLeftOperand_asEncounteredConstraint() {
        // the usage policy validator relies on this to see that a permission carries a credential attribute
        var encountered = new HashSet<String>();
        JsonObject input = Json.createObjectBuilder().add(ID, ABAC_LEFT_OPERAND).build();

        var result = ConxLeftOperandValidator.instance(JsonObjectValidator.newValidator(), ACTION_USAGE, ODRL_PERMISSION_ATTRIBUTE, encountered)
                .build()
                .validate(input);

        assertThat(result).isSucceeded();
        assertThat(encountered).containsExactly(ABAC_LEFT_OPERAND);
    }

    private ValidationResult validateLeftOperand(JsonObject input, String policyType, String ruleType) {
        return ConxLeftOperandValidator.instance(JsonObjectValidator.newValidator(), policyType, ruleType)
                .build()
                .validate(input);
    }
}
