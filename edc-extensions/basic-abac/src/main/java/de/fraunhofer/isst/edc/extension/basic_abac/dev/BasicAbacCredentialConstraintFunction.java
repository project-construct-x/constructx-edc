package de.fraunhofer.isst.edc.extension.basic_abac.dev;

import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.eclipse.edc.policy.engine.spi.DynamicAtomicConstraintRuleFunction;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;

import java.util.List;
import java.util.function.Predicate;

import static de.fraunhofer.isst.edc.extension.basic_abac.dev.BasicAbacUtils.BASIC_ABAC_PATTERN;
import static de.fraunhofer.isst.edc.extension.basic_abac.dev.BasicAbacUtils.getPathObject;
import static de.fraunhofer.isst.edc.extension.basic_abac.dev.BasicAbacUtils.truncateLastPathSegment;

public class BasicAbacCredentialConstraintFunction <C extends ParticipantAgentPolicyContext> implements DynamicAtomicConstraintRuleFunction<Permission, C> {


    @Override
    public boolean evaluate(Object leftValue, Operator operator, Object rightValue, Permission rule, C context) {
        double expectedNumber = 0;
        boolean isNumericCondition = false;
        try {
            expectedNumber = Double.parseDouble(rightValue.toString());
            isNumericCondition = true;
        } catch (Exception e) {
        }
        final double finalExpectedNumber = expectedNumber;
        Predicate<Double> numericPredicate = switch (operator) {
            case EQ -> n -> n == finalExpectedNumber;
            case NEQ -> n -> n != finalExpectedNumber;
            case GEQ -> n -> n >= finalExpectedNumber;
            case LEQ -> n -> n <= finalExpectedNumber;
            case GT -> n -> n > finalExpectedNumber;
            case LT -> n -> n < finalExpectedNumber;

            default -> n -> false;
        };

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
            return false;
        }
        if (verifiableCredentialList == null || verifiableCredentialList.isEmpty()) {
            context.reportProblem(potentialProblem);
            return false;
        }

        String credentialFullyQualifiedName = truncateLastPathSegment(leftValue);

        for (var verifiableCredential : verifiableCredentialList) {
            if (verifiableCredential.getType().contains(credentialFullyQualifiedName)) {
                for (var credentialSubject : verifiableCredential.getCredentialSubject()) {
                    var claims = credentialSubject.getClaims();
                    Object relevantClaim = getPathObject(leftValue, claims);
                    if (isNumericCondition) {
                        try {
                            double claimInteger = Double.parseDouble(relevantClaim.toString());
                            return numericPredicate.test(claimInteger);
                        } catch (Exception e) {
                        }
                    } else {
                        // can handle only equal or not equal at the moment
                        if (relevantClaim instanceof String) {
                            // probably unsafe for anything other than String type
                            return switch (operator) {
                                case EQ -> rightValue.equals(relevantClaim);
                                case NEQ -> !rightValue.equals(relevantClaim);
                                default -> false;
                            };
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean canHandle(Object leftValue) {
        if (leftValue instanceof String leftValueString) {
            return BASIC_ABAC_PATTERN.matcher(leftValueString).matches();
        }
        return false;
    }



    @Override
    public String name() {
        return this.getClass().getSimpleName() + "-Rule";
    }
}
