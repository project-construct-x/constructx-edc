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

val edcVersion = project.property("con-x-edcVersion") as String
val txVersion = project.property("version") as String

dependencies {
    implementation("org.eclipse.edc:controlplane-dcp-bom:$edcVersion")
    implementation("org.eclipse.edc:controlplane-feature-sql-bom:$edcVersion")

    implementation(project(":edc-extensions:sql-vault"))
    implementation(project(":edc-extensions:policy-hub-client"))

    implementation("org.eclipse.tractusx.edc:agreements:$txVersion")
    implementation("org.eclipse.tractusx.edc:retirement-evaluation-store-sql:$txVersion")
    implementation(project(":edc-extensions:agreements:retirement-evaluation-bootstrapping"))
    implementation(project(":edc-extensions:dynamic-issuers"))
    implementation(project(":edc-extensions:basic-abac"))

    // Construct-X's own policy extension (con-x-policy, ConxPolicyExtension): the ODRL policy functions and the
    // policy definition validation of the Construct-X connector. Required so the control plane knows the left
    // operands (Membership, UsagePurpose, FrameworkAgreement, DataSubset, ...) emitted by the Construct-X Policy
    // Hub; without them edc.policy.validation.enabled=true already rejects the policy on creation.
    // The Catena-X policy extension (CxPolicyExtension) is NOT used: con-x-policy only reuses individual classes of
    // the unmodified cx-policy module as a library, and the ShadowJar below removes CxPolicyExtension from the runtime.
    // Deliberately no Catena-X BPN/BDRS extensions (bpn-validation, bdrs-client): Construct-X uses DIDs as
    // participant ids and con-x-policy does not register the BusinessPartnerGroup/BusinessPartnerNumber functions.
    implementation(project(":edc-extensions:con-x-policy"))

    // Hotfix version overrides
    implementation("com.fasterxml.jackson.core:jackson-core:2.22.3")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.4")
}

configurations.runtimeClasspath {
    // No Catena-X BPN/BDRS code in the Construct-X runtime, not even interfaces: the unmodified cx-policy library
    // depends on these two SPI modules, but only for the BusinessPartnerGroup/BusinessPartnerNumber functions and
    // CxPolicyExtension, none of which con-x-policy uses or loads.
    exclude(group = "org.eclipse.tractusx.edc", module = "bdrs-client-spi")
    exclude(group = "org.eclipse.tractusx.edc", module = "bpn-validation-spi")
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
    // Only Construct-X's own ConxPolicyExtension (con-x-policy) may run. The Catena-X CxPolicyExtension arrives on the
    // classpath with the cx-policy library classes; drop it from the service file so it is never loaded (EDC composes
    // policy validators, so it would otherwise still reject what Construct-X accepts).
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
