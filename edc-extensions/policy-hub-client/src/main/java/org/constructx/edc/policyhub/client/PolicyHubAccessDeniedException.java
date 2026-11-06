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

package org.constructx.edc.policyhub.client;

import org.eclipse.edc.spi.EdcException;

/**
 * Thrown when the Policy Hub rejects the connector's credentials, i.e. answers {@code 401} or {@code 403}.
 * Like {@link PolicyHubUnavailableException} this affects every policy alike, so it is not reported per policy.
 * Unlike an outage, waiting does not help: the credentials in the vault have to be fixed.
 */
public class PolicyHubAccessDeniedException extends EdcException {

    private final String reason;

    public PolicyHubAccessDeniedException(String reason) {
        super("Policy Hub rejected the credentials (%s); check the Policy Hub credentials in the vault".formatted(reason));
        this.reason = reason;
    }

    /**
     * Short cause, e.g. {@code status 401}.
     */
    public String getReason() {
        return reason;
    }
}
