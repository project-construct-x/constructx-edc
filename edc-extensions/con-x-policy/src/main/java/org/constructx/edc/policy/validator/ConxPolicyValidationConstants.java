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

import static org.eclipse.tractusx.edc.edr.spi.CoreConstants.CX_POLICY_2025_09_NS;
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

    // Upstream only allows DataUsageStartDate/DataUsageEndDate on CONTRACT (usage) policies.
    public static final Set<String> ACCESS_PERMISSION_POLICY_ALLOWED_LEFT_OPERANDS = union(
            PolicyValidationConstants.ACCESS_PERMISSION_POLICY_ALLOWED_LEFT_OPERANDS,
            DATA_USAGE_START_DATE_LITERAL,
            DATA_USAGE_END_DATE_LITERAL
    );

    public static final Set<String> USAGE_PERMISSION_POLICY_ALLOWED_LEFT_OPERANDS = union(
            PolicyValidationConstants.USAGE_PERMISSION_POLICY_ALLOWED_LEFT_OPERANDS,
            DATA_USAGE_START_DATE_LITERAL
    );

    private static Set<String> union(Set<String> base, String... additions) {
        return Stream.concat(base.stream(), Stream.of(additions)).collect(Collectors.toUnmodifiableSet());
    }
}
