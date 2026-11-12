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

import org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.fraunhofer.isst.edc.extension.basic_abac.dev.BasicAbacUtils.BASIC_ABAC_PATTERN;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_AND_CONSTRAINT_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_AND_SEQUENCE_CONSTRAINT_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_OR_CONSTRAINT_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_XONE_CONSTRAINT_ATTRIBUTE;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.tractusx.edc.edr.spi.CoreConstants.CX_POLICY_2025_09_NS;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.BUSINESS_PARTNER_GROUP_LITERAL;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.BUSINESS_PARTNER_NUMBER_LITERAL;
import static org.eclipse.tractusx.edc.policy.cx.validator.PolicyValidationConstants.DATA_USAGE_END_DATE_LITERAL;

/**
 * Construct-X additions to {@link PolicyValidationConstants}. Only the allowed left-operand sets that differ from
 * upstream are redefined here; everything else is used from {@link PolicyValidationConstants} directly.
 */
public final class ConxPolicyValidationConstants {
    private ConxPolicyValidationConstants() {
    }

    // No DataUsageStartDate exists anywhere upstream (checked up to tractusx-edc 0.13.0), added here to pair with
    // DataUsageEndDate for the Construct-X Policy Hub's DATE_RANGE constraint.
    public static final String DATA_USAGE_START_DATE_LITERAL = CX_POLICY_2025_09_NS + "DataUsageStartDate";

    // Construct-X Policy Hub's "Participant selection" constraint, using EDC's own left operand (not Catena-X
    // namespaced, like INFORCE_POLICY_LITERAL above). EDC core defines this left operand but implements no
    // evaluation semantics for it; evaluation is provided by ConnectorConstraintFunction (no upstream equivalent).
    public static final String CONNECTOR_LITERAL = EDC_NAMESPACE + "connector";

    // Construct-X Policy Hub's "DataSubset" constraint (Construct-X namespace, no upstream equivalent), allowed in
    // ACCESS and CONTRACT permissions. Evaluation is a placeholder, see DataSubsetConstraintFunction.
    public static final String DATA_SUBSET_LITERAL = "https://w3id.org/construct-x/policy/DataSubset";

    // Upstream only allows odrl:and; Construct-X additionally allows odrl:or.
    public static final Set<String> ALLOWED_LOGICAL_CONSTRAINTS = Set.of(
            ODRL_AND_CONSTRAINT_ATTRIBUTE,
            ODRL_OR_CONSTRAINT_ATTRIBUTE
    );
    public static final Set<String> NOT_ALLOWED_LOGICAL_CONSTRAINTS = Set.of(
            ODRL_XONE_CONSTRAINT_ATTRIBUTE,
            ODRL_AND_SEQUENCE_CONSTRAINT_ATTRIBUTE
    );

    // Upstream only allows DataUsageStartDate/DataUsageEndDate on CONTRACT (usage) policies.
    // BusinessPartnerGroup/BusinessPartnerNumber are removed: they need the Catena-X BPN/BDRS services, which
    // Construct-X does not depend on, so ConxPolicyExtension registers no function for them.
    static final Set<String> ACCESS_PERMISSION_POLICY_ALLOWED_LEFT_OPERANDS = union(
            without(PolicyValidationConstants.ACCESS_PERMISSION_POLICY_ALLOWED_LEFT_OPERANDS,
                    BUSINESS_PARTNER_GROUP_LITERAL, BUSINESS_PARTNER_NUMBER_LITERAL),
            DATA_USAGE_START_DATE_LITERAL,
            DATA_USAGE_END_DATE_LITERAL,
            CONNECTOR_LITERAL,
            DATA_SUBSET_LITERAL
    );

    static final Set<String> USAGE_PERMISSION_POLICY_ALLOWED_LEFT_OPERANDS = union(
            PolicyValidationConstants.USAGE_PERMISSION_POLICY_ALLOWED_LEFT_OPERANDS,
            DATA_USAGE_START_DATE_LITERAL,
            DATA_SUBSET_LITERAL
    );

    /**
     * Whether the given left operand is a credential attribute handled by the {@code basic-abac} extension, e.g.
     * {@code https://w3id.org/constructx/credentials/v1.0/ConstructXMembershipCredential.credentialSubject.isMember}.
     * Such operands are open-ended (any credential type, any attribute path), so they cannot be listed in the allowed
     * left-operand sets; instead they are recognised by the same pattern the constraint function evaluates them with.
     * They are only evaluated in permissions, which is therefore the only place the validators accept them.
     *
     * @param leftOperand the left operand IRI of a constraint
     * @return {@code true} if it matches the {@code basic-abac} pattern
     */
    public static boolean isAbacLeftOperand(String leftOperand) {
        return leftOperand != null && BASIC_ABAC_PATTERN.matcher(leftOperand).matches();
    }

    private static Set<String> without(Set<String> base, String... removals) {
        var removed = Set.of(removals);
        return base.stream().filter(operand -> !removed.contains(operand)).collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> union(Set<String> base, String... additions) {
        return Stream.concat(base.stream(), Stream.of(additions)).collect(Collectors.toUnmodifiableSet());
    }
}
