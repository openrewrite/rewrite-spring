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
package org.openrewrite.spring.boot2

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.openrewrite.RefactorVisitor
import org.openrewrite.RefactorVisitorTestForParser
import org.openrewrite.java.JavaParser
import org.openrewrite.java.tree.J
import org.openrewrite.spring.boot2.ValueToConfigurationProperties

class ValueToConfigurationPropertiesTest : RefactorVisitorTestForParser<J.CompilationUnit> {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
            .classpath("spring-boot", "spring-beans")
            .build()
    override val visitors: Iterable<RefactorVisitor<*>> = listOf(ValueToConfigurationProperties())

    @Test
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


    /**
     * FIXME Implement me!
     */
//    @Disabled
//    @Test
//    fun nestedClass() {
//        val aSource = """
//            import org.springframework.beans.factory.annotation.Value;
//
//            class MyConfiguration {
//                @Value("${"$"}{app.screen.refresh-rate}")
//                private int refresh;
//
//                @Value("${"$"}{app.name}")
//                private String name;
//
//                public int getRefresh() {
//                    return this.refresh;
//                }
//            }
//        """.trimIndent()
//
//        val bSource = """
//            class MyService {
//                MyConfiguration config;
//
//                {
//                    config.getRefresh();
//                }
//            }
//        """.trimIndent()
//
//        val (a, b) = jp.parseStrings(aSource, bSource)
//        val recipe = ValueToConfigurationProperties()
//        val aFixed = a.refactor().visit(recipe).fix().fixed
//
//        assertRefactored(aFixed, """
//            import org.springframework.boot.context.properties.ConfigurationProperties;
//
//            @ConfigurationProperties("app")
//            class MyConfiguration {
//                private Screen screen;
//
//                private String name;
//
//                public Screen getScreen() {
//                    return this.screen;
//                }
//
//                public void setScreen() {
//                    this.screen = screen;
//                }
//
//                public static class Screen {
//                    private int refreshRate;
//
//                    public int getRefreshRate() {
//                        return this.refreshRate;
//                    }
//                }
//            }
//        """.trimIndent())
//
//        val bFixed = b.refactor().visit(recipe).fix().fixed
//
//        assertRefactored(bFixed, """
//            class MyService {
//                MyConfiguration config;
//
//                {
//                    config.getScreen().getRefreshRate();
//                }
//            }
//        """.trimIndent())
//    }

    @Test
    fun longestCommonPrefix() {
        assertThat(ValueToConfigurationProperties.longestCommonPrefix("a.b", "a")).isEqualTo("a")
        assertThat(ValueToConfigurationProperties.longestCommonPrefix("a", "a.b")).isEqualTo("a")
        assertThat(ValueToConfigurationProperties.longestCommonPrefix("a", "a")).isEqualTo("a")
        assertThat(ValueToConfigurationProperties.longestCommonPrefix("a", "b")).isEqualTo("")
        assertThat(ValueToConfigurationProperties.longestCommonPrefix(null, "a")).isEqualTo("a")
        assertThat(ValueToConfigurationProperties.longestCommonPrefix("a.b.c.d", "a.b")).isEqualTo("a.b")
    }
}
