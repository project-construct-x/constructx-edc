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

import java.util.Set;

import static java.lang.String.format;

public final class DcpConstants {

    public static final String POLICY_NS = "https://w3id.org/constructx/policy/v1.0/";

    public static final String CREDENTIAL_TYPE_NAMESPACE = "org.constructx.dspace.dcp.vc.type";
    public static final String MEMBERSHIP_CREDENTIAL = "MembershipCredential";
    public static final String READ_OPERATION = "read";
    public static final String MEMBERSHIP_SCOPE = format("%s:%s:%s", CREDENTIAL_TYPE_NAMESPACE, MEMBERSHIP_CREDENTIAL, READ_OPERATION);
    public static final Set<String> DEFAULT_SCOPES = Set.of(MEMBERSHIP_SCOPE);
    public static final Set<String> V08_DEFAULT_SCOPES = Set.of(MEMBERSHIP_SCOPE);

    private DcpConstants() {
    }
}
