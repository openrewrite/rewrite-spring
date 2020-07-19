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
package org.openrewrite.spring

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.classgraph.ClassGraph
import org.openrewrite.spring.internal.SpringBootReleases
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

        var previousVersion: String? = null
        val alreadyDefined = mutableSetOf<String>()
        latestPatchReleases.filter { v -> v.startsWith("2") }.forEach { version ->
            val versionDir = File(releasesDir, version)

            if (versionDir.mkdirs()) {
                println("Downloading version $version")
                springBootReleases.download(version).forEach { download ->
                    download.body.use { it.byteStream().transferTo(File(versionDir, "${download.moduleName}-${version}.jar").outputStream()) }
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
                                type: beta.openrewrite.org/v1/visitor
                                name: org.openrewrite.spring.boot.config.SpringBootConfigurationProperties.$majorMinor
                                ${previousVersion?.let { "extends: org.openrewrite.spring.boot.config.SpringBootConfigurationProperties.$previousVersion" } ?: ""}
                                visitors:
                            """.trimIndent())

                            config.appendText(
                                    replacements.joinToString("\n", prefix = "\n", postfix = "\n") { prop ->
                                        """
                                        - org.openrewrite.properties.ChangePropertyKey:
                                            property: ${prop.name}
                                            toProperty: ${prop.deprecation!!.replacement}
                                        """.trimIndent().prependIndent("  ")
                                    }
                            )

                            config.appendText("""
                                
                                ---
                                type: beta.openrewrite.org/v1/visitor
                                name: org.openrewrite.spring.boot.config.SpringBootConfigurationYaml.$majorMinor
                                ${previousVersion?.let { "extends: org.openrewrite.spring.boot.config.SpringBootConfigurationYaml.$previousVersion" } ?: ""}
                                visitors:
                            """.trimIndent())

                            config.appendText(
                                    replacements.joinToString("\n", prefix = "\n", postfix = "\n") { prop ->
                                        """
                                        - org.openrewrite.yaml.ChangePropertyKey:
                                            property: ${prop.name}
                                            toProperty: ${prop.deprecation!!.replacement}
                                        """.trimIndent().prependIndent("  ")
                                    }
                            )

                            previousVersion = majorMinor
                        }
                    }
        }
    }
}

data class SpringConfigurationMetadata(val properties: List<ConfigurationProperty>)
data class ConfigurationProperty(val name: String, val deprecation: Deprecation?)
data class Deprecation(val replacement: String?)
