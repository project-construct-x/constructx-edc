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

plugins {
    `java-library`
}

repositories { mavenCentral() }

dependencies {
    // cx-policy is used purely as a library: ConxPolicyExtension reuses individual, unmodified classes (constraint
    // functions, validator building blocks) but is the only policy extension that runs in Construct-X. The Catena-X
    // CxPolicyExtension that cx-policy ships is NOT used and is removed from the runtime by the Construct-X
    // distributions' ShadowJar configuration.
    // Deliberately no dependency on the Catena-X BPN/BDRS modules (bpn-validation, bdrs-client and their SPIs): the
    // BusinessPartnerGroup/BusinessPartnerNumber functions are not registered, participants are selected by DID.
    api(project(":edc-extensions:cx-policy"))
    // the ABAC left-operand pattern (BasicAbacUtils.BASIC_ABAC_PATTERN) is the contract between the validator here and the
    // constraint function there; sharing it keeps the two from drifting apart (a policy that validates but is never evaluated)
    implementation(project(":edc-extensions:basic-abac"))
    implementation(project(":spi:core-spi"))
    implementation(libs.edc.spi.core)
    implementation(libs.edc.core.policy.monitor)
    implementation(libs.edc.spi.catalog)
    implementation(libs.edc.spi.contract)
    implementation(libs.edc.spi.controlplane)
    implementation(libs.edc.spi.decentralized.claims)
    implementation(libs.edc.spi.policyengine)
    implementation(libs.edc.spi.vc)
    implementation(libs.edc.spi.jsonld)
    implementation(libs.edc.lib.validator)
    implementation(libs.jakartaJson)

    testImplementation(testFixtures(project(":edc-extensions:cx-policy")))
    testImplementation(libs.edc.junit)
    testImplementation(libs.jacksonJsonP)
    testImplementation(libs.titaniumJsonLd)
}
