/*
 * Copyright 2022 the original author or authors.
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
package org.openrewrite.java.spring.boot3

import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

/**
 * @author Alex Boyko
 */
class RemoveConstructorBindingAnnotationTest : JavaRecipeTest {

    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .logCompilationWarningsAndErrors(true)
            .classpath("spring-boot")
            .build()

    override val recipe: Recipe
        get() = RemoveConstructorBindingAnnotation()


    @Test
    fun topLevelTypeAnnotation() = assertChanged(
        before = """
            import org.springframework.boot.context.properties.ConfigurationProperties;
            import org.springframework.boot.context.properties.ConstructorBinding;
            
            @ConfigurationProperties
            @ConstructorBinding
            class A {
                void method() {
                }
            }
        """,
        after = """
            import org.springframework.boot.context.properties.ConfigurationProperties;
            
            @ConfigurationProperties
            class A {
                void method() {
                }
            }
        """
    )

    @Test
    fun constructorAnnotation() = assertChanged(
        before = """
            import org.springframework.boot.context.properties.ConfigurationProperties;
            import org.springframework.boot.context.properties.ConstructorBinding;
            
            @ConfigurationProperties
            class A {
                @ConstructorBinding
                A() {
                }
            }
        """,
        after = """
            import org.springframework.boot.context.properties.ConfigurationProperties;
            
            @ConfigurationProperties
            class A {
                
                A() {
                }
            }
        """
    )

    @Test
    fun topLevelTypeAnnotationWithoutConfigProperties() = assertUnchanged(
        before = """
            import org.springframework.boot.context.properties.ConstructorBinding;
            
            @ConstructorBinding
            class A {
            }
        """
    )

    @Test
    fun constructorAnnotationWithMultipleConstructors() = assertUnchanged(
        before = """
            import org.springframework.boot.context.properties.ConfigurationProperties;
            import org.springframework.boot.context.properties.ConstructorBinding;
            
            @ConfigurationProperties
            class A {
                A() {
                }
                @ConstructorBinding
                A(int n) {
                }
            }
        """
    )

    @Test
    fun noConstrBindingAnnotation() = assertUnchanged(
        before = """
            import org.springframework.boot.context.properties.ConfigurationProperties;
            
            @ConfigurationProperties
            class A {
                A() {
                }
                A(int n) {
                }
            }
        """
    )

    @Test
    fun topLevelTypeAnnotationInnerClass() = assertChanged(
        before = """
            import org.springframework.boot.context.properties.ConfigurationProperties;
            import org.springframework.boot.context.properties.ConstructorBinding;
            
            class A {
                void method() {
                }
                
                @ConfigurationProperties
                @ConstructorBinding
                static class B {
                }
            }
        """,
        after = """
            import org.springframework.boot.context.properties.ConfigurationProperties;
            
            class A {
                void method() {
                }
                
                @ConfigurationProperties
                static class B {
                }
            }
        """
    )

    @Test
    fun constructorAnnotationInnerClass() = assertChanged(
        before = """
            import org.springframework.boot.context.properties.ConfigurationProperties;
            import org.springframework.boot.context.properties.ConstructorBinding;
            
            @ConfigurationProperties
            @ConstructorBinding
            class A {
                void method() {
                }
                
                @ConfigurationProperties
                static class B {
                    @ConstructorBinding
                    B() {
                    }
                }
            }
        """,
        after = """
            import org.springframework.boot.context.properties.ConfigurationProperties;
            
            @ConfigurationProperties
            class A {
                void method() {
                }
                
                @ConfigurationProperties
                static class B {
                    
                    B() {
                    }
                }
            }
        """
    )

    @Test
    fun constructorAndTypeAnnotationWithMultipleConstructorsInnerClass() = assertUnchanged(
        before = """
            import org.springframework.boot.context.properties.ConfigurationProperties;
            import org.springframework.boot.context.properties.ConstructorBinding;
            
            class A {
                @ConfigurationProperties
                static class B {
                    B() {
                    }
                    @ConstructorBinding
                    B(int n) {
                    }
                }
            }
        """
    )

    @Test
    fun classAnnotationWithMultipleConstructors() = assertChanged(
        before = """
            import org.springframework.boot.context.properties.ConfigurationProperties;
            import org.springframework.boot.context.properties.ConstructorBinding;
            
            @ConfigurationProperties
            @ConstructorBinding
            class A {
                A() {
                }
                A(int n) {
                }
            }
        """,
        after = """
            import org.springframework.boot.context.properties.ConfigurationProperties;
            import org.springframework.boot.context.properties.ConstructorBinding;
            
            @ConfigurationProperties
            /*
             * TODO:
             * You need to remove ConstructorBinding on class level and move it to appropriate
             * constructor.
             * */
            @ConstructorBinding
            class A {
                A() {
                }
                A(int n) {
                }
            }
        """
    )

    // TODO: what will happen to number int numberOfConstructors = 0;
    // when a class has subclass which has constructors
}
