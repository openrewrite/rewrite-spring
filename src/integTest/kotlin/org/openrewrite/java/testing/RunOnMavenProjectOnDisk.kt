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
package org.openrewrite.java.testing

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.openrewrite.InMemoryExecutionContext
import org.openrewrite.Recipe
import org.openrewrite.SourceFile
import org.openrewrite.config.Environment
import org.openrewrite.java.JavaParser
import org.openrewrite.java.search.FindTypes
import org.openrewrite.marker.SearchResult
import org.openrewrite.maven.MavenParser
import org.openrewrite.maven.cache.LocalMavenArtifactCache
import org.openrewrite.maven.cache.MapdbMavenPomCache
import org.openrewrite.maven.cache.ReadOnlyLocalMavenArtifactCache
import org.openrewrite.maven.internal.MavenParsingException
import org.openrewrite.maven.utilities.MavenArtifactDownloader
import org.openrewrite.maven.utilities.MavenProjectParser
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.util.function.BiPredicate
import java.util.function.Consumer
import kotlin.streams.toList

//TODO - Need to sort testing classpath for this. The tests classes do not have type attribution.
class RunOnMavenProjectOnDisk {
    private var sources: List<SourceFile> = emptyList()

    fun parseSources() {
        val projectDir = Paths.get(System.getenv("rewrite.project"))

        val errorConsumer = Consumer<Throwable> { t ->
            if (t is MavenParsingException) {
                println("  ${t.message}")
            } else {
                t.printStackTrace()
            }
        }

        val downloader = MavenArtifactDownloader(
            ReadOnlyLocalMavenArtifactCache.MAVEN_LOCAL.orElse(
                LocalMavenArtifactCache(Paths.get(System.getProperty("user.home"), ".rewrite-cache", "artifacts"))
            ),
            null,
            errorConsumer
        )

        val pomCache = MapdbMavenPomCache(
            Paths.get(System.getProperty("user.home"), ".rewrite-cache", "poms").toFile(),
            null
        )

        val mavenParserBuilder = MavenParser.builder()
            .cache(pomCache)
            .mavenConfig(projectDir.resolve(".mvn/maven.config"))

        val parser = MavenProjectParser(
            downloader,
            mavenParserBuilder,
            JavaParser.fromJavaVersion(),
            InMemoryExecutionContext(errorConsumer)
        )

        this.sources = parser.parse(projectDir)
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "rewrite.project", matches = ".*")
    fun springBoot2JUnit4to5Migration() {
        parseSources()

        val recipe = Environment.builder()
            .scanClasspath(emptyList())
            .build()
            .activateRecipes("org.openrewrite.java.spring.boot2.SpringBoot2JUnit4to5Migration")


        runRecipe(recipe)
    }

    private fun runRecipe(recipe: Recipe) {
        val results = recipe.run(sources.filter { it.sourcePath.toString().contains("src/test/java") })

        for (result in results) {
            println(result.before!!.sourcePath)
            println("-----------------------------------------")
            println(result.diff(SearchResult.PRINTER))
        }
    }
}
