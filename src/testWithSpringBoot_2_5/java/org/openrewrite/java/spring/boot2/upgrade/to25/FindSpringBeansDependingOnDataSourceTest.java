/*
 * Copyright 2021 VMware, Inc.
 * SPDX-License-Identifier: Apache License 2.0
 *
 * @author: fkrueger
 */
package org.openrewrite.java.spring.boot2.upgrade.to25;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FindSpringBeansDependingOnDataSourceTest {

    private FindSpringBeansDependingOnDataSource sut = new FindSpringBeansDependingOnDataSource();

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
                        "    public method("+theParameter+") {}\n" +
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
                    "    public method() {}\n" +
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
                            "    public method() {}\n" +
                            "}";
            assertMatches(0, given);
        }

    }

    private void assertMatches(int numMatches, String given) {
        List<J.CompilationUnit> rewriteSourceFileHolders = getRewriteSourceFileHolders(given);
        assertThat(rewriteSourceFileHolders).hasSize(numMatches);
    }


    private List<J.CompilationUnit> getRewriteSourceFileHolders(String... given) {
        List<J.CompilationUnit> compilationUnits = JavaParser.fromJavaVersion()
                .classpath("mysql-connector-java")
                .build()
                .parse(given);

        return sut.run(compilationUnits).stream().map(r -> (J.CompilationUnit)r.getAfter()).collect(Collectors.toList());
//
//        ProjectContext context = TestProjectContext.buildProjectContext()
//                .withJavaSources(given)
//                .withBuildFileHavingDependencies("org.springframework.boot:spring-boot:2.5.6", "mysql:mysql-connector-java:8.0.27", "org.springframework.boot:spring-boot-test:2.5.6")
//                .build();
//
//        return context.getProjectJavaSources().find(sut);
    }
}