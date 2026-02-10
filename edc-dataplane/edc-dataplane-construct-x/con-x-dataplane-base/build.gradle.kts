plugins {
    `java-library`
    id("application")
    alias(libs.plugins.shadow)
}

dependencies {
    val txGroup = "org.eclipse.tractusx.edc"
    runtimeOnly("org.eclipse.tractusx.edc:edc-dataplane-base:0.11.2") {

        exclude(group = txGroup, module = "edr-core") // refresh token related

        exclude(group = txGroup, module = "edc-dataplane-proxy-consumer-api") // refresh token related

        exclude(group = txGroup, module = "token-refresh-api") // refresh token related
        exclude(group = txGroup, module = "token-refresh-core") // refresh token related
        exclude(group = txGroup, module = "tx-dcp-sts-dim") // tx specific
        exclude(group = txGroup, module = "tokenrefresh-handler") // refresh token related
        exclude(group = txGroup, module = "event-subscriber")


        exclude(group = "io.opentelemetry.instrumentation", module = "opentelemetry-log4j-appender")

    }

    implementation("org.eclipse.edc:dataplane-base-bom:0.14.1")
//    runtimeOnly("org.eclipse.edc:edr-cache-api:0.14.1")
//    implementation("org.eclipse.edc:data-plane-self-registration:0.14.1") // temporary quickfix?
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")

}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    mergeServiceFiles()
    archiveFileName.set("con-x-edc-dataplane.jar")
    transform(com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer())
}

tasks.named("build") {
    dependsOn(tasks.named("shadowJar"))
}

application {
    mainClass.set("org.eclipse.edc.boot.system.runtime.BaseRuntime")
}