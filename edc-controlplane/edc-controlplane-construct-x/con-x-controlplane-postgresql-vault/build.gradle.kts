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
    val edcVersion = "0.15.1"
    val txVersion = "0.12.0"
    implementation("org.eclipse.edc:controlplane-dcp-bom:$edcVersion")
    implementation("org.eclipse.edc:controlplane-feature-sql-bom:$edcVersion")

    implementation(project(":edc-extensions:sql-vault"))
    implementation(project(":edc-extensions:policy-hub-client"))

    implementation("org.eclipse.tractusx.edc:agreements:$txVersion")
    implementation("org.eclipse.tractusx.edc:retirement-evaluation-store-sql:$txVersion")
    implementation("org.eclipse.tractusx.edc:control-plane-migration:$txVersion")
    implementation("org.eclipse.tractusx.edc:tx-dcp:$txVersion")

    // Catena-X ODRL policy functions plus the Construct-X additions (DataUsageStartDate, relaxed
    // validation, ...). Required so the control plane knows the left operands (Membership,
    // UsagePurpose, FrameworkAgreement, ...) emitted by the Construct-X Policy Hub. Without them
    // edc.policy.validation.enabled=true already rejects the policy on creation. con-x-policy pulls
    // in the unmodified cx-policy; its ConxPolicyExtension replaces CxPolicyExtension (see ShadowJar).
    implementation(project(":edc-extensions:con-x-policy"))
    // ConxPolicyExtension injects BusinessPartnerStore (in-mem default) and BdrsClient; both are
    // mandatory injects, so the runtime does not boot without these two.
    implementation(project(":edc-extensions:bpn-validation"))
    implementation(project(":edc-extensions:bdrs-client"))
}

/**
 * Merges `META-INF/services` files like `mergeServiceFiles()`, but drops the given service implementations.
 */
class ExcludingServiceFileTransformer(private val excluded: Set<String>) :
    com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer() {
    override fun transform(context: com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext) {
        val lines = context.inputStream.bufferedReader().use { it.readLines() }.filterNot { it.trim() in excluded }
        super.transform(context.copy(inputStream = lines.joinToString("\n").byteInputStream()))
    }
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    // ConxPolicyExtension (con-x-policy) replaces CxPolicyExtension (cx-policy): EDC composes policy
    // validators, so running both would still reject what Construct-X relaxes. Drop the upstream one.
    transform(ExcludingServiceFileTransformer(setOf("org.eclipse.tractusx.edc.policy.cx.CxPolicyExtension")))
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    archiveFileName.set("con-x-controlplane-postgresql-vault.jar")
    transform(com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer())
}

tasks.named("build") {
    dependsOn(tasks.named("shadowJar"))
}

application {
    mainClass.set("org.eclipse.edc.boot.system.runtime.BaseRuntime")
}