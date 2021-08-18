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
package org.openrewrite.java.spring.org.openrewrite.java.spring

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.openrewrite.java.JavaRecipeTest
import org.openrewrite.java.spring.boot2.MarkDeprecatedAPIs

class MarkDeprecatedAPIsTest : JavaRecipeTest {

    @Test
    fun markClassMethod() = assertChanged(
        recipe = MarkDeprecatedAPIs(
            "org.example in 4.10.0",
            "classMethod",
            "java.util.List add(..)",
            "use version x",
            "https://link.here"
        ),
        before = """
            package org.test;

            import java.util.ArrayList;
            import java.util.List;
            
            public class Test {
                public void method() {
                    List<Integer> example = new ArrayList<>();
                    example.add(1000);
                }
            }
        """,
        after = """
            package org.test;

            import java.util.ArrayList;
            import java.util.List;
            
            public class Test {
                public void method() {
                    List<Integer> example = new ArrayList<>();
                    /*~~> Source of classMethod deprecation: org.example in 4.10.0. Reason: use version x. Link: https://link.here. ~~*/example.add(1000);
                }
            }
        """
    )

    @Test
    fun markFieldConstant() = assertChanged(
        recipe = MarkDeprecatedAPIs(
            "org.example in 4.10.0",
            "fieldConstant",
            "org.a.Constants.VALUE",
            "use version x",
            "https://link.here"
        ),
        dependsOn = arrayOf("""
            package org.a;
            public class Constants {
                public static final int VALUE = 1000;
            }
        """),
        before = """
            package org.test;

            import org.a.Constants;

            public class Test {
                public void method() {
                    int i = Constants.VALUE;
                }
            }
        """,
        after = """
            package org.test;

            import org.a.Constants;
            
            public class Test {
                public void method() {
                    int i = /*~~> Source of fieldConstant deprecation: org.example in 4.10.0. Reason: use version x. Link: https://link.here. ~~*/Constants.VALUE;
                }
            }
        """
    )

    @Test
    fun markStaticFieldConstant() = assertChanged(
        recipe = MarkDeprecatedAPIs(
            "org.example in 4.10.0",
            "fieldConstant",
            "org.a.Constants.VALUE",
            "use version x",
            "https://link.here"
        ),
        dependsOn = arrayOf("""
            package org.a;
            public class Constants {
                public static final int VALUE = 1000;
            }
        """),
        before = """
            package org.test;

            import static org.a.Constants.VALUE;

            public class Test {
                public void method() {
                    int i = VALUE;
                }
            }
        """,
        after = """
            package org.test;

            import static org.a.Constants.VALUE;
            
            public class Test {
                public void method() {
                    int i = /*~~> Source of fieldConstant deprecation: org.example in 4.10.0. Reason: use version x. Link: https://link.here. ~~*/VALUE;
                }
            }
        """
    )

    @Test
    fun markInterface() = assertChanged(
        recipe = MarkDeprecatedAPIs(
            "org.example in 4.10.0",
            "interface",
            "org.test.AnInterface",
            "use version x",
            "https://link.here"
        ),
        dependsOn = arrayOf("""
            package org.test;
            
            public interface AnInterface {
                void method();
            }
        """),
        before = """
            package org.test;

            import java.util.ArrayList;
            import java.util.List;
            
            public class Test implements AnInterface{
                @Override
                public void method() {
                    List<Integer> example = new ArrayList<>();
                    example.add(1000);
                }
            }
        """,
        after = """
            package org.test;

            import java.util.ArrayList;
            import java.util.List;
            
            public class Test implements /*~~> Source of interface deprecation: org.example in 4.10.0. Reason: use version x. Link: https://link.here. ~~*/AnInterface{
                @Override
                public void method() {
                    List<Integer> example = new ArrayList<>();
                    example.add(1000);
                }
            }
        """
    )

    @Disabled
    @Test
    fun markMethodInterface() = assertChanged(
        recipe = MarkDeprecatedAPIs(
            "org.example in 4.10.0",
            "interfaceMethod",
            "* method()",
            "use version x",
            "https://link.here"
        ),
        dependsOn = arrayOf("""
            package org.test;
            
            public interface AnInterface {
                void method();
            }
        """),
        before = """
            package org.test;

            import java.util.ArrayList;
            import java.util.List;
            
            public class Test implements AnInterface{
                @Override
                public void method() {
                    List<Integer> example = new ArrayList<>();
                    example.add(1000);
                }
            }
        """,
        after = """
            package org.test;

            import java.util.ArrayList;
            import java.util.List;
            
            public class Test implements AnInterface{
                @Override
                public void /*~~> placeholder ~~*/method() {
                    List<Integer> example = new ArrayList<>();
                    example.add(1000);
                }
            }
        """
    )

    @Test
    fun markClass() = assertChanged(
        recipe = MarkDeprecatedAPIs(
            "org.example in 4.10.0",
            "class",
            "org.test.DeprecatedClass",
            "use version x",
            "https://link.here"
        ),
        dependsOn = arrayOf("""
            package org.test;
            
            public class DeprecatedClass {}
        """),
        before = """
            package org.test;
            
            public class Test {
            
                public void method() {
                    DeprecatedClass deprecated = new DeprecatedClass();
                }
            }
        """,
        after = """
            package org.test;
            
            public class Test {
            
                public void method() {
                    /*~~> Source of class deprecation: org.example in 4.10.0. Reason: use version x. Link: https://link.here. ~~*/DeprecatedClass deprecated = new /*~~> Source of class deprecation: org.example in 4.10.0. Reason: use version x. Link: https://link.here. ~~*/DeprecatedClass();
                }
            }
        """
    )

    @Disabled
    @Test
    fun markConstructor() = assertChanged(
        recipe = MarkDeprecatedAPIs(
            "org.example in 4.10.0",
            "constructor",
            "org.test.DeprecatedClass DeprecatedClass(java.lang.int, java.lang.boolean)",
            "use version x",
            "https://link.here"
        ),
        dependsOn = arrayOf("""
            package org.test;
            
            public class DeprecatedClass {
                public DeprecatedClass() {}
                public DeprecatedClass(int valA, boolean valB) {}
            }
        """),
        before = """
            package org.test;
            
            public class Test {
            
                public void method() {
                    int valA = 1;
                    boolean valB = true;
                    DeprecatedClass deprecated = new DeprecatedClass(valA, valB);
                    deprecated = new DeprecatedClass();
                }
            }
        """,
        after = """
            package org.test;
            
            public class Test {
            
                public void method() {
                    int valA = 1, valB = 2;
                    DeprecatedClass deprecated = new DeprecatedClass(valA, valB);
                    deprecated = new DeprecatedClass();
                }
            }
        """
    )

    @Test
    fun markEnum() = assertChanged(
        recipe = MarkDeprecatedAPIs(
            "org.example in 4.10.0",
            "enum",
            "org.test.DeprecatedEnum",
            "use version x",
            "https://link.here"
        ),
        dependsOn = arrayOf("""
            package org.test;
            
            public enum DeprecatedEnum { V_1, V_2, V_3 }
        """),
        before = """
            package org.test;
            
            public class Test {
            
                public void method() {
                    DeprecatedEnum value = DeprecatedEnum.V_1;
                }
            }
        """,
        after = """
            package org.test;

            public class Test {

                public void method() {
                    /*~~> Source of enum deprecation: org.example in 4.10.0. Reason: use version x. Link: https://link.here. ~~*/DeprecatedEnum value = /*~~> Source of enum deprecation: org.example in 4.10.0. Reason: use version x. Link: https://link.here. ~~*//*~~> Source of enum deprecation: org.example in 4.10.0. Reason: use version x. Link: https://link.here. ~~*/DeprecatedEnum.V_1;
                }
            }
        """
    )

    @Test
    fun enumValueReference() = assertChanged(
        recipe = MarkDeprecatedAPIs(
            "org.example in 4.10.0",
            "enumConstant",
            "org.a.DeprecatedEnum.V_1",
            "use version x",
            "https://link.here"
        ),
        dependsOn = arrayOf("""
            package org.a;
            
            public enum DeprecatedEnum { V_1, V_2, V_3 }
        """),
        before = """
            package org.test;
            
            import org.a.DeprecatedEnum;

            public class Test {
            
                public void method() {
                    DeprecatedEnum v1 = DeprecatedEnum.V_1;
                }
            }
        """,
        after = """
            package org.test;
            
            import org.a.DeprecatedEnum;

            public class Test {
            
                public void method() {
                    DeprecatedEnum v1 = /*~~> Source of enumConstant deprecation: org.example in 4.10.0. Reason: use version x. Link: https://link.here. ~~*/DeprecatedEnum.V_1;
                }
            }
        """
    )

    @Test
    fun enumStaticValueReference() = assertChanged(
        recipe = MarkDeprecatedAPIs(
            "org.example in 4.10.0",
            "enumConstant",
            "org.a.DeprecatedEnum.V_2",
            "use version x",
            "https://link.here"
        ),
        dependsOn = arrayOf("""
            package org.a;
            
            public enum DeprecatedEnum { V_1, V_2, V_3 }
        """),
        before = """
            package org.test;
            
            import org.a.DeprecatedEnum;

            import static org.a.DeprecatedEnum.V_2;

            public class Test {
            
                public void method() {
                    DeprecatedEnum v2 = V_2;
                }
            }
        """,
        after = """
            package org.test;
            
            import org.a.DeprecatedEnum;

            import static org.a.DeprecatedEnum.V_2;

            public class Test {
            
                public void method() {
                    DeprecatedEnum v2 = /*~~> Source of enumConstant deprecation: org.example in 4.10.0. Reason: use version x. Link: https://link.here. ~~*/V_2;
                }
            }
        """
    )

    @Test
    fun markException() = assertChanged(
        recipe = MarkDeprecatedAPIs(
            "org.example in 4.10.0",
            "exception",
            "org.test.DeprecatedException",
            "use version x",
            "https://link.here"
        ),
        dependsOn = arrayOf("""
            package org.test;
            
            public class DeprecatedException extends Exception {}
        """),
        before = """
            package org.test;
            
            public class Test {
            
                public void method() {
                    try {
                        int i = 1 + 2;
                    } catch (DeprecatedException ex) {
                        throw new RuntimeException("message", ex);
                    }
                }
            }
        """,
        after = """
            package org.test;
            
            public class Test {
            
                public void method() {
                    try {
                        int i = 1 + 2;
                    } catch (/*~~> Source of exception deprecation: org.example in 4.10.0. Reason: use version x. Link: https://link.here. ~~*/DeprecatedException ex) {
                        throw new RuntimeException("message", ex);
                    }
                }
            }
        """
    )
}
