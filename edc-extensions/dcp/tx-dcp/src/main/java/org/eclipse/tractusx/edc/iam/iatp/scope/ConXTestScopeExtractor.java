package org.eclipse.tractusx.edc.iam.iatp.scope;

import org.eclipse.edc.policy.context.request.spi.RequestPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyValidatorRule;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;


public class ConXTestScopeExtractor<C extends RequestPolicyContext> implements PolicyValidatorRule<C> {

    private final Monitor monitor;

    private static final String FX_SCOPE = "org.eclipse.dspace.dcp.vc.type:ConstructXMembershipCredential:read";
    private static final String FOO_SCOPE = "org.eclipse.dspace.dcp.vc.type:FooCredential:read";

    public ConXTestScopeExtractor(Monitor monitor) {
        this.monitor = monitor.withPrefix(this.getClass().getSimpleName());
    }

    @Override
    public Boolean apply(Policy policy, C requestPolicyContext) {
        monitor.info("RequestPolicyContext class " + requestPolicyContext.requestContext().getClass());
        monitor.info("Policy " + policy);

        for (org.eclipse.edc.policy.model.Permission permission : policy.getPermissions()) {
            StringBuilder sb = new StringBuilder();
            var perm = permission;
            sb.append("Permission ");
            sb.append(perm.getDuties()).append("\n").append(perm.getConstraints()).append("\n\n");
            monitor.info(sb.toString());
        }
        monitor.info("Policy Permissions" + policy.getPermissions());

        requestPolicyContext.requestScopeBuilder().scopes(List.of(FX_SCOPE, FOO_SCOPE));

        return true;
    }

    @Override
    public @NotNull <V> BiFunction<Policy, C, V> andThen(@NotNull Function<? super Boolean, ? extends V> after) {
        return PolicyValidatorRule.super.andThen(after);
    }

    @Override
    public String name() {
        return this.getClass().getName() + "-Rule";
    }
}
