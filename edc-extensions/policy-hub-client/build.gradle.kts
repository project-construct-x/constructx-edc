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
    implementation(libs.edc.spi.boot)
    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.http)
    implementation(libs.edc.spi.jsonld)
    implementation(libs.edc.spi.transform)
    implementation(libs.edc.spi.validator)
    implementation(libs.edc.spi.web)
    implementation(libs.edc.spi.controlplane)
    implementation(libs.edc.spi.policy)
    implementation(libs.edc.spi.participant.context.single)
    implementation(libs.edc.lib.api)
    implementation(libs.edc.lib.jersey.providers)
    implementation(libs.jakarta.rsApi)

    testImplementation(libs.edc.junit) {
        exclude(group = "org.junit.jupiter")
        exclude(group = "org.junit.platform")
        exclude(group = "org.junit")
    }
    testImplementation(libs.edc.lib.jsonld)
    // ships the cached document/odrl.jsonld context that the runtime's JsonLdExtension registers as well
    testImplementation(libs.edc.ext.jsonld)
    testImplementation(libs.edc.controlplane.transform)
    testImplementation(libs.edc.lib.transform)
    testImplementation(libs.edc.spi.participant)
    testImplementation(libs.titaniumJsonLd)
    testImplementation(libs.jacksonJsonP)
}
