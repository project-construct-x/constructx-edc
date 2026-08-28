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

plugins {
    `java-library`
}

repositories { mavenCentral() }

val edcVersion = "0.15.1"

dependencies {
    implementation("org.eclipse.edc:boot-spi:${edcVersion}")
    implementation("org.eclipse.edc:core-spi:${edcVersion}")
    implementation("org.eclipse.edc:http-spi:${edcVersion}")
    implementation("org.eclipse.edc:json-ld-spi:${edcVersion}")
    implementation("org.eclipse.edc:transform-spi:${edcVersion}")
    implementation("org.eclipse.edc:validator-spi:${edcVersion}")
    implementation("org.eclipse.edc:web-spi:${edcVersion}")
    implementation("org.eclipse.edc:control-plane-spi:${edcVersion}")
    implementation("org.eclipse.edc:policy-spi:${edcVersion}")
    implementation("org.eclipse.edc:participant-context-single-spi:${edcVersion}")
    implementation("org.eclipse.edc:api-lib:${edcVersion}")
    implementation("org.eclipse.edc:jersey-providers-lib:${edcVersion}")
    implementation(libs.jakarta.rsApi)

    testImplementation("org.eclipse.edc:junit:${edcVersion}") {
        exclude(group = "org.junit.jupiter")
        exclude(group = "org.junit.platform")
        exclude(group = "org.junit")
    }
    testImplementation("org.eclipse.edc:json-ld-lib:${edcVersion}")
    // ships the cached document/odrl.jsonld context that the runtime's JsonLdExtension registers as well
    testImplementation("org.eclipse.edc:json-ld:${edcVersion}")
    testImplementation("org.eclipse.edc:control-plane-transform:${edcVersion}")
    testImplementation("org.eclipse.edc:transform-lib:${edcVersion}")
    testImplementation("org.eclipse.edc:participant-spi:${edcVersion}")
    testImplementation(libs.titaniumJsonLd)
    testImplementation(libs.jacksonJsonP)
}
