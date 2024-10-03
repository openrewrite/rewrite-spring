package org.openrewrite.java.spring.framework;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.trait.Traits;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.MethodCall;

public class MigrateResponseStatusExceptionGetRawStatusCodeMethod extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate `ResponseStatusException#getRawStatusCode()` to `getStatusCode().value()`";
    }

    @Override
    public String getDescription() {
        return "Migrate Spring Framework 5.3's `ResponseStatusException` method `getRawStatusCode()` to Spring Framework 6's `getStatusCode().value()`.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Traits.methodAccess("org.springframework.web.server.ResponseStatusException getRawStatusCode()")
                .asVisitor((mc, ctx) -> {
                    MethodCall tree = mc.getTree();
                    if (tree instanceof J.MethodInvocation) {
                        return JavaTemplate.builder("#{any()}.getStatusCode().value()")
                                .contextSensitive()
                                .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-core-6", "spring-beans-6", "spring-web-6"))
                                .build().apply(mc.getCursor(), tree.getCoordinates().replace(), ((J.MethodInvocation) tree).getSelect());
                    }
                    return tree;
                });
    }
}
