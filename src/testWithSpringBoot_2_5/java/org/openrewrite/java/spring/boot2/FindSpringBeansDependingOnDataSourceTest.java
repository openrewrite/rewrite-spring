/*
 * Copyright 2021 VMware, Inc.
 * SPDX-License-Identifier: Apache License 2.0
 *
 * @author: fkrueger
 */
package org.openrewrite.java.spring.boot2;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FindSpringBeansDependingOnDataSourceTest {

    private FindClassesDependingOnDataSource sut = new FindClassesDependingOnDataSource();

    @Test
    void shouldAddResultToExecutionContext() {
        String given =
                "import javax.sql.DataSource;\n " +
                        "public class MyClass<T extends DataSource>{}";
        ExecutionContext ctx = new InMemoryExecutionContext();
        List<J.CompilationUnit> compilationUnits = parse(given);

        sut.run(compilationUnits, ctx);
        FindClassesDependingOnDataSource.Matches matches = ctx.getMessage(FindClassesDependingOnDataSource.CLASSES_USING_DATA_SOURCE);

        assertThat(matches).isNotNull();
        assertThat(matches.getAll()).hasSize(1);
        assertThat(matches.getAll().get(0)).isEqualTo("MyClass");
    }

    @Nested
    public class GivenSpringComponentExists {

        @ParameterizedTest
        @CsvSource({
                "Component, org.springframework.stereotype.Component, import javax.sql.DataSource;, DataSource ds",
                "Service, org.springframework.stereotype.Service, import javax.sql.DataSource;, DataSource ds",
                "Repository, org.springframework.stereotype.Repository, import javax.sql.DataSource;, DataSource ds",
                "TestComponent, org.springframework.boot.test.context.TestComponent, import javax.sql.DataSource;, DataSource ds",
                
                "Component, org.springframework.stereotype.Component, import com.mysql.cj.jdbc.MysqlDataSource;,MysqlDataSource ds",
                "Service, org.springframework.stereotype.Service, import com.mysql.cj.jdbc.MysqlDataSource;,MysqlDataSource ds",
                "Repository, org.springframework.stereotype.Repository, import com.mysql.cj.jdbc.MysqlDataSource;,MysqlDataSource ds",
                "TestComponent, org.springframework.boot.test.context.TestComponent, import com.mysql.cj.jdbc.MysqlDataSource;,MysqlDataSource ds",
        })
        void whenDependsOnDataSourceThenReturnResult(String beanType, String beanImport, String theImport, String theParameter) {
            String given =
                        "import " + beanImport+";\n" +
                        theImport + "\n" +
                        "@" + beanType + "\n" +
                        "public class SomeComponent {\n" +
                        "    public void method("+theParameter+") {}\n" +
                        "}";
            assertMatches(1, given);
        }

        @ParameterizedTest
        @CsvSource({
                "Component, org.springframework.stereotype.Component",
                "Service, org.springframework.stereotype.Service",
                "Repository, org.springframework.stereotype.Repository",
                "TestComponent, org.springframework.boot.test.context.TestComponent",
        })
        void whenDoesntDependOnDataSourceThenReturnNoResult(String beanType, String beanImport) {
            String given =
                    "import " + beanImport+";\n" +
                    "@" + beanType + "\n" +
                    "public class SomeComponent {\n" +
                    "    public void method() {}\n" +
                    "}";
            assertMatches(0, given);
        }


        @Test
        void whenOnlyImportsDataSourceThenReturnNoResult() {
            String given =
                    "import org.springframework.stereotype.Component;\n" +
                            "import javax.sql.DataSource;\n" +
                            "@Component\n" +
                            "public class SomeComponent {\n" +
                            "    public void method() {}\n" +
                            "}";
            assertMatches(0, given);
        }

    }

    private void assertMatches(int numMatches, String given) {
        List<String> result = compileGivenAndApplyRecipe(given);
        assertThat(result).hasSize(numMatches);
    }


    private List<String> compileGivenAndApplyRecipe(String... given) {
        List<J.CompilationUnit> compilationUnits = parse(given);

        ExecutionContext executionContext = new InMemoryExecutionContext();
        sut.run(compilationUnits, executionContext);
        return ((FindClassesDependingOnDataSource.Matches)executionContext.getMessage(FindClassesDependingOnDataSource.CLASSES_USING_DATA_SOURCE)).getAll()
                .stream().map(r -> (String)r).collect(Collectors.toList());
    }

    @NotNull
    private List<J.CompilationUnit> parse(String... sources) {
        List<J.CompilationUnit> compilationUnits = JavaParser.fromJavaVersion()
                .logCompilationWarningsAndErrors(true)
                .classpath("mysql-connector-java")
                .build()
                .parse(sources);
        return compilationUnits;
    }
}