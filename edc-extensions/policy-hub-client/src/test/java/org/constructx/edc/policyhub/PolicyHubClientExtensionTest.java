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

package org.constructx.edc.policyhub;

import org.constructx.edc.policyhub.api.PolicyHubApiController;
import org.eclipse.edc.boot.system.injection.ObjectFactory;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.ConfigFactory;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies the "no Policy Hub URL configured" contract documented in the module README: the import
 * API must be registered when {@code constructx.policyhub.url} is set, and the extension must stay
 * passive - not fail - when it is unset (the consumer-side deployment case).
 */
@ExtendWith(DependencyInjectionExtension.class)
class PolicyHubClientExtensionTest {

    private final WebService webService = mock();
    private final TypeTransformerRegistry transformerRegistry = mock();

    @BeforeEach
    void setUp(ServiceExtensionContext context) {
        context.registerService(WebService.class, webService);
        context.registerService(TypeTransformerRegistry.class, transformerRegistry);
        when(transformerRegistry.forContext("management-api")).thenReturn(transformerRegistry);
    }

    @Test
    void initialize_whenPolicyHubUrlConfigured_shouldRegisterImportApi(ServiceExtensionContext context, ObjectFactory factory) {
        when(context.getConfig()).thenReturn(ConfigFactory.fromMap(
                Map.of("constructx.policyhub.url", "https://policy-hub.example.com")));

        var extension = factory.constructInstance(PolicyHubClientExtension.class);
        extension.initialize(context);

        verify(webService).registerResource(eq(ApiContext.MANAGEMENT), isA(PolicyHubApiController.class));
        verify(webService).registerDynamicResource(eq(ApiContext.MANAGEMENT), eq(PolicyHubApiController.class), any());
    }

    @Test
    void initialize_whenPolicyHubUrlNotConfigured_shouldStayPassiveWithoutFailing(ServiceExtensionContext context, ObjectFactory factory) {
        // no 'constructx.policyhub.url' in config - mirrors a consumer-side deployment
        var extension = factory.constructInstance(PolicyHubClientExtension.class);

        extension.initialize(context);

        verifyNoInteractions(webService);
        verify(context.getMonitor()).info("No Policy Hub URL configured ('constructx.policyhub.url'), the Policy Hub import API is disabled");
    }
}
