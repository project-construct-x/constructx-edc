package org.eclipse.tractusx.edc.iam.iatp.scope;

import org.eclipse.edc.policy.context.request.spi.RequestPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyValidatorRule;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.monitor.Monitor;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;


public class ConXTestScopeExtractor<C extends RequestPolicyContext> implements PolicyValidatorRule<C> {

    private final Monitor monitor;

    private static final String CONX_SCOPE = "org.eclipse.dspace.dcp.vc.type:ConstructXMembershipCredential:read";
    private static final String FOO_SCOPE = "org.eclipse.dspace.dcp.vc.type:FooCredential:read";

    public ConXTestScopeExtractor(Monitor monitor) {
        this.monitor = monitor.withPrefix(this.getClass().getSimpleName());
    }

    @Override
    public Boolean apply(Policy policy, C requestPolicyContext) {
        Set<String> scope = new HashSet<>();
        scope.add(CONX_SCOPE);
        for (org.eclipse.edc.policy.model.Permission permission : policy.getPermissions()) {
            for (var constraint : permission.getConstraints()) {
                if (constraint.toString().contains("https://w3id.org/constructx/policy/v1.0/Foo")) {
                    scope.add(FOO_SCOPE);
                    monitor.info("Adding FooCredential requirement");
                }
            }
        }
        monitor.info("Setting the following scopes " + scope);
        requestPolicyContext.requestScopeBuilder().scopes(scope);

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
