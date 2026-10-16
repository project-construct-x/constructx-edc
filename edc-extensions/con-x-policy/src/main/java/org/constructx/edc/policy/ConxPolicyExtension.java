/*
 * Copyright (c) 2023 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 * Copyright (c) 2025 Fraunhofer-Gesellschaft zur Förderung der angewandten Forschung e.V.
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

package org.constructx.edc.policy;

import org.constructx.edc.policy.dataprovisioning.ConxDataProvisioningEndDateConstraintFunction;
import org.constructx.edc.policy.datausage.ConxDataUsageEndDateConstraintFunction;
import org.constructx.edc.policy.datausage.DataUsageStartDateConstraintFunction;
import org.constructx.edc.policy.framework.ConxFrameworkAgreementConstraintFunction;
import org.constructx.edc.policy.validator.ConxPolicyDefinitionValidator;
import org.eclipse.edc.connector.controlplane.catalog.spi.policy.CatalogPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.ContractNegotiationPolicyContext;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.TransferProcessPolicyContext;
import org.eclipse.edc.connector.policy.monitor.spi.PolicyMonitorContext;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.engine.spi.RuleBindingRegistry;
import org.eclipse.edc.policy.model.Duty;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Prohibition;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.tractusx.edc.policy.cx.CxPolicyExtension;
import org.eclipse.tractusx.edc.policy.cx.affiliates.AffiliatesBpnlPermissionConstraintFunction;
import org.eclipse.tractusx.edc.policy.cx.affiliates.AffiliatesBpnlProhibitionConstraintFunction;
import org.eclipse.tractusx.edc.policy.cx.affiliates.AffiliatesRegionPermissionConstraintFunction;
import org.eclipse.tractusx.edc.policy.cx.affiliates.AffiliatesRegionProhibitionConstraintFunction;
import org.eclipse.tractusx.edc.policy.cx.businesspartner.BusinessPartnerGroupConstraintFunction;
import org.eclipse.tractusx.edc.policy.cx.businesspartner.BusinessPartnerNumberConstraintFunction;
import org.eclipse.tractusx.edc.policy.cx.confidentialinformation.ConfidentialInformationMeasuresConstraintFunction;
import org.eclipse.tractusx.edc.policy.cx.confidentialinformation.ConfidentialInformationSharingConstraintFunction;
import org.eclipse.tractusx.edc.policy.cx.contractreference.ContractReferenceConstraintFunction;
import org.eclipse.tractusx.edc.policy.cx.contracttermination.ContractTerminationConstraintFunction;
import org.eclipse.tractusx.edc.policy.cx.datafrequency.DataFrequencyConstraintFunction;
import org.eclipse.tractusx.edc.policy.cx.dataprovisioning.DataProvisioningEndDurationDaysConstraintFunction;
import org.eclipse.tractusx.edc.policy.cx.datausage.DataUsageEndDefinitionConstraintFunction;
import org.eclipse.tractusx.edc.policy.cx.datausage.DataUsageEndDurationDaysConstraintFunction;
import org.eclipse.tractusx.edc.policy.cx.dismantler.DismantlerCredentialConstraintFunction;
import org.eclipse.tractusx.edc.policy.cx.jurisdictionlocation.JurisdictionLocationConstraintFunction;
import org.eclipse.tractusx.edc.policy.cx.jurisdictionlocation.JurisdictionLocationReferenceConstraintFunction;
import org.eclipse.tractusx.edc.policy.cx.liability.LiabilityConstraintFunction;
import org.eclipse.tractusx.edc.policy.cx.membership.MembershipCredentialConstraintFunction;
import org.eclipse.tractusx.edc.policy.cx.precedence.PrecedenceConstraintFunction;
import org.eclipse.tractusx.edc.policy.cx.usage.ExcludingUsageConstraintFunction;
import org.eclipse.tractusx.edc.policy.cx.usage.UsagePurposeConstraintFunction;
import org.eclipse.tractusx.edc.policy.cx.usage.UsageRestrictionConstraintFunction;
import org.eclipse.tractusx.edc.policy.cx.versionchange.VersionChangesConstraintFunction;
import org.eclipse.tractusx.edc.policy.cx.warranty.WarrantyConstraintFunction;
import org.eclipse.tractusx.edc.policy.cx.warranty.WarrantyDefinitionConstraintFunction;
import org.eclipse.tractusx.edc.policy.cx.warranty.WarrantyDurationMonthsConstraintFunction;
import org.eclipse.tractusx.edc.spi.identity.mapper.BdrsClient;
import org.eclipse.tractusx.edc.validation.businesspartner.spi.store.BusinessPartnerStore;

import static org.constructx.edc.policy.datausage.DataUsageStartDateConstraintFunction.DATA_USAGE_START_DATE;
import static org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition.EDC_POLICY_DEFINITION_TYPE;
import static org.eclipse.edc.connector.policy.monitor.spi.PolicyMonitorContext.POLICY_MONITOR_SCOPE;
import static org.eclipse.tractusx.edc.edr.spi.CoreConstants.CX_POLICY_2025_09_NS;
import static org.eclipse.tractusx.edc.policy.cx.affiliates.AffiliatesBpnlProhibitionConstraintFunction.AFFILIATES_BPNL;
import static org.eclipse.tractusx.edc.policy.cx.affiliates.AffiliatesRegionProhibitionConstraintFunction.AFFILIATES_REGION;
import static org.eclipse.tractusx.edc.policy.cx.businesspartner.BusinessPartnerGroupConstraintFunction.BUSINESS_PARTNER_GROUP;
import static org.eclipse.tractusx.edc.policy.cx.businesspartner.BusinessPartnerNumberConstraintFunction.BUSINESS_PARTNER_NUMBER;
import static org.eclipse.tractusx.edc.policy.cx.common.PolicyScopes.TRANSFER_PROCESS_SCOPE;
import static org.eclipse.tractusx.edc.policy.cx.confidentialinformation.ConfidentialInformationMeasuresConstraintFunction.CONFIDENTIAL_INFORMATION_MEASURES;
import static org.eclipse.tractusx.edc.policy.cx.confidentialinformation.ConfidentialInformationSharingConstraintFunction.CONFIDENTIAL_INFORMATION_SHARING;
import static org.eclipse.tractusx.edc.policy.cx.contractreference.ContractReferenceConstraintFunction.CONTRACT_REFERENCE;
import static org.eclipse.tractusx.edc.policy.cx.contracttermination.ContractTerminationConstraintFunction.CONTRACT_TERMINATION;
import static org.eclipse.tractusx.edc.policy.cx.datafrequency.DataFrequencyConstraintFunction.DATA_FREQUENCY;
import static org.eclipse.tractusx.edc.policy.cx.dataprovisioning.DataProvisioningEndDateConstraintFunction.DATA_PROVISIONING_END_DATE;
import static org.eclipse.tractusx.edc.policy.cx.dataprovisioning.DataProvisioningEndDurationDaysConstraintFunction.DATA_PROVISIONING_END_DURATION_DAYS;
import static org.eclipse.tractusx.edc.policy.cx.datausage.DataUsageEndDateConstraintFunction.DATA_USAGE_END_DATE;
import static org.eclipse.tractusx.edc.policy.cx.datausage.DataUsageEndDefinitionConstraintFunction.DATA_USAGE_END_DEFINITION;
import static org.eclipse.tractusx.edc.policy.cx.datausage.DataUsageEndDurationDaysConstraintFunction.DATA_USAGE_END_DURATION_DAYS;
import static org.eclipse.tractusx.edc.policy.cx.jurisdictionlocation.JurisdictionLocationConstraintFunction.JURISDICTION_LOCATION;
import static org.eclipse.tractusx.edc.policy.cx.jurisdictionlocation.JurisdictionLocationReferenceConstraintFunction.JURISDICTION_LOCATION_REFERENCE;
import static org.eclipse.tractusx.edc.policy.cx.liability.LiabilityConstraintFunction.LIABILITY;
import static org.eclipse.tractusx.edc.policy.cx.precedence.PrecedenceConstraintFunction.PRECEDENCE;
import static org.eclipse.tractusx.edc.policy.cx.usage.ExcludingUsageConstraintFunction.EXCLUSIVE_USAGE;
import static org.eclipse.tractusx.edc.policy.cx.usage.UsagePurposeConstraintFunction.USAGE_PURPOSE;
import static org.eclipse.tractusx.edc.policy.cx.usage.UsageRestrictionConstraintFunction.USAGE_RESTRICTION;
import static org.eclipse.tractusx.edc.policy.cx.versionchange.VersionChangesConstraintFunction.VERSION_CHANGES;
import static org.eclipse.tractusx.edc.policy.cx.warranty.WarrantyConstraintFunction.WARRANTY;
import static org.eclipse.tractusx.edc.policy.cx.warranty.WarrantyDefinitionConstraintFunction.WARRANTY_DEFINITION;
import static org.eclipse.tractusx.edc.policy.cx.warranty.WarrantyDurationMonthsConstraintFunction.WARRANTY_DURATION_MONTHS;

/**
 * Construct-X replacement for {@link CxPolicyExtension}: provides the standard CX usage policies plus the
 * Construct-X additions and fixes.
 * <p>
 * Must run <strong>instead of</strong> {@link CxPolicyExtension}, not next to it: EDC composes JSON validators and
 * policy {@code validate()} functions (all of them must pass), so the relaxed Construct-X validation would otherwise
 * still be rejected by the upstream one. The Construct-X distributions therefore remove {@link CxPolicyExtension}
 * from the merged {@code ServiceExtension} file.
 * <p>
 * Differences to upstream:
 * <ul>
 *   <li>{@link ConxPolicyDefinitionValidator} instead of the upstream policy definition validator</li>
 *   <li>{@link ConxFrameworkAgreementConstraintFunction}: subtype without version is valid</li>
 *   <li>{@link ConxDataUsageEndDateConstraintFunction}/{@link ConxDataProvisioningEndDateConstraintFunction}: LEQ and bare dates</li>
 *   <li>new {@link DataUsageStartDateConstraintFunction} (does not exist upstream)</li>
 *   <li>UsagePurpose registered for {@link CatalogPolicyContext} (upstream binds it to the catalog scope but has
 *   no function for it, which causes a NullPointerException when building the catalog)</li>
 * </ul>
 */
@Extension(ConxPolicyExtension.NAME)
public class ConxPolicyExtension implements ServiceExtension {

    public static final String NAME = "Construct-X Policy";

    private static String withCxPolicyNsPrefix(String name) {
        return CX_POLICY_2025_09_NS + name;
    }

    @Inject
    private PolicyEngine policyEngine;

    @Inject
    private RuleBindingRegistry bindingRegistry;

    @Inject
    private BusinessPartnerStore store;

    @Inject
    private BdrsClient bdrsClient;

    @Inject
    private JsonObjectValidatorRegistry validatorRegistry;

    public void registerFunctions(PolicyEngine engine) {

        // Usage Prohibition Validators
        engine.registerFunction(ContractNegotiationPolicyContext.class, Prohibition.class,
                withCxPolicyNsPrefix(AFFILIATES_BPNL), new AffiliatesBpnlProhibitionConstraintFunction<>());
        engine.registerFunction(ContractNegotiationPolicyContext.class, Prohibition.class,
                withCxPolicyNsPrefix(AFFILIATES_REGION), new AffiliatesRegionProhibitionConstraintFunction<>());
        engine.registerFunction(ContractNegotiationPolicyContext.class, Prohibition.class,
                withCxPolicyNsPrefix(USAGE_RESTRICTION), new UsageRestrictionConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Prohibition.class,
                withCxPolicyNsPrefix(AFFILIATES_BPNL), new AffiliatesBpnlProhibitionConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Prohibition.class,
                withCxPolicyNsPrefix(AFFILIATES_REGION), new AffiliatesRegionProhibitionConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Prohibition.class,
                withCxPolicyNsPrefix(USAGE_RESTRICTION), new UsageRestrictionConstraintFunction<>());

        // Usage Duty Validators
        engine.registerFunction(PolicyMonitorContext.class, Duty.class,
                withCxPolicyNsPrefix(DATA_PROVISIONING_END_DURATION_DAYS), new DataProvisioningEndDurationDaysConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Duty.class,
                withCxPolicyNsPrefix(DATA_PROVISIONING_END_DURATION_DAYS), new DataProvisioningEndDurationDaysConstraintFunction<>());
        engine.registerFunction(PolicyMonitorContext.class, Duty.class,
                withCxPolicyNsPrefix(DATA_PROVISIONING_END_DATE), new ConxDataProvisioningEndDateConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Duty.class,
                withCxPolicyNsPrefix(DATA_PROVISIONING_END_DATE), new ConxDataProvisioningEndDateConstraintFunction<>());

        // Access and Usage Permission Validators
        engine.registerFunction(CatalogPolicyContext.class, Permission.class, new DismantlerCredentialConstraintFunction<>());
        engine.registerFunction(ContractNegotiationPolicyContext.class, Permission.class, new DismantlerCredentialConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Permission.class, new DismantlerCredentialConstraintFunction<>());

        engine.registerFunction(CatalogPolicyContext.class, Permission.class, new ConxFrameworkAgreementConstraintFunction<>());
        engine.registerFunction(ContractNegotiationPolicyContext.class, Permission.class, new ConxFrameworkAgreementConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Permission.class, new ConxFrameworkAgreementConstraintFunction<>());

        engine.registerFunction(CatalogPolicyContext.class, Permission.class, new MembershipCredentialConstraintFunction<>());
        engine.registerFunction(ContractNegotiationPolicyContext.class, Permission.class, new MembershipCredentialConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Permission.class, new MembershipCredentialConstraintFunction<>());

        engine.registerFunction(CatalogPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(BUSINESS_PARTNER_GROUP), new BusinessPartnerGroupConstraintFunction<>(store, bdrsClient));
        engine.registerFunction(CatalogPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(BUSINESS_PARTNER_NUMBER), new BusinessPartnerNumberConstraintFunction<>(bdrsClient));

        // Usage Permission Validators
        engine.registerFunction(ContractNegotiationPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(AFFILIATES_BPNL), new AffiliatesBpnlPermissionConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(AFFILIATES_BPNL), new AffiliatesBpnlPermissionConstraintFunction<>());

        engine.registerFunction(ContractNegotiationPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(AFFILIATES_REGION), new AffiliatesRegionPermissionConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(AFFILIATES_REGION), new AffiliatesRegionPermissionConstraintFunction<>());

        engine.registerFunction(ContractNegotiationPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(CONTRACT_REFERENCE), new ContractReferenceConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(CONTRACT_REFERENCE), new ContractReferenceConstraintFunction<>());

        // USAGE_PURPOSE is bound to CATALOG_SCOPE (upstream) but upstream registers no function for
        // CatalogPolicyContext. Building a catalog for an offer whose policy carries a UsagePurpose constraint then
        // throws a NullPointerException in Constraint.accept(), because the engine finds a scope binding but no evaluator.
        engine.registerFunction(CatalogPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(USAGE_PURPOSE), new UsagePurposeConstraintFunction<>());
        engine.registerFunction(ContractNegotiationPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(USAGE_PURPOSE), new UsagePurposeConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(USAGE_PURPOSE), new UsagePurposeConstraintFunction<>());

        engine.registerFunction(ContractNegotiationPolicyContext.class, Permission.class, withCxPolicyNsPrefix(DATA_FREQUENCY), new DataFrequencyConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Permission.class, withCxPolicyNsPrefix(DATA_FREQUENCY), new DataFrequencyConstraintFunction<>());

        engine.registerFunction(PolicyMonitorContext.class, Permission.class, withCxPolicyNsPrefix(DATA_USAGE_END_DATE), new ConxDataUsageEndDateConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Permission.class, withCxPolicyNsPrefix(DATA_USAGE_END_DATE), new ConxDataUsageEndDateConstraintFunction<>());

        engine.registerFunction(PolicyMonitorContext.class, Permission.class, withCxPolicyNsPrefix(DATA_USAGE_START_DATE), new DataUsageStartDateConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Permission.class, withCxPolicyNsPrefix(DATA_USAGE_START_DATE), new DataUsageStartDateConstraintFunction<>());

        engine.registerFunction(ContractNegotiationPolicyContext.class, Permission.class, withCxPolicyNsPrefix(DATA_USAGE_END_DEFINITION), new DataUsageEndDefinitionConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Permission.class, withCxPolicyNsPrefix(DATA_USAGE_END_DEFINITION), new DataUsageEndDefinitionConstraintFunction<>());

        engine.registerFunction(PolicyMonitorContext.class, Permission.class, withCxPolicyNsPrefix(DATA_USAGE_END_DURATION_DAYS), new DataUsageEndDurationDaysConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Permission.class, withCxPolicyNsPrefix(DATA_USAGE_END_DURATION_DAYS), new DataUsageEndDurationDaysConstraintFunction<>());

        engine.registerFunction(ContractNegotiationPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(JURISDICTION_LOCATION), new JurisdictionLocationConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(JURISDICTION_LOCATION), new JurisdictionLocationConstraintFunction<>());

        engine.registerFunction(ContractNegotiationPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(JURISDICTION_LOCATION_REFERENCE), new JurisdictionLocationReferenceConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(JURISDICTION_LOCATION_REFERENCE), new JurisdictionLocationReferenceConstraintFunction<>());

        engine.registerFunction(ContractNegotiationPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(LIABILITY), new LiabilityConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(LIABILITY), new LiabilityConstraintFunction<>());

        engine.registerFunction(ContractNegotiationPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(EXCLUSIVE_USAGE), new ExcludingUsageConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(EXCLUSIVE_USAGE), new ExcludingUsageConstraintFunction<>());

        engine.registerFunction(ContractNegotiationPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(PRECEDENCE), new PrecedenceConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(PRECEDENCE), new PrecedenceConstraintFunction<>());

        engine.registerFunction(ContractNegotiationPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(VERSION_CHANGES), new VersionChangesConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(VERSION_CHANGES), new VersionChangesConstraintFunction<>());

        engine.registerFunction(ContractNegotiationPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(WARRANTY), new WarrantyConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(WARRANTY), new WarrantyConstraintFunction<>());

        engine.registerFunction(ContractNegotiationPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(WARRANTY_DEFINITION), new WarrantyDefinitionConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(WARRANTY_DEFINITION), new WarrantyDefinitionConstraintFunction<>());

        engine.registerFunction(ContractNegotiationPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(WARRANTY_DURATION_MONTHS), new WarrantyDurationMonthsConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(WARRANTY_DURATION_MONTHS), new WarrantyDurationMonthsConstraintFunction<>());

        engine.registerFunction(ContractNegotiationPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(CONTRACT_TERMINATION), new ContractTerminationConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(CONTRACT_TERMINATION), new ContractTerminationConstraintFunction<>());

        engine.registerFunction(ContractNegotiationPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(CONFIDENTIAL_INFORMATION_MEASURES), new ConfidentialInformationMeasuresConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(CONFIDENTIAL_INFORMATION_MEASURES), new ConfidentialInformationMeasuresConstraintFunction<>());

        engine.registerFunction(ContractNegotiationPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(CONFIDENTIAL_INFORMATION_SHARING), new ConfidentialInformationSharingConstraintFunction<>());
        engine.registerFunction(TransferProcessPolicyContext.class, Permission.class,
                withCxPolicyNsPrefix(CONFIDENTIAL_INFORMATION_SHARING), new ConfidentialInformationSharingConstraintFunction<>());
    }

    public static void registerBindings(RuleBindingRegistry registry) {
        // upstream bindings are purely additive and can be reused as they are
        CxPolicyExtension.registerBindings(registry);

        registry.bind(withCxPolicyNsPrefix(DATA_USAGE_START_DATE), TRANSFER_PROCESS_SCOPE);
        registry.bind(withCxPolicyNsPrefix(DATA_USAGE_START_DATE), POLICY_MONITOR_SCOPE);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void prepare() {
        validatorRegistry.register(EDC_POLICY_DEFINITION_TYPE, ConxPolicyDefinitionValidator.instance());
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        registerFunctions(policyEngine);
        registerBindings(bindingRegistry);
    }
}
