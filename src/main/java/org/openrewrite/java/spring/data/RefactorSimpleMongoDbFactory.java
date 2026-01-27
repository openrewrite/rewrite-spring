/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.spring.data;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.MethodCall;

public class RefactorSimpleMongoDbFactory extends Recipe {

    private static final String COM_MONGODB_MONGO_CLIENT_URI = "com.mongodb.MongoClientURI";
    private static final String SIMPLE_MONGO_DB_FACTORY = "org.springframework.data.mongodb.core.SimpleMongoDbFactory";
    private static final MethodMatcher SIMPLE_FACTORY_CONSTRUCTOR = new MethodMatcher(SIMPLE_MONGO_DB_FACTORY + " <constructor>(..)", true);
    private static final MethodMatcher MONGO_CLIENT_CONSTRUCTOR = new MethodMatcher(COM_MONGODB_MONGO_CLIENT_URI + " <constructor>(String)", true);

    private static final String SIMPLE_MONGO_CLIENT_DB_FACTORY = "org.springframework.data.mongodb.core.SimpleMongoClientDbFactory";

    @Getter
    final String displayName = "Use `new SimpleMongoClientDbFactory(String)`";

    @Getter
    final String description = "Replace usage of deprecated `new SimpleMongoDbFactory(new MongoClientURI(String))` with `new SimpleMongoClientDbFactory(String)`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JavaVisitor<ExecutionContext> javaVisitor = new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                if (SIMPLE_FACTORY_CONSTRUCTOR.matches(newClass)) {
                    Expression expression = newClass.getArguments().get(0);
                    if (MONGO_CLIENT_CONSTRUCTOR.matches(expression)) {
                        Expression uri = ((MethodCall) expression).getArguments().get(0);
                        maybeRemoveImport(SIMPLE_MONGO_DB_FACTORY);
                        maybeRemoveImport(COM_MONGODB_MONGO_CLIENT_URI);
                        maybeAddImport(SIMPLE_MONGO_CLIENT_DB_FACTORY);
                        return JavaTemplate
                                .builder("new SimpleMongoClientDbFactory(#{any(java.lang.String)})")
                                .imports(SIMPLE_MONGO_CLIENT_DB_FACTORY)
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-data-mongodb-2", "mongo-java-driver"))
                                .build()
                                .apply(getCursor(), newClass.getCoordinates().replace(), uri);
                    }
                }
                return super.visitNewClass(newClass, ctx);
            }
        };
        return Preconditions.check(
                Preconditions.and(
                        new UsesMethod<>(SIMPLE_FACTORY_CONSTRUCTOR),
                        new UsesMethod<>(MONGO_CLIENT_CONSTRUCTOR)
                ),
                javaVisitor
        );
    }
}
