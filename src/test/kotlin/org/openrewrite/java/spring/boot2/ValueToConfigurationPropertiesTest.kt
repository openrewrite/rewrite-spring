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
package org.openrewrite.java.spring.boot2

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.openrewrite.*
import org.openrewrite.java.JavaParser
import org.openrewrite.java.tree.J
import org.openrewrite.java.tree.JavaType

class ValueToConfigurationPropertiesTest : RefactorVisitorTestForParser<J.CompilationUnit> {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
            .classpath("spring-boot", "spring-beans")
            .build()
    override val visitors: Iterable<RefactorVisitor<*>> = listOf(ValueToConfigurationProperties())

    @Test
    fun testBasicVTCP() {
        val springApplication = """
            package org.example;
            import org.springframework.boot.autoconfigure.SpringBootApplication;
            @SpringBootApplication
            public class ASpringBootApplication {
                public static void main(String[] args) {}
            }
        """.trimIndent()
        val classUsingValue = """
            package org.example;
            import org.springframework.beans.factory.annotation.Value;
            
            public class ExampleValueClass {
                public ExampleValueClass(@Value("${"$"}{app.config.constructor-param}") String baz) {}
                @Value("${"$"}{app.config.foo}")
                String foo;
                
                @Value("${"$"}{app.config.bar}")
                String bar;
                
                @Value("${"$"}{screen.resolution.height}")
                int height;
                
                @Value("${"$"}{screen.resolution.width}")
                int width;
                
                @Value("${"$"}{screen.refresh-rate}")
                int refreshRate;
            }
        """.trimIndent()
        val vtcp = ValueToConfigurationProperties()
        val results = Refactor()
                .visit(vtcp)
                .fix(parser.parse(classUsingValue, springApplication))
                .map { it.fixed as J.CompilationUnit }

        val prefixtree = vtcp.prefixTree

        val foo = prefixtree.get("app.config.foo")
        assertThat(foo).isNotNull
        assertThat(foo).isInstanceOf(ValueToConfigurationProperties.PrefixTerminalNode::class.java)
        val bar = prefixtree.get("app.config.bar")
        assertThat(bar).isNotNull
        assertThat(bar).isInstanceOf(ValueToConfigurationProperties.PrefixTerminalNode::class.java)
        val refreshRate = prefixtree.get("screen.refreshRate")
        assertThat(refreshRate).isNotNull
        assertThat(refreshRate).isInstanceOf(ValueToConfigurationProperties.PrefixTerminalNode::class.java)
        val width = prefixtree.get("screen.resolution.height")
        assertThat(width).isNotNull
        assertThat(width).isInstanceOf(ValueToConfigurationProperties.PrefixTerminalNode::class.java)

        val config = prefixtree.get("app.config")
        assertThat(config).isNotNull
        assertThat(config).isInstanceOf(ValueToConfigurationProperties.PrefixParentNode::class.java)

        val longestCommonPrefixPaths = prefixtree.longestCommonPrefixes
        assertThat(longestCommonPrefixPaths).isNotEmpty
        assertThat(longestCommonPrefixPaths).contains(listOf("app", "config"), listOf("screen"))

        // Now check out some actual results
        assertThat(results.size).isEqualTo(3)
                .`as`("There should be the generated AppConfigConfiguration and ScreenConfiguration classes, " +
                        "plus a modified ExampleValueClass")

        val appConfig = results.find { it.classes.first().name.simpleName == "AppConfigConfiguration" }
        assertThat(appConfig).isNotNull
        val screenConfig = results.find { it.classes.first().name.simpleName == "ScreenConfiguration" }
        assertThat(screenConfig).isNotNull
        val exampleValue = results.find { it.classes.first().name.simpleName == "ExampleValueClass" }
        assertThat(exampleValue).isNotNull

        appConfig.assertHasMethod("getFoo", "setFoo", "getConstructorParam", "setConstructorParam")
        screenConfig.assertHasMethod("getResolution", "setResolution", "getRefreshRate", "setRefreshRate")
        // The constructor of ExampleValue should have been amended to accept an AppConfigConfiguration
        // and a ScreenConfiguration as its two arguments
        val constructors = exampleValue!!.classes.first().methods.filter { it.isConstructor }
        assertThat(constructors.size).isEqualTo(1)
        val constructor = constructors.first()
        val constructorParams = constructor.params.params
        assertThat(constructorParams.size).isEqualTo(2)
        assertThat(constructorParams.find { it.hasClassType(JavaType.Class.build("org.example.AppConfigConfiguration")) }).isNotNull
        assertThat(constructorParams.find { it.hasClassType(JavaType.Class.build("org.example.ScreenConfiguration")) }).isNotNull
    }

    private fun J.CompilationUnit?.assertHasMethod(vararg names: String) =
        names.forEach { name ->
            assertThat(this!!.classes.first().methods.find { it.name.simpleName == name }).isNotNull
        }


}
