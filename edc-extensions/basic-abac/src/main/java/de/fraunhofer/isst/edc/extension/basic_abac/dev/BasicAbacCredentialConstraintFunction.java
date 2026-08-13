/*
 * Copyright (c) 2026 Fraunhofer-Gesellschaft zur Foerderung der angewandten Forschung e.V. (represented by Fraunhofer ISST)
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

package de.fraunhofer.isst.edc.extension.basic_abac.dev;

import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.eclipse.edc.policy.engine.spi.DynamicAtomicConstraintRuleFunction;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static de.fraunhofer.isst.edc.extension.basic_abac.dev.BasicAbacUtils.*;

public class BasicAbacCredentialConstraintFunction<C extends ParticipantAgentPolicyContext> implements DynamicAtomicConstraintRuleFunction<Permission, C> {

    @Override
    public boolean evaluate(Object leftValue, Operator operator, Object rightValue, Permission rule, C context) {
        if (leftValue == null || rightValue == null || operator == null || context == null) {
            return false;
        }

        // case: is rightValue a list?
        List<?> rightValueList;
        if (rightValue instanceof List<?> list) {
            rightValueList = normalizeNumericToDouble(list);
        } else {
            rightValueList = convertJsonToList(rightValue.toString());
        }
        if (rightValueList != null) {
            return handleRightValueList(leftValue, operator, rightValueList, rule, context);
        }


        // case: is rightValue a (single) numeric?
        try {
            double numericValue = Double.parseDouble(rightValue.toString());
            return handleRightValueNumeric(leftValue, operator, numericValue, rule, context);
        } catch (NumberFormatException e) {
        }

        // case: rightValue is neither list nor numeric, but could be e.g. a Boolean or non-numeric String value
        // or some unexpected type...
        Object claimValue = extractValueFromCredentialSubject(context, leftValue);
        if (claimValue == null) return false;

        return switch (operator) {
            case EQ -> rightValue.equals(claimValue);
            case NEQ -> !rightValue.equals(claimValue);
            case HAS_PART -> claimValue instanceof List<?> claimList && claimList.contains(rightValue);
            default -> false;
        };
    }

    @Override
    public boolean canHandle(Object leftValue) {
        return leftValue instanceof String leftValueString && BASIC_ABAC_PATTERN.matcher(leftValueString).matches();
    }

    private boolean handleRightValueList(Object leftValue, Operator operator, List<?> rightValueList, Permission rule, C context) {
        Object claimValue = extractValueFromCredentialSubject(context, leftValue);
        if (claimValue == null) return false;

        return switch (operator) {
            case IN -> !(claimValue instanceof List<?>) && rightValueList.contains(claimValue);

            case IS_ANY_OF -> {
                if (claimValue instanceof List<?> claimList) {
                    yield !Collections.disjoint(claimList, rightValueList);
                }
                yield rightValueList.contains(claimValue);
            }

            case IS_ALL_OF -> {
                if (rightValueList.isEmpty()) {
                    yield true;
                }
                if (claimValue instanceof List<?> claimList) {
                    yield claimList.containsAll(rightValueList);
                }
                yield rightValueList.size() == 1
                        && rightValueList.contains(claimValue);
            }

            case IS_NONE_OF -> {
                if (claimValue instanceof List<?> claimList) {
                    yield Collections.disjoint(claimList, rightValueList);
                }
                yield !rightValueList.contains(claimValue);
            }

            case HAS_PART -> {
                if (claimValue instanceof List<?> claimList) {
                    yield claimList.containsAll(rightValueList);
                }
                yield false;
            }

            case EQ -> claimValue instanceof List<?> claimList && listsEqualAsSets(claimList, rightValueList);

            case NEQ -> !(claimValue instanceof List<?> claimList) || !listsEqualAsSets(claimList, rightValueList);

            default -> false;
        };
    }

    private boolean listsEqualAsSets(List<?> left, List<?> right) {
        return left.containsAll(right) && right.containsAll(left);
    }

    private boolean handleRightValueNumeric(Object leftValue, Operator operator, double expectedNumber, Permission rule, C context) {
        Object valueFromCredentialClaims = extractValueFromCredentialSubject(context, leftValue);
        try {
            if (valueFromCredentialClaims instanceof List<?> claimList && operator.equals(Operator.HAS_PART)) {
                return normalizeNumericToDouble(claimList).contains(expectedNumber);
            }

            double numericFromCredentialClaims = Double.parseDouble(valueFromCredentialClaims.toString());
            return switch (operator) {
                case EQ -> numericFromCredentialClaims == expectedNumber;
                case NEQ -> numericFromCredentialClaims != expectedNumber;
                case GEQ -> numericFromCredentialClaims >= expectedNumber;
                case LEQ -> numericFromCredentialClaims <= expectedNumber;
                case GT -> numericFromCredentialClaims > expectedNumber;
                case LT -> numericFromCredentialClaims < expectedNumber;
                default -> false;
            };
        } catch (Exception e) {
            return false;
        }
    }

    private @Nullable Object extractValueFromCredentialSubject(C context, Object leftValue) {
        var verifiableCredentialList = getVerifiableCredentialList(context);
        if (verifiableCredentialList == null) return null;
        String requiredCredentialType = truncateLastPathSegment(leftValue);
        for (var credential : verifiableCredentialList) {
            if (credential.getType() == null || !credential.getType().contains(requiredCredentialType)) {
                continue;
            }
            for (var credentialSubject : credential.getCredentialSubject()) {
                Object value = getPathObject(leftValue, credentialSubject.getClaims());
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static <C extends ParticipantAgentPolicyContext> @Nullable List<VerifiableCredential> getVerifiableCredentialList(C context) {
        List<VerifiableCredential> verifiableCredentialList = null;
        String potentialProblem = "No Credential Claims found";
        try {
            var participantAgent = context.participantAgent();
            var supposedToBeCredentialList = participantAgent.getClaims().get("vc");
            if (supposedToBeCredentialList instanceof List<?> credentialList) {
                if (credentialList.stream().allMatch(it -> it instanceof VerifiableCredential)) {
                    verifiableCredentialList = (List<VerifiableCredential>) credentialList;
                }
            }
        } catch (Exception e) {
            context.reportProblem(potentialProblem);
            return null;
        }
        if (verifiableCredentialList == null || verifiableCredentialList.isEmpty()) {
            context.reportProblem(potentialProblem);
            return null;
        }
        return verifiableCredentialList;
    }

}
