package de.fraunhofer.isst.edc.extension.basic_abac.dev;

import org.eclipse.edc.policy.context.request.spi.RequestPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyValidatorRule;
import org.eclipse.edc.policy.model.*;
import org.eclipse.edc.spi.monitor.Monitor;

import java.util.HashSet;
import java.util.Set;

import static de.fraunhofer.isst.edc.extension.basic_abac.dev.BasicAbacUtils.*;

public class BasicAbacPolicyPostValidator<C extends RequestPolicyContext> implements PolicyValidatorRule<C> {
    private final Monitor monitor;

    public BasicAbacPolicyPostValidator(Monitor monitor) {
        this.monitor = monitor.withPrefix(this.getClass().getSimpleName());
    }


    @Override
    public Boolean apply(Policy policy, C requestPolicyContext) {
        var foundAbacCredentialTypeIdentifiers = explorePolicy(policy);
        requestPolicyContext.requestScopeBuilder().scopes(foundAbacCredentialTypeIdentifiers);
        monitor.debug("Found credential type identifiers " + foundAbacCredentialTypeIdentifiers);

        // Minimally require CON-X Membership Credential
        requestPolicyContext.requestScopeBuilder().scope(CONX_MEMBERSHIP_SCOPE);
        return true;
    }

    private Set<String> explorePolicy(Policy policy) {
        Set<String> output = new HashSet<>();
        for (var permission : policy.getPermissions()) {
            for (var constraint : permission.getConstraints()) {
                output.addAll(exploreConstraint(constraint));
            }
        }
        return output;
    }

    private Set<String> exploreConstraint(Constraint constraint) {
        Set<String> output = new HashSet<>();
        if (constraint instanceof MultiplicityConstraint multiplicityConstraint) {
            for (var nestedConstraint : multiplicityConstraint.getConstraints()) {
                output.addAll(exploreConstraint(nestedConstraint));
            }
        } else if (constraint instanceof AtomicConstraint atomicConstraint) {
            if (atomicConstraint.getLeftExpression() instanceof LiteralExpression literalExpression) {
                if (isCredentialConstraint(literalExpression.getValue())) {
                    output.add("org.eclipse.dspace.dcp.vc.type:" + truncateLastPathSegment(literalExpression.getValue()) + ":read");
                }
            }
        }
        return output;
    }

    private boolean isCredentialConstraint(Object leftExpression) {
        if (leftExpression instanceof String leftExpressionString) {
            return BASIC_ABAC_PATTERN.matcher(leftExpressionString).matches();
        }
        return false;
    }


    @Override
    public String name() {
        return this.getClass().getName() + "-Rule";
    }
}
