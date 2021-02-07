/*
 * Copyright 2021 Bloomberg Finance L.P.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

repositories {
    mavenCentral()
    jcenter()
}

plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
    jacoco
    `maven-publish`
}

description = "Selekt Pools library."

dependencies {
    compileOnly(selekt("annotations", selektVersionName))
    implementation(selekt("commons", selektVersionName))
}

disableKotlinCompilerAssertions()

java {
    @Suppress("UnstableApiUsage")
    withSourcesJar()
}

tasks.withType<JacocoReport>().configureEach {
    reports {
        csv.isEnabled = false
        html.isEnabled = true
        xml.isEnabled = true
    }
}

tasks.withType<AbstractPublishToMaven>().configureEach {
    dependsOn("assembleSelekt")
}

tasks.register("assembleSelekt") {
    dependsOn("assemble")
}

publishing {
    publications.register<MavenPublication>("main") {
        groupId = selektGroupId
        artifactId = "selekt-pools"
        version = selektVersionName
        from(components.getByName("java"))
        pom { commonInitialisation(project) }
    }
}
