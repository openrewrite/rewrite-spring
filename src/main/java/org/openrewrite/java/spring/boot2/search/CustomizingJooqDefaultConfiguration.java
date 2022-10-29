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
package org.openrewrite.java.spring.boot2.search;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Incubating;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Migration for Spring Boot 2.4 to 2.5
 * <a href="https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-2.5-Release-Notes#customizing-jooqs-defaultconfiguration">Customizing jOOQ’s DefaultConfiguration</a>
 */
@Incubating(since = "4.16.0")
public class CustomizingJooqDefaultConfiguration extends Recipe {
    private final List<String> jooqTypes = Arrays.asList(
            "org.jooq.conf.Settings",
            "org.jooq.ConnectionProvider",
            "org.jooq.ExecutorProvider",
            "org.jooq.TransactionProvider",
            "org.jooq.RecordMapperProvider",
            "org.jooq.RecordUnmapperProvider",
            "org.jooq.RecordListenerProvider",
            "org.jooq.ExecuteListenerProvider",
            "org.jooq.VisitListenerProvider",
            "org.jooq.TransactionListenerProvider"
    );

    @Override
    public String getDisplayName() {
        return "In Spring Boot 2.5 a `DefaultConfigurationCustomizer` can now be used in favour of defining one or more `*Provider` beans";
    }

    @Override
    public String getDescription() {
        return "To streamline the customization of jOOQ’s `DefaultConfiguration`, " +
                "a bean that implements `DefaultConfigurationCustomizer` can now be defined. " +
                "This customizer callback should be used in favour of defining one or more `*Provider` beans, " +
                "the support for which has now been deprecated. " +
                "See [Spring Boot 2.5 jOOQ customization](https://docs.spring.io/spring-boot/docs/2.5.x/reference/htmlsingle/#features.sql.jooq.customizing).";
    }

    @Override
    protected JavaIsoVisitor<ExecutionContext> getSingleSourceApplicableTest() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public JavaSourceFile visitJavaSourceFile(JavaSourceFile cu, ExecutionContext ctx) {
                for (String jooqType : jooqTypes) {
                    doAfterVisit(new UsesType<>(jooqType));
                }
                return cu;
            }
        };
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);

                if (isJooqCustomizationBean(md)) {
                    md = markAsMatch(md);
                }

                return md;
            }

            private J.MethodDeclaration markAsMatch(J.MethodDeclaration md) {
                return md.withMarkers(md.getMarkers().searchResult());
            }

            private boolean isJooqCustomizationBean(J.MethodDeclaration md) {
                return !FindAnnotations.find(md, "@org.springframework.context.annotation.Bean").isEmpty() &&
                        returnsJooqCustomizationType(md);
            }

            private boolean returnsJooqCustomizationType(J.MethodDeclaration md) {
                JavaType.Method methodType = md.getMethodType();
                if (methodType != null) {
                    for (String jooqType : jooqTypes) {
                        if (TypeUtils.isOfClassType(methodType.getReturnType(), jooqType)) {
                            return true;
                        }
                    }
                }
                return false;
            }
        };
    }
}
