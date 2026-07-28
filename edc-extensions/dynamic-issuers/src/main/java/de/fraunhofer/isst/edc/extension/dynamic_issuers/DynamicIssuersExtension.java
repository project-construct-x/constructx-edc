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

package de.fraunhofer.isst.edc.extension.dynamic_issuers;

import org.eclipse.edc.iam.verifiablecredentials.spi.validation.TrustedIssuerRegistry;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.net.URI;

@Extension("Dynamic Issuers Registry Extension")
public class DynamicIssuersExtension implements ServiceExtension {

    public static final String DEFAULT_CON_X_TRUSTSERVER = "https://con-x.org/trustedissuers";
    public static final long DEFAULT_UPDATE_INTERVAL = 7200L; // 2 hours

    @Setting(description = "The trust server URL to be used", required = false, key = "edc.iam.trustserver.url")
    private String trustServerUrl;

    @Setting(description = "The default interval between calls to the trust server", required = false, key = "edc.iam.trustserver.default.interval")
    private String trustServerDefaultInterval;

    @Provider
    public TrustedIssuerRegistry provideDynamicTrustedIssuerRegistry(ServiceExtensionContext context) {
        var localMonitor = context.getMonitor().withPrefix(this.getClass().getSimpleName());
        URI serverUri;
        try {
            serverUri = URI.create(trustServerUrl);
        } catch (Exception e) {
            localMonitor.warning("Could not parse value of edc.iam.trustserver.url: " + trustServerUrl);
            serverUri = URI.create(DEFAULT_CON_X_TRUSTSERVER);
        }
        localMonitor.info("Using Trust Server: " + trustServerUrl);

        long defaultInterval;
        try {
            defaultInterval = Long.parseLong(trustServerDefaultInterval);
        } catch (NumberFormatException e) {
            defaultInterval = DEFAULT_UPDATE_INTERVAL;
        }
        localMonitor.info("Using Default Interval: " + defaultInterval + " seconds");

        return new DynamicTrustedIssuerRegistry(serverUri, context.getMonitor(), defaultInterval);
    }

    @Override
    public String name() {
        return "Dynamic Issuers Registry Extension";
    }
}
