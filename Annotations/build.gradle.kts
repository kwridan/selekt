/*
 * Copyright 2022 Bloomberg Finance L.P.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

repositories {
    mavenCentral()
}

plugins {
    kotlin("jvm")
    `maven-publish`
    signing
}

java {
    withJavadocJar()
    withSourcesJar()
}

disableKotlinCompilerAssertions()

tasks.register("assembleSelekt") {
    dependsOn("assemble")
    dependsOn("sourcesJar")
}

publishing {
    publications.register<MavenPublication>("main") {
        groupId = selektGroupId
        artifactId = "selekt-annotations"
        version = selektVersionName
        from(components.getByName("java"))
        pom {
            commonInitialisation(project)
            description.set("Selekt annotations library.")
        }
    }
}
