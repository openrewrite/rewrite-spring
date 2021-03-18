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
package org.openrewrite.java.spring.org.openrewrite.java.spring.boot2

import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest
import org.openrewrite.java.spring.boot2.ReplaceDeprecatedEnvironmentTestUtils

class ReplaceDeprecatedEnvironmentTestUtilsTest : JavaRecipeTest {

    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .classpath("spring-beans", "spring-core", "spring-context", "spring-boot-test")
            .logCompilationWarningsAndErrors(true)
            .build()

    override val recipe: Recipe
        get() = ReplaceDeprecatedEnvironmentTestUtils()

    @Test
    fun givenHasStringVariableWhenRemovingDeprecatedThenReplacesAddEnvironmentWithSetProperties() =
        assertChanged(
            before = """
                package com.mycompany;
                import org.springframework.context.annotation.AnnotationConfigApplicationContext;
                import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;
                
                public class MyClass {
                    public void myMethod() {
                        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
                        String pair = "pair";
                        addEnvironment(context, pair);
                    }
                }
            """,
            after = """
                package com.mycompany;
                import org.springframework.boot.test.util.TestPropertyValues;
                import org.springframework.context.annotation.AnnotationConfigApplicationContext;
                
                public class MyClass {
                    public void myMethod() {
                        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
                        String pair = "pair";
                        TestPropertyValues.of(pair).applyTo(context);
                    }
                }
            """
        )

    @Test
    fun givenHasSingleStringWhenRemovingDeprecatedThenReplacesAddEnvironmentWithSetProperties() =
        assertChanged(
            before = """
                package com.mycompany;
                import org.springframework.context.annotation.AnnotationConfigApplicationContext;
                import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;
                
                public class MyClass {
                    public void myMethod() {
                        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
                        addEnvironment(context, "pair:pair");
                    }
                }
            """,
            after = """
                package com.mycompany;
                import org.springframework.boot.test.util.TestPropertyValues;
                import org.springframework.context.annotation.AnnotationConfigApplicationContext;
                
                public class MyClass {
                    public void myMethod() {
                        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
                        TestPropertyValues.of("pair:pair").applyTo(context);
                    }
                }
            """
        )

    @Test
    fun givenConstructsStringAndContextWhenRemovingDeprecatedThenReplacesAddEnvironmentWithSetProperties() =
        assertChanged(
            before = """
                package com.mycompany;
                import org.springframework.context.annotation.AnnotationConfigApplicationContext;
                import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;
                
                public class MyClass {
                    public void myMethod() {
                        addEnvironment(new AnnotationConfigApplicationContext(), "pair" + "pair");
                    }
                }
            """,
            after = """
                package com.mycompany;
                import org.springframework.boot.test.util.TestPropertyValues;
                import org.springframework.context.annotation.AnnotationConfigApplicationContext;
                
                public class MyClass {
                    public void myMethod() {
                        TestPropertyValues.of("pair" + "pair").applyTo(new AnnotationConfigApplicationContext());
                    }
                }
            """
        )

    @Test
    fun givenChainedCallsReplacesThemWithAFluentSetOfCalls() = assertChanged(
        before = """
                package com.mycompany;
                import org.springframework.context.annotation.AnnotationConfigApplicationContext;
                import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;
                
                public class MyClass {
                    public void myMethod() {
                        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
                        addEnvironment(context, "key1:value1");
                        addEnvironment(context, "key2:value2");
                        addEnvironment(context, "key3:value3");
                        String x = "x";
                        addEnvironment(context, "key4:value4");
                    }
                }
            """,
        after = """
                package com.mycompany;
                import org.springframework.boot.test.util.TestPropertyValues;
                import org.springframework.context.annotation.AnnotationConfigApplicationContext;
                
                public class MyClass {
                    public void myMethod() {
                        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
                        TestPropertyValues.of("key1:value1").and("key2:value2").and("key3:value3").applyTo(context);
                        String x = "x";
                        TestPropertyValues.of("key4:value4").applyTo(context);
                    }
                }
            """
    )

    @Test
    fun givenChainedCallsWithDifferentContextsCoalescesThemCorrectly() {
        assertChanged(
            before = """
                package com.mycompany;
                import org.springframework.context.annotation.AnnotationConfigApplicationContext;
                import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;
                
                public class MyClass {
                    public void myMethod() {
                        AnnotationConfigApplicationContext context1 = new AnnotationConfigApplicationContext();
                        AnnotationConfigApplicationContext context2 = new AnnotationConfigApplicationContext();
                        addEnvironment(context2, "key1:value1");
                        addEnvironment(context1, "key2:value2");
                        addEnvironment(context1, "key3:value3");
                        String x = "x";
                        addEnvironment(context2, "key4:value4");
                        addEnvironment(context2, "key5:value5");
                        addEnvironment(context1, "key6:value6");
                        addEnvironment(context2, "key7:value7");
                    }
                }
            """,
            after = """
                package com.mycompany;
                import org.springframework.boot.test.util.TestPropertyValues;
                import org.springframework.context.annotation.AnnotationConfigApplicationContext;
                
                public class MyClass {
                    public void myMethod() {
                        AnnotationConfigApplicationContext context1 = new AnnotationConfigApplicationContext();
                        AnnotationConfigApplicationContext context2 = new AnnotationConfigApplicationContext();
                        TestPropertyValues.of("key1:value1").applyTo(context2);
                        TestPropertyValues.of("key2:value2").and("key3:value3").applyTo(context1);
                        String x = "x";
                        TestPropertyValues.of("key4:value4").and("key5:value5").applyTo(context2);
                        TestPropertyValues.of("key6:value6").applyTo(context1);
                        TestPropertyValues.of("key7:value7").applyTo(context2);
                    }
                }
            """
        )
    }

    @Test
    fun givenChainedCallsWithGeneratedContextsCoalescesThemCorrectly() {
        assertChanged(
            before = """
                package com.mycompany;
                import org.springframework.context.annotation.AnnotationConfigApplicationContext;
                import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;
                
                public class MyClass {
                    public void myMethod() {
                        addEnvironment(new AnnotationConfigApplicationContext(), "key1:value1");
                        addEnvironment(new AnnotationConfigApplicationContext(), "key2:value2");
                        addEnvironment(new AnnotationConfigApplicationContext(), "key3:value3");
                    }
                }
            """,
            after = """
                package com.mycompany;
                import org.springframework.boot.test.util.TestPropertyValues;
                import org.springframework.context.annotation.AnnotationConfigApplicationContext;
                
                public class MyClass {
                    public void myMethod() {
                        TestPropertyValues.of("key1:value1").applyTo(new AnnotationConfigApplicationContext());
                        TestPropertyValues.of("key2:value2").applyTo(new AnnotationConfigApplicationContext());
                        TestPropertyValues.of("key3:value3").applyTo(new AnnotationConfigApplicationContext());
                    }
                }
            """
        )
    }

    @Test
    fun givenChainedCallsWithMixOfContextsCoalescesThemCorrectly() {
        assertChanged(
            before = """
                package com.mycompany;
                import org.springframework.context.annotation.AnnotationConfigApplicationContext;
                import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;
                
                public class MyClass {
                    public void myMethod() {
                        addEnvironment(new AnnotationConfigApplicationContext(), "key1:value1");
                        addEnvironment(new AnnotationConfigApplicationContext(), "key2:value2");
                        AnnotationConfigApplicationContext context1 = new AnnotationConfigApplicationContext();
                        addEnvironment(context1, "key3:value3");
                        addEnvironment(context1, "key4:value4");
                        addEnvironment(new AnnotationConfigApplicationContext(), "key5:value5");
                        addEnvironment(context1, "key5:value5");
                    }
                }
            """,
            after = """
                package com.mycompany;
                import org.springframework.boot.test.util.TestPropertyValues;
                import org.springframework.context.annotation.AnnotationConfigApplicationContext;
                
                public class MyClass {
                    public void myMethod() {
                        TestPropertyValues.of("key1:value1").applyTo(new AnnotationConfigApplicationContext());
                        TestPropertyValues.of("key2:value2").applyTo(new AnnotationConfigApplicationContext());
                        AnnotationConfigApplicationContext context1 = new AnnotationConfigApplicationContext();
                        TestPropertyValues.of("key3:value3").and("key4:value4").applyTo(context1);
                        TestPropertyValues.of("key5:value5").applyTo(new AnnotationConfigApplicationContext());
                        TestPropertyValues.of("key5:value5").applyTo(context1);
                    }
                }
            """
        )
    }

    @Test
    fun givenChainedCallsThatReferToTheSameObjectUnfortunatelyDoesntChainThem() = assertChanged(
        before = """
                package com.mycompany;
                import org.springframework.context.annotation.AnnotationConfigApplicationContext;
                import static org.springframework.boot.test.util.EnvironmentTestUtils.addEnvironment;
                
                public class MyClass {
                    public void myMethod() {
                        AnnotationConfigApplicationContext context1 = new AnnotationConfigApplicationContext();
                        AnnotationConfigApplicationContext context2 = context1;
                        addEnvironment(context2, "key1:value1");
                        addEnvironment(context1, "key2:value2");
                        addEnvironment(context1, "key3:value3");
                        String x = "x";
                        addEnvironment(context2, "key4:value4");
                        addEnvironment(context2, "key5:value5");
                        context1 = new AnnotationConfigApplicationContext();
                        addEnvironment(context1, "key6:value6");
                        addEnvironment(context2, "key7:value7");
                    }
                }
            """,
        after = """
                package com.mycompany;
                import org.springframework.boot.test.util.TestPropertyValues;
                import org.springframework.context.annotation.AnnotationConfigApplicationContext;
                
                public class MyClass {
                    public void myMethod() {
                        AnnotationConfigApplicationContext context1 = new AnnotationConfigApplicationContext();
                        AnnotationConfigApplicationContext context2 = context1;
                        TestPropertyValues.of("key1:value1").applyTo(context2);
                        TestPropertyValues.of("key2:value2").and("key3:value3").applyTo(context1);
                        String x = "x";
                        TestPropertyValues.of("key4:value4").and("key5:value5").applyTo(context2);
                        context1 = new AnnotationConfigApplicationContext();
                        TestPropertyValues.of("key6:value6").applyTo(context1);
                        TestPropertyValues.of("key7:value7").applyTo(context2);
                    }
                }
            """
    )
}