/*
 * Copyright 2021 the original author or authors.
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

import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest
import org.openrewrite.java.spring.data.MigrateJpaSort

class MigrateJpaSortTest : JavaRecipeTest {
    override val parser: JavaParser
        get() = JavaParser.fromJavaVersion()
            .classpath("javax.persistence", "spring-data-jpa", "spring-data-commons")
            .build()

    override val recipe: Recipe
        get() = MigrateJpaSort()


    @Test
    fun constructorWithAttribute() = assertChanged(
        before = """
            import org.springframework.data.jpa.domain.JpaSort;
            import javax.persistence.metamodel.Attribute;

            class Test {
                Attribute<?, ?> attr;
                JpaSort onlyAttr = new JpaSort(attr);
            }
        """,
        after = """
            import org.springframework.data.jpa.domain.JpaSort;
            import javax.persistence.metamodel.Attribute;

            class Test {
                Attribute<?, ?> attr;
                JpaSort onlyAttr = JpaSort.of(attr);
            }
        """
    )

    @Test
    fun constructorWithDirectionAndAttribute() = assertChanged(
        before = """
            import org.springframework.data.jpa.domain.JpaSort;
            import org.springframework.data.domain.Sort.Direction;
            import javax.persistence.metamodel.Attribute;

            class Test {
                Attribute<?, ?> attr;
                JpaSort directionAndAttr = new JpaSort(Direction.DESC, attr);
            }
        """,
        after = """
            import org.springframework.data.jpa.domain.JpaSort;
            import org.springframework.data.domain.Sort.Direction;
            import javax.persistence.metamodel.Attribute;

            class Test {
                Attribute<?, ?> attr;
                JpaSort directionAndAttr = JpaSort.of(Direction.DESC, attr);
            }
        """
    )

    @Test
    fun constructorWithPath() = assertChanged(
        before = """
            import org.springframework.data.jpa.domain.JpaSort;
            import org.springframework.data.jpa.domain.JpaSort.Path;

            class Test {
                Path<?, ?> path;
                JpaSort onlyPath = new JpaSort(path);
            }
        """,
        after = """
            import org.springframework.data.jpa.domain.JpaSort;
            import org.springframework.data.jpa.domain.JpaSort.Path;

            class Test {
                Path<?, ?> path;
                JpaSort onlyPath = JpaSort.of(path);
            }
        """
    )

    @Test
    fun constructorWithDirectionAndPath() = assertChanged(
            before = """
            import org.springframework.data.jpa.domain.JpaSort;
            import org.springframework.data.jpa.domain.JpaSort.Path;
            import org.springframework.data.domain.Sort.Direction;

            class Test {
                Path<?, ?> path;
                JpaSort directionAndPath = new JpaSort(Direction.DESC, path);
            }
        """,
            after = """
            import org.springframework.data.jpa.domain.JpaSort;
            import org.springframework.data.jpa.domain.JpaSort.Path;
            import org.springframework.data.domain.Sort.Direction;

            class Test {
                Path<?, ?> path;
                JpaSort directionAndPath = JpaSort.of(Direction.DESC, path);
            }
        """
    )
}
