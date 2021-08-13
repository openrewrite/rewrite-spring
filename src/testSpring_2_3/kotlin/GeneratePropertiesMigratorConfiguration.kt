/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.spring

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.classgraph.ClassGraph
import org.openrewrite.java.spring.internal.SpringBootReleases
import java.io.File

/**
 * TODO move this to buildSrc or somewhere else where it can be run automatically
 */
object GeneratePropertiesMigratorConfiguration {
    @JvmStatic
    fun main(args: Array<String>) {
        val springBootReleases = SpringBootReleases()

        val objectMapper = ObjectMapper()
                .registerModule(KotlinModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

        val releasesDir = File(".boot-releases")
        releasesDir.mkdirs()

        val latestPatchReleases =
                if (args.contains("offline"))
                    releasesDir.listFiles()?.map { it.name } ?: emptySet()
                else
                    springBootReleases.latestPatchReleases()

        val config = File("src/main/resources/META-INF/rewrite/spring-boot-configuration-migration.yml")
        config.writeText("\n${File("gradle/licenseHeader.txt").readText()}\n".prependIndent("# "))

        val alreadyDefined = mutableSetOf<String>()
        latestPatchReleases.filter { v -> v.startsWith("2") }.forEach { version ->
            val versionDir = File(releasesDir, version)

            if (versionDir.mkdirs()) {
                println("Downloading version $version")
                springBootReleases.download(version).forEach { download ->
                    download.body.use { it.byteStream().copyTo(File(versionDir, "${download.moduleName}-${version}.jar").outputStream()) }
                }
            } else {
                println("Using existing download of version $version")
            }

            println("Scanning version $version")

            ClassGraph()
                    .overrideClasspath(versionDir.listFiles()!!.map { it.toURI() })
                    .acceptPaths("META-INF")
                    .enableMemoryMapping()
                    .scan()
                    .use { scanResult ->
                        val replacements = scanResult
                                .getResourcesWithLeafName("additional-spring-configuration-metadata.json")
                                .flatMap { res ->
                                    res.open().use { inputStream ->
                                        val metadata = objectMapper.readValue<SpringConfigurationMetadata>(inputStream)
                                        metadata.properties.filter { it.deprecation?.replacement != null }
                                    }
                                }
                                .filter { alreadyDefined.add(it.name) }

                        if (replacements.isNotEmpty()) {
                            val majorMinor = version.split(".").subList(0, 2).joinToString("_")

                            config.appendText("""
                                ---
                                type: specs.openrewrite.org/v1beta/recipe
                                name: org.openrewrite.java.spring.boot2.SpringBootProperties_$majorMinor
                                displayName: Migrate Spring Boot properties to ${version.split(".").subList(0, 2).joinToString(".")}
                                description: Migrate properties found in `application.properties` and `application.yml`.
                                recipeList:
                            """.trimIndent())

                            config.appendText(
                                    replacements.joinToString("\n", prefix = "\n", postfix = "\n") { prop ->
                                        """
                                        - org.openrewrite.properties.ChangePropertyKey:
                                            oldPropertyKey: ${prop.name}
                                            newPropertyKey: ${prop.deprecation!!.replacement}
                                        - org.openrewrite.yaml.ChangePropertyKey:
                                            oldPropertyKey: ${prop.name}
                                            newPropertyKey: ${prop.deprecation.replacement}
                                        """.trimIndent().prependIndent("  ")
                                    }
                            )
                        }
                    }
        }
    }
}

data class SpringConfigurationMetadata(val properties: List<ConfigurationProperty>)
data class ConfigurationProperty(val name: String, val deprecation: Deprecation?)
data class Deprecation(val replacement: String?)
