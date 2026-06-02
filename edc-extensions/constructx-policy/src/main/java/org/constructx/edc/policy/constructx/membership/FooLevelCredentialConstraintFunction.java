package org.constructx.edc.policy.constructx.membership;

import org.constructx.edc.policy.constructx.common.AbstractDynamicCredentialConstraintFunction;
import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.tractusx.edc.core.utils.credentials.CredentialTypePredicate;

import java.util.function.Predicate;

import static org.constructx.edc.policy.constructx.ConstructxPolicyConstants.CONSTRUCTX_CREDENTIAL_NS;


public class FooLevelCredentialConstraintFunction<C extends ParticipantAgentPolicyContext> extends AbstractDynamicCredentialConstraintFunction<C> {

    public static final String FOO_CRED = "FooCredential";

    private final Monitor monitor;

    public FooLevelCredentialConstraintFunction(Monitor monitor) {
        this.monitor = monitor.withPrefix(this.getClass().getSimpleName());
    }

    @Override
    public boolean evaluate(Object leftOperand, Operator operator, Object rightOperand, Permission rule, C context) {
        final int fooLevel;
        try {
            fooLevel = Integer.parseInt(rightOperand.toString());
        } catch (Exception e) {
            context.reportProblem("rightOperand must be parseable as an integer");
            return false;
        }

        Predicate<Integer> testPredicate = switch (operator) {
            case EQ -> n -> n == fooLevel;
            case NEQ -> n -> n != fooLevel;
            case GEQ -> n -> n >= fooLevel;
            case LEQ -> n -> n <= fooLevel;
            case GT -> n -> n > fooLevel;
            case LT -> n -> n < fooLevel;

            default -> n -> false;
        };

        var participantAgent = extractParticipantAgent(context);
        if (participantAgent.failed()) {
            context.reportProblem(participantAgent.getFailureDetail());
            return false;
        }

        var credentialResult = getCredentialList(participantAgent.getContent());
        if (credentialResult.failed()) {
            context.reportProblem(credentialResult.getFailureDetail());
            return false;
        }
        boolean expectedFooLevelSatisfied = false;
        for (var cr : credentialResult.getContent()) {
            if (cr.getType().contains("FooCredential")) {
                for (var claim : cr.getCredentialSubject()) {
                    if (claim.getClaims().containsKey("fooLevel")) {
                        try {
                            int provenFooLevel = Integer.parseInt(claim.getClaims().get("fooLevel").toString());
                            boolean test = testPredicate.test(provenFooLevel);
                            if (test) {
                                monitor.info("Expectation satisfied!");
                            } else {
                                monitor.info("Expected fooLevel " + fooLevel + ", but found " + provenFooLevel);
                            }
                            expectedFooLevelSatisfied = expectedFooLevelSatisfied || test;
                        } catch (Exception ignored){
                        }
                    }
                }
            }
        }

        if (!expectedFooLevelSatisfied) {
            monitor.info("Rejecting because expected fooLevel condition not met: " + operator + " " + fooLevel);
            return false;
        }

        return credentialResult.getContent().stream()
                .anyMatch(new CredentialTypePredicate(CONSTRUCTX_CREDENTIAL_NS, FOO_CRED));
    }

    @Override
    public boolean canHandle(Object leftOperand) {
        return "https://w3id.org/constructx/policy/v1.0/Foo.fooLevel".equals(leftOperand);
    }


    @Override
    public String name() {
        return this.getClass().getName();
    }
}
