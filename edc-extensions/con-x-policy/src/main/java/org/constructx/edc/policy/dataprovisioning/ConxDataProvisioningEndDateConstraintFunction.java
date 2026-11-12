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

package org.constructx.edc.policy.dataprovisioning;

import org.constructx.edc.policy.common.ConxAbstractDataEndDateConstraintFunction;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.AgreementPolicyContext;
import org.eclipse.edc.policy.model.Duty;

/**
 * Construct-X replacement for {@link org.eclipse.tractusx.edc.policy.cx.dataprovisioning.DataProvisioningEndDateConstraintFunction},
 * registered under the same left operand ({@code DataProvisioningEndDate}).
 */
public class ConxDataProvisioningEndDateConstraintFunction<C extends AgreementPolicyContext> extends ConxAbstractDataEndDateConstraintFunction<Duty, C> {
}
