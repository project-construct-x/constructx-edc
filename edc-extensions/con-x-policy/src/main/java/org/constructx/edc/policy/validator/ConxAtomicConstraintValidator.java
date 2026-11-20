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
import org.eclipse.edc.validator.jsonobject.validators.MandatoryObject;
import org.eclipse.edc.validator.jsonobject.validators.MandatoryValue;
import org.eclipse.edc.validator.jsonobject.validators.OptionalIdNotBlank;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.eclipse.edc.validator.spi.Validator;

import java.util.HashSet;
import java.util.Set;

import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_LEFT_OPERAND_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_OPERATOR_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_RIGHT_OPERAND_ATTRIBUTE;

/**
 * Construct-X copy of {@link org.eclipse.tractusx.edc.policy.cx.validator.AtomicConstraintValidator}, delegating
 * left-operand validation to {@link ConxLeftOperandValidator}.
 */
public class ConxAtomicConstraintValidator implements Validator<JsonObject> {
    private final JsonLdPath path;
    private final String policyType;
    private final String ruleType;
    private final Set<String> encounteredConstraints;

    private ConxAtomicConstraintValidator(JsonLdPath path, String policyType, String ruleType, Set<String> encounteredConstraints) {
        this.path = path;
        this.policyType = policyType;
        this.ruleType = ruleType;
        this.encounteredConstraints = encounteredConstraints;
    }

    public static ConxAtomicConstraintValidator instance(JsonLdPath path, String policyType, String ruleType, Set<String> encounteredConstraints) {
        return new ConxAtomicConstraintValidator(path, policyType, ruleType, encounteredConstraints);
    }

    public static ConxAtomicConstraintValidator instance(JsonLdPath path, String policyType, String ruleType) {
        return new ConxAtomicConstraintValidator(path, policyType, ruleType, new HashSet<>());
    }

    @Override
    public ValidationResult validate(JsonObject input) {
        return JsonObjectValidator.newValidator()
                .verify(ODRL_LEFT_OPERAND_ATTRIBUTE, MandatoryObject::new)
                .verifyObject(ODRL_LEFT_OPERAND_ATTRIBUTE, b -> ConxLeftOperandValidator.instance(b, policyType, ruleType, encounteredConstraints))
                .verify(ODRL_OPERATOR_ATTRIBUTE, MandatoryObject::new)
                .verifyObject(ODRL_OPERATOR_ATTRIBUTE, b -> b.verifyId(OptionalIdNotBlank::new))
                .verify(ODRL_RIGHT_OPERAND_ATTRIBUTE, MandatoryValue::new)
                .build()
                .validate(input);
    }
}
