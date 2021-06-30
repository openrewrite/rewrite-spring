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

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.openrewrite.Issue
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class NoAutowiredTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .classpath("spring-beans")
            .build()

    override val recipe: Recipe
        get() = NoAutowiredOnConstructor()

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    fun removeLeadingAutowiredAnnotation() = assertChanged(
        dependsOn = arrayOf(
            """
            package org.C;
            import org.springframework.stereotype.Component;
            @Component
            public class TestSourceA {
            }
        """,
        """
            package org.B;
            import org.springframework.stereotype.Component;
            @Component
            public class TestSourceB {
            }
        """,
        """
            package org.C;
            import org.springframework.stereotype.Component;
            @Component
            public class TestSourceC {
            }
        """),
        before = """
            import org.A.TestSourceA;
            import org.B.TestSourceB;
            import org.C.TestSourceC;
            import org.springframework.beans.factory.annotation.Autowired;
            
            public class TestConfiguration {
                private final TestSourceA testSourceA;
                private TestSourceB testSourceB;
            
                @Autowired
                private TestSourceC testSourceC;
            
                @Autowired
                public TestConfiguration(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            
                @Autowired
                public void setTestSourceB(TestSourceB testSourceB) {
                    this.testSourceB = testSourceB;
                }
            }
        """,
        after = """
            import org.A.TestSourceA;
            import org.B.TestSourceB;
            import org.C.TestSourceC;
            import org.springframework.beans.factory.annotation.Autowired;
            
            public class TestConfiguration {
                private final TestSourceA testSourceA;
                private TestSourceB testSourceB;
            
                @Autowired
                private TestSourceC testSourceC;
            
                public TestConfiguration(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            
                @Autowired
                public void setTestSourceB(TestSourceB testSourceB) {
                    this.testSourceB = testSourceB;
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    fun removeLeadingAutowiredAnnotationNoModifiers() = assertChanged(
        dependsOn = arrayOf(
            """
            package org.C;
            import org.springframework.stereotype.Component;
            @Component
            public class TestSourceA {
            }
        """,
            """
            package org.B;
            import org.springframework.stereotype.Component;
            @Component
            public class TestSourceB {
            }
        """,
            """
            package org.C;
            import org.springframework.stereotype.Component;
            @Component
            public class TestSourceC {
            }
        """),
        before = """
            import org.A.TestSourceA;
            import org.B.TestSourceB;
            import org.C.TestSourceC;
            import org.springframework.beans.factory.annotation.Autowired;
            
            public class TestConfiguration {
                private final TestSourceA testSourceA;
                private TestSourceB testSourceB;
            
                @Autowired
                private TestSourceC testSourceC;
            
                @Autowired
                TestConfiguration(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            
                @Autowired
                public void setTestSourceB(TestSourceB testSourceB) {
                    this.testSourceB = testSourceB;
                }
            }
        """,
        after = """
            import org.A.TestSourceA;
            import org.B.TestSourceB;
            import org.C.TestSourceC;
            import org.springframework.beans.factory.annotation.Autowired;
            
            public class TestConfiguration {
                private final TestSourceA testSourceA;
                private TestSourceB testSourceB;
            
                @Autowired
                private TestSourceC testSourceC;
            
                TestConfiguration(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            
                @Autowired
                public void setTestSourceB(TestSourceB testSourceB) {
                    this.testSourceB = testSourceB;
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    fun removeAutowiredWithMultipleAnnotation() = assertChanged(
        dependsOn = arrayOf(
            """
            package org.C;
            import org.springframework.stereotype.Component;
            @Component
            public class TestSourceA {
            }
        """),
        before = """
            import org.A.TestSourceA;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.beans.factory.annotation.Qualifier;
            import org.springframework.beans.factory.annotation.Required;
            
            public class AnnotationPos1 {
                private final TestSourceA testSourceA;
            
                @Autowired
                @Required
                @Qualifier
                public TestConfiguration(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            }
            
            public class AnnotationPos2 {
                private final TestSourceA testSourceA;
            
                @Required
                @Autowired
                @Qualifier
                public TestConfiguration(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            }
            
            public class AnnotationPos3 {
                private final TestSourceA testSourceA;
            
                @Required
                @Qualifier
                @Autowired
                public TestConfiguration(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            }
        """,
        after = """
            import org.A.TestSourceA;
            import org.springframework.beans.factory.annotation.Qualifier;
            import org.springframework.beans.factory.annotation.Required;
            
            public class AnnotationPos1 {
                private final TestSourceA testSourceA;
            
                @Required
                @Qualifier
                publicAnnotationPos1 TestConfiguration(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            }
            
            public class AnnotationPos2 {
                private final TestSourceA testSourceA;
            
                @Required
                @Qualifier
                publicAnnotationPos2 TestConfiguration(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            }
            
            public class AnnotationPos3 {
                private final TestSourceA testSourceA;
            
                @Required
                @Qualifier
                publicAnnotationPos3 TestConfiguration(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    fun removeAutowiredWithMultipleInLineAnnotation() = assertChanged(
        dependsOn = arrayOf(
            """
            package org.C;
            import org.springframework.stereotype.Component;
            @Component
            public class TestSourceA {
            }
        """),
        before = """
            import org.A.TestSourceA;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.beans.factory.annotation.Qualifier;
            import org.springframework.beans.factory.annotation.Required;
            
            public class AnnotationPos1 {
                private final TestSourceA testSourceA;
            
                @Autowired @Required @Qualifier
                public TestConfiguration(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            }
            
            public class AnnotationPos2 {
                private final TestSourceA testSourceA;
            
                @Required @Autowired @Qualifier
                public TestConfiguration(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            }
            
            public class AnnotationPos3 {
                private final TestSourceA testSourceA;
            
                @Required @Qualifier @Autowired
                public TestConfiguration(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            }
        """,
        after = """
            import org.A.TestSourceA;
            import org.springframework.beans.factory.annotation.Qualifier;
            import org.springframework.beans.factory.annotation.Required;
            
            public class AnnotationPos1 {
                private final TestSourceA testSourceA;
            
                @Required @Qualifier
                publicAnnotationPos1 TestConfiguration(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            }
            
            public class AnnotationPos2 {
                private final TestSourceA testSourceA;
            
                @Required @Qualifier
                publicAnnotationPos2 TestConfiguration(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            }
            
            public class AnnotationPos3 {
                private final TestSourceA testSourceA;
            
                @Required @Qualifier
                publicAnnotationPos3 TestConfiguration(TestSourceA testSourceA) {
                    this.testSourceA = testSourceA;
                }
            }
        """
    )
    @Disabled
    @Issue("https://github.com/openrewrite/rewrite/issues/726")
    @Test
    fun removeNamePrefixAutowiredAnnotation() = assertChanged(
        before = """
            import javax.sql.DataSource;
            import org.springframework.beans.factory.annotation.Autowired;
            
            public class DatabaseConfiguration {
                private final DataSource dataSource;
                
                public @Autowired DatabaseConfiguration(DataSource dataSource) {
                }
            }
        """,
        after = """
            import javax.sql.DataSource;
            
            public class DatabaseConfiguration {
                private final DataSource dataSource;
                
                public DatabaseConfiguration(DataSource dataSource) {
                }
            }
        """
    )

    @Issue("https://github.com/openrewrite/rewrite-spring/issues/78")
    @Test
    fun keepAutowiredAnnotationsWhenMultipleConstructorsExist() = assertUnchanged(
        before = """
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.core.io.Resource;
            import java.io.PrintStream;
            
            public class MyAppResourceService {
                private final Resource someResource;
                private final PrintStream printStream;
            
                public MyAppResourceService(Resource someResource) {
                    this.someResource = someResource;
                    this.printStream = System.out;
                }
            
                @Autowired
                public MyAppResourceService(Resource someResource, PrintStream printStream) {
                    this.someResource = someResource;
                    this.printStream = printStream;
                }
            }
        """
    )

    @Test
    fun optionalAutowiredAnnotations() = assertUnchanged(
        before = """
            import org.springframework.beans.factory.annotation.Autowired;
            import javax.sql.DataSource;
            
            public class DatabaseConfiguration {
                private final DataSource dataSource;

                public DatabaseConfiguration(@Autowired(required = false) DataSource dataSource) {
                }
            }
        """
    )

    @Test
    fun noAutowiredAnnotations() = assertUnchanged(
        before = """
            import javax.sql.DataSource;
            
            public class DatabaseConfiguration {
                private final DataSource dataSource;

                @Primary
                public DatabaseConfiguration(DataSource dataSource) {
                }
            }
        """
    )
}
