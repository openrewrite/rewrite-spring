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
package org.openrewrite.java.spring

import org.junit.jupiter.api.Test
import org.openrewrite.ExecutionContext
import org.openrewrite.Recipe
import org.openrewrite.TreeVisitor
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

/**
 * @author Alex Boyko
 */
class AutowiredFieldIntoConstructorParameterVisitorTest : JavaRecipeTest {

    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .classpath("spring-beans")
            .build()

    override val recipe: Recipe
       get() = toRecipe {
           AutowiredFieldIntoConstructorParameterVisitor("demo.A", "a")
       }

    @Test
    fun fieldIntoExistingSingleConstructor() = assertChanged(
        before = """
            package demo;

            import org.springframework.beans.factory.annotation.Autowired;
            
            public class A {
            
                @Autowired
                private String a;
                
                A() {
                }
            
            }
        """,
        after = """
            package demo;
            
            public class A {
            
                private String a;
                
                A(String a) {
                    this.a = a;
                }
       
            }
        """
    )

    @Test
    fun fieldIntoNewConstructor() = assertChanged(
        before = """
            package demo;

            import org.springframework.beans.factory.annotation.Autowired;
            
            public class A {
            
                @Autowired
                private String a;
                
            }
        """,
        after = """
            package demo;
            
            public class A {
            
                private String a;
          
                A(String a) {
                    this.a = a;
                }
                
            }
        """
    )

    @Test
    fun fieldIntoAutowiredConstructor() = assertChanged(
        before = """
            package demo;

            import org.springframework.beans.factory.annotation.Autowired;
            
            public class A {
            
                @Autowired
                private String a;
                
                A() {
                }
            
                @Autowired
                A(long l) {
                }
            }
        """,
        after = """
            package demo;

            import org.springframework.beans.factory.annotation.Autowired;
            
            public class A {
            
                private String a;
                
                A() {
                }
            
                @Autowired
                A(long l, String a) {
                    this.a = a;
                }
            }
        """
    )

    @Test
    fun noAutowiredConstructor() = assertUnchanged(
        before = """
            package demo;

            import org.springframework.beans.factory.annotation.Autowired;
            
            public class A {
            
                @Autowired
                private String a;
                
                A() {
                }
            
                A(long l) {
                }
            }
        """
    )

    @Test
    fun noAutowiredField() = assertUnchanged(
        before = """
            package demo;

            import org.springframework.beans.factory.annotation.Autowired;
            
            public class A {
            
                private String a;
                
                A() {
                }
            
            }
        """
    )

    @Test
    fun multipleAutowiredConstructors() = assertUnchanged(
        before = """
            package demo;

            import org.springframework.beans.factory.annotation.Autowired;
            
            public class A {
            
                @Autowired
                private String a;
                
                @Autowired
                A() {
                }
            
                @Autowired
                A(long l) {
                }
            }
        """
    )

    @Test
    fun fieldIntoAutowiredConstructorInnerClassPresent() = assertChanged(
        before = """
            package demo;

            import org.springframework.beans.factory.annotation.Autowired;
            
            public class A {
            
                @Autowired
                private String a;
                
                A() {
                }
            
                public static class B {
                
                    @Autowired
                    private String a;
                    
                    B() {
                    
                    }
                }

            }
        """,
        after = """
            package demo;

            import org.springframework.beans.factory.annotation.Autowired;
            
            public class A {
            
                private String a;
                
                A(String a) {
                    this.a = a;
                }
            
                public static class B {
                
                    @Autowired
                    private String a;
                    
                    B() {
                    
                    }
                }

            }
        """
    )

    @Test
    fun fieldInitializedInConstructor() = assertUnchanged(
        before = """
            package demo;

            import org.springframework.beans.factory.annotation.Autowired;
            
            public class A {
            
                @Autowired
                private String a;
                
                A() {
                    this.a = "something";
                }
            
            }
        """
    )

    @Test
    fun fieldInitializedInConstructorWithoutThis() = assertUnchanged(
        before = """
            package demo;

            import org.springframework.beans.factory.annotation.Autowired;
            
            public class A {
            
                @Autowired
                private String a;
                
                A() {
                    a = "something";
                }
            
            }
        """
    )
}
