/********************************************************************************
 * Copyright (c) 2022 Mercedes-Benz Tech Innovation GmbH
 * Copyright (c) 2026 Fraunhofer-Gesellschaft zur Foerderung der angewandten Forschung e.V. (represented by Fraunhofer ISST)
 * Copyright (c) 2021,2022 Contributors to the Eclipse Foundation
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
    id("application")
    alias(libs.plugins.shadow)
}

dependencies {
    val txGroup = "org.eclipse.tractusx.edc"
    runtimeOnly("org.eclipse.tractusx.edc:edc-controlplane-base:0.11.2") {

        exclude(group = txGroup, module = "edr-core") // refresh token
        exclude(group = txGroup, module = "json-ld-core") // tx specific

        exclude(group = txGroup, module = "agreements-bpns") // bpn specific
        exclude(group = txGroup, module = "bdrs-client") // bpn specific
        exclude(group = txGroup, module = "bpn-validation") // bpn specific
        exclude(group = txGroup, module = "cx-policy") // tx specific
        exclude(group = txGroup, module = "cx-policy-legacy") // tx specific
        exclude(group = txGroup, module = "data-flow-properties-provider") // tx specific
        exclude(group = txGroup, module = "dcp:tx-dcp") // tx specific, but currently needed!
        exclude(group = txGroup, module = "dcp:tx-dcp-sts-dim") // tx specific
        exclude(group = txGroup, module = "edr-api-v2") // related to refresh token
        exclude(group = txGroup, module = "edr-callback") // related to refresh token

        exclude(group = txGroup, module = "provision-additional-headers") // tx specific
        exclude(group = txGroup, module = "tokenrefresh-handler") // refresh token
        exclude(group = txGroup, module = "empty-asset-selector") // restricts certain contract definition, unsure if needed
        exclude(group = txGroup, module = "connector-discovery-api") // requires bdrs client
        exclude(group = txGroup, module = "dataspace-protocol") // tx specific

        exclude(group = txGroup, module = "event-subscriber") // open-telemetry-related, unsure if needed

    }
    runtimeOnly("org.eclipse.edc:controlplane-dcp-bom:0.14.1")
    runtimeOnly("org.eclipse.edc:controlplane-base-bom:0.14.1")
    runtimeOnly("org.eclipse.edc:edr-cache-api:0.14.1")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")

}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    mergeServiceFiles()
    archiveFileName.set("con-x-edc-controlplane.jar")
    transform(com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer())
}

tasks.named("build") {
    dependsOn(tasks.named("shadowJar"))
}

application {
    mainClass.set("org.eclipse.edc.boot.system.runtime.BaseRuntime")
}