/*
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
 */

package org.constructx.edc.policy.validator;

import jakarta.json.JsonObject;
import org.eclipse.edc.validator.jsonobject.JsonLdPath;
import org.eclipse.edc.validator.jsonobject.JsonObjectValidator;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.eclipse.edc.validator.spi.Validator;
import org.eclipse.tractusx.edc.policy.cx.validator.TypedMandatoryArray;

import java.util.HashSet;
import java.util.Set;

import static org.constructx.edc.policy.validator.ConxPolicyValidationConstants.ALLOWED_LOGICAL_CONSTRAINTS;
import static org.constructx.edc.policy.validator.ConxPolicyValidationConstants.NOT_ALLOWED_LOGICAL_CONSTRAINTS;
import static org.eclipse.edc.validator.spi.Violation.violation;

/**
 * Construct-X copy of {@link org.eclipse.tractusx.edc.policy.cx.validator.LogicalConstraintValidator}, delegating
 * nested constraints to {@link ConxConstraintValidator}. Allows {@code odrl:or} in addition to {@code odrl:and}
 * (see {@link ConxPolicyValidationConstants#ALLOWED_LOGICAL_CONSTRAINTS}).
 */
public class ConxLogicalConstraintValidator implements Validator<JsonObject> {
    private final JsonLdPath path;
    private final String policyType;
    private final String ruleType;
    private final Set<String> encounteredConstraints;

    private ConxLogicalConstraintValidator(JsonLdPath path, String policyType, String ruleType, Set<String> encounteredConstraints) {
        this.path = path;
        this.policyType = policyType;
        this.ruleType = ruleType;
        this.encounteredConstraints = encounteredConstraints;
    }

    public static ConxLogicalConstraintValidator instance(JsonLdPath path, String policyType, String ruleType) {
        return new ConxLogicalConstraintValidator(path, policyType, ruleType, new HashSet<>());
    }

    public static ConxLogicalConstraintValidator instance(JsonLdPath path, String policyType, String ruleType, Set<String> encounteredConstraints) {
        return new ConxLogicalConstraintValidator(path, policyType, ruleType, encounteredConstraints);
    }

    @Override
    public ValidationResult validate(JsonObject input) {
        var invalidConstraintsResult = validateNoInvalidConstraints(input);
        if (invalidConstraintsResult.failed()) {
            return invalidConstraintsResult;
        }

        var logicalConstraints = input.keySet().stream()
                .filter(ALLOWED_LOGICAL_CONSTRAINTS::contains)
                .toList();
        if (logicalConstraints.size() != 1) {
            return ValidationResult.failure(
                    violation("Policy must have exactly one logical constraint: " + logicalConstraints,
                            path.toString())
            );
        }
        return validateLogicalConstraintContent(input, logicalConstraints.get(0));
    }

    private ValidationResult validateLogicalConstraintContent(JsonObject input, String logicalConstraint) {
        var arrayValidationResult = validateLogicalConstraintIsArray(input, logicalConstraint);
        if (arrayValidationResult.failed()) {
            return arrayValidationResult;
        }
        return JsonObjectValidator.newValidator()
                .verifyArrayItem(logicalConstraint, b -> ConxConstraintValidator.instance(b, policyType, ruleType, encounteredConstraints))
                .build()
                .validate(input);
    }

    private ValidationResult validateLogicalConstraintIsArray(JsonObject input, String logicalConstraint) {
        return JsonObjectValidator.newValidator()
                .verify(logicalConstraint, TypedMandatoryArray::new)
                .build()
                .validate(input);
    }

    private ValidationResult validateNoInvalidConstraints(JsonObject input) {
        var invalidLogicalConstraints = input.keySet().stream()
                .filter(NOT_ALLOWED_LOGICAL_CONSTRAINTS::contains)
                .toList();

        if (!invalidLogicalConstraints.isEmpty()) {
            return ValidationResult.failure(
                    violation("Policy includes not allowed logical constraints: " + invalidLogicalConstraints, path.toString())
            );
        }
        return ValidationResult.success();
    }
}
