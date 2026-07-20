/*
 * Copyright (c) 2026 planen-bauen 4.0 GmbH
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

package org.constructx.edc.policy.evaluation.registry.registry;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Declares how a left operand is bound to DSP scopes.
 */
public sealed interface PolicyEvaluationBinding {

    /**
     * Binds left operand that starts with {@code namespace + pattern} to the given DSP scopes.
     */
    record DynamicPrefix(Pattern namespace, Pattern credSubKeyPattern, Set<String> scopes) implements PolicyEvaluationBinding {
    }

    /**
     * Binds an exact left operand to a single DSP scope.
     */
    record StaticKey(String key, String scope) implements PolicyEvaluationBinding {
    }
}
