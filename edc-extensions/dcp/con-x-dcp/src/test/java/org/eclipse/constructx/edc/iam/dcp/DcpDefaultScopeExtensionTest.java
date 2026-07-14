/********************************************************************************
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
 ********************************************************************************/

package org.eclipse.constructx.edc.iam.dcp;

import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.policy.context.request.spi.RequestCatalogPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestContractNegotiationPolicyContext;
import org.eclipse.edc.policy.context.request.spi.RequestTransferProcessPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.tractusx.edc.iam.iatp.scope.DefaultScopeExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.eclipse.constructx.edc.iam.dcp.DcpConstants.DEFAULT_SCOPES;
import static org.eclipse.constructx.edc.iam.dcp.DcpConstants.V08_DEFAULT_SCOPES;
import static org.eclipse.edc.protocol.dsp.spi.type.Dsp08Constants.DSP_SCOPE_V_08;
import static org.eclipse.edc.protocol.dsp.spi.type.Dsp2025Constants.DSP_SCOPE_V_2025_1;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(DependencyInjectionExtension.class)
class DcpDefaultScopeExtensionTest {

    private final PolicyEngine policyEngine = mock();

    @BeforeEach
    void setup(ServiceExtensionContext context) {
        context.registerService(PolicyEngine.class, policyEngine);
    }

    @Test
    void initialize(ServiceExtensionContext context, DcpDefaultScopeExtension extension) {
        extension.initialize(context);
        var scopes = new HashMap<String, Set<String>>();
        scopes.put(DSP_SCOPE_V_08, V08_DEFAULT_SCOPES);
        scopes.put(DSP_SCOPE_V_2025_1, DEFAULT_SCOPES);

        verify(policyEngine).registerPostValidator(eq(RequestCatalogPolicyContext.class), argThat(new ScopeMatcher(scopes)));
        verify(policyEngine).registerPostValidator(eq(RequestContractNegotiationPolicyContext.class), argThat(new ScopeMatcher(scopes)));
        verify(policyEngine).registerPostValidator(eq(RequestTransferProcessPolicyContext.class), argThat(new ScopeMatcher(scopes)));
    }

    private record ScopeMatcher(Map<String, Set<String>> expectedScopes) implements ArgumentMatcher<DefaultScopeExtractor> {

        @Override
        public boolean matches(DefaultScopeExtractor defaultScopeExtractor) {
            Map<String, Set<String>> defaultScopes = defaultScopeExtractor.defaultScopes();
            return defaultScopes.entrySet().stream()
                    .allMatch((Map.Entry<String, Set<String>> entry) -> {
                        Set<String> expectedSet = expectedScopes.get(entry.getKey());
                        return expectedSet != null && entry.getValue().containsAll(expectedSet);
                    });
        }
    }
}
