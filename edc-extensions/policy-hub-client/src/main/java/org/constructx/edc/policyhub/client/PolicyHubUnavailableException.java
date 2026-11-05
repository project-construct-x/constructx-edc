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
 * Thrown when the Policy Hub as a whole cannot be reached: a connection failure or timeout, or an
 * HTTP {@code 5xx}, {@code 408} or {@code 429} response. Such a failure affects every policy alike,
 * unlike a failure tied to a single policy (e.g. a {@code 404}), which is reported as a failed
 * {@link org.eclipse.edc.spi.result.Result} instead.
 */
public class PolicyHubUnavailableException extends EdcException {

    private final String reason;

    public PolicyHubUnavailableException(String reason) {
        super("Policy Hub unreachable (%s)".formatted(reason));
        this.reason = reason;
    }

    /**
     * Short cause, e.g. {@code Connection refused} or {@code status 503}.
     */
    public String getReason() {
        return reason;
    }
}
