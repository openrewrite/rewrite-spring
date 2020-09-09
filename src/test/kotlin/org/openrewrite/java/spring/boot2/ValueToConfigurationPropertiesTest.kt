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
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.openrewrite.*
import org.openrewrite.java.JavaParser
import org.openrewrite.java.assertRefactored
import org.openrewrite.java.tree.J

class ValueToConfigurationPropertiesTest : RefactorVisitorTestForParser<J.CompilationUnit> {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
            .classpath("spring-boot", "spring-beans")
            .build()
    override val visitors: Iterable<RefactorVisitor<*>> = listOf(ValueToConfigurationProperties())

    companion object {
        const val disabledReason = "ValueToConfigurationProperties is still a work in progress"
    }

    @Test
    @Disabled(disabledReason)
    fun sharedPrefix() = assertRefactored(
            before = """
            import org.springframework.beans.factory.annotation.Value;

            class MyConfiguration {
                @Value("${"$"}{app.refresh-rate}")
                private int refreshRate;
            }
        """,
            after = """
            import org.springframework.boot.context.properties.ConfigurationProperties;
            
            @ConfigurationProperties("app")
            class MyConfiguration {
                private int refreshRate;
            }
        """
    )

    @Test
    @Disabled(disabledReason)
    fun changeFieldName() = assertRefactored(
            before = """
            import org.springframework.beans.factory.annotation.Value;

            class MyConfiguration {
                @Value("${"$"}{app.refresh-rate}")
                private int refresh;
            
                public int getRefresh() {
                    return this.refresh;
                }
            
                public void setRefresh(int refresh) {
                    this.refresh = refresh;
                }
            }
        """,
            after = """
            import org.springframework.boot.context.properties.ConfigurationProperties;
            
            @ConfigurationProperties("app")
            class MyConfiguration {
                private int refreshRate;
            
                public int getRefreshRate() {
                    return this.refreshRate;
                }
            
                public void setRefreshRate(int refresh) {
                    this.refreshRate = refresh;
                }
            }
        """
    )

    @Test
    @Disabled(disabledReason)
    fun changeFieldNameReferences() = assertRefactored(
            before = """
            class MyService {
                MyConfiguration config;
            
                {
                    config.getRefresh();
                    config.setRefresh(1);
                }
            }
        """,
            dependencies = listOf("""
            import org.springframework.beans.factory.annotation.Value;

            class MyConfiguration {
                @Value("${"$"}{app.refresh-rate}")
                private int refresh;
            
                public int getRefresh() {
                    return this.refresh;
                }
            
                public void setRefresh(int refresh) {
                    this.refresh = refresh;
                }
            }
        """),
            after = """
            import org.springframework.boot.context.properties.ConfigurationProperties;
            
            @ConfigurationProperties("app")
            class MyService {
                MyConfiguration config;
            
                {
                    config.getRefreshRate();
                    config.setRefreshRate(1);
                }
            }
        """
    )

    @Test
    @Disabled(disabledReason)
    fun nestedClass() {
        val aSource = """
            import org.springframework.beans.factory.annotation.Value;

            class MyConfiguration {
                @Value("${"$"}{app.screen.refresh-rate}")
                private int refresh;

                @Value("${"$"}{app.name}")
                private String name;

                public int getRefresh() {
                    return this.refresh;
                }
            }
        """.trimIndent()

        val bSource = """
            class MyService {
                MyConfiguration config;

                {
                    config.getRefresh();
                }
            }
        """.trimIndent()

        val fixed: List<SourceFile> = Refactor()
                .visit(ValueToConfigurationProperties())
                .fix(parser.parse(aSource, bSource))
                .map { it.fixed }
                .toList()

        val aFixed = fixed[0]
        val bFixed = fixed[1]
        assertRefactored(aFixed, """
            import org.springframework.boot.context.properties.ConfigurationProperties;

            @ConfigurationProperties("app")
            class MyConfiguration {
                private Screen screen;

                private String name;

                public Screen getScreen() {
                    return this.screen;
                }

                public void setScreen() {
                    this.screen = screen;
                }

                public static class Screen {
                    private int refreshRate;

                    public int getRefreshRate() {
                        return this.refreshRate;
                    }
                }
            }
        """)
        assertRefactored(bFixed, """
            class MyService {
                MyConfiguration config;

                {
                    config.getScreen().getRefreshRate();
                }
            }
        """.trimIndent())
    }

    @Test
    fun testPrefixTreeBuilding() {
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
            
            public class CodeSnippet {
                public CodeSnippet(@Value("${"$"}{app.config.constructor-param}") String baz) {}
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
                .map { it.fixed.printTrimmed() }

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
        assertThat(longestCommonPrefixPaths).contains(listOf("app", "config"), listOf("screen"));
    }
}
