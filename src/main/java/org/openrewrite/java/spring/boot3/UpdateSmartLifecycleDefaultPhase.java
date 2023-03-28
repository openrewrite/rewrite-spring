package org.openrewrite.java.spring.boot3;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.PartProvider;
import org.openrewrite.java.tree.J;

public class UpdateSmartLifecycleDefaultPhase extends Recipe  {
    private static final MethodMatcher GET_PHASE_MATCHER = new MethodMatcher(
        "org.springframework.context.SmartLifecycle getPhase()", true
    );

    @Nullable
    private static J.Identifier defaultPhaseIdentifier = null;

    @Override
    public String getDisplayName() {
        return "Use `SmartLifecycle.DEFAULT_PHASE` instead of Integer.MAX_VALUE";
    }

    @Override
    public String getDescription() {
        return "The phases used by the SmartLifecycle implementations for graceful shutdown have been updated. " +
               "Graceful shutdown now begins in phase SmartLifecycle.DEFAULT_PHASE - 2048 and the web server is " +
               "stopped in phase SmartLifecycle.DEFAULT_PHASE - 1024. Any SmartLifecycle implementations that were " +
               "participating in graceful shutdown should be updated accordingly.";
    }


    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl,
                                                            ExecutionContext executionContext) {
                return super.visitClassDeclaration(classDecl, executionContext);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
                                                              ExecutionContext ctx) {
                if (GET_PHASE_MATCHER.matches(method.getMethodType())) {
                    return (J.MethodDeclaration) new JavaVisitor<ExecutionContext>() {

                        @Override
                        public J visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext executionContext) {
                            if (fieldAccess.toString().equals("Integer.MAX_VALUE")) {
                                // replace with `SmartLifecycle.DEFAULT_PHASE`
                                return getDefaultPhaseIdentifier();
                            }

                            return fieldAccess;
                        }
                    }.visit(method, ctx);
                }
                return super.visitMethodDeclaration(method, ctx);
            }

        };
    }

    private static J.Identifier getDefaultPhaseIdentifier() {
        if (defaultPhaseIdentifier == null) {
            J.MethodInvocation m = PartProvider.buildPart("import org.springframework.context.SmartLifecycle;\n" +
                                                            "class MyLifeCycle implements SmartLifecycle {\n" +
                                                            "\t@Override public void start() {}\n" +
                                                            "\t@Override public void stop() {}\n" +
                                                            "\t@Override public boolean isRunning() { return false; " +
                                                            "}\n" +
                                                            "\t@Override public int getPhase() { return " +
                                                            "DEFAULT_PHASE;}\n" +
                                                            "\tvoid method(int i) {}\n" +
                                                            "\tvoid methodCall() {method(DEFAULT_PHASE);}\n" +
                                                            "}", J.MethodInvocation.class, "spring-context");
            defaultPhaseIdentifier = (J.Identifier) m.getArguments().get(0);
        }
        return defaultPhaseIdentifier;
    }
}
