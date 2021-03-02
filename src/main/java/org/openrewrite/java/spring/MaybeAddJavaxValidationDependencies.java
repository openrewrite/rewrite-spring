package org.openrewrite.java.spring;

import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.maven.MavenVisitor;
import org.openrewrite.maven.tree.Maven;

/**
 * Adds Javax validation-api and Spring Boot starter-validation if a prior recipe
 * set the javax-validation-exists value to True in the ExecutionContext
 *
 * e.g {@link ChangeDeprecatedHibernateValidationToJavax}
 */
@Value
public class MaybeAddJavaxValidationDependencies extends Recipe {

    static String JAVAX_VALIDATION_EXISTS = "javax-validation-exists";

    @Option(displayName = "Spring Boot Version", description = "spring-boot-starter-validation version")
    String springBootVersion;

    @Option(displayName = "Javax validation-api version", description = "Javax validation-api version")
    String javaxValidationApiVersion;

    @Override
    public String getDisplayName() {
        return "Add the javax validation-api and spring-boot-starter-validation if necessary";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MaybeAddSpringValidationVisitor();
    }

    private class MaybeAddSpringValidationVisitor extends MavenVisitor {
        @Override
        public Maven visitMaven(Maven maven, ExecutionContext ctx) {
            if (Boolean.TRUE.equals(ctx.pollMessage(JAVAX_VALIDATION_EXISTS))) {
                maybeAddDependency("javax.validation", "validation-api", "2.x", null, null, null);
                maybeAddDependency("org.springframework.boot", "spring-boot-starter-validation", "2.x", null, null, null);
            }
            return super.visitMaven(maven, ctx);
        }
    }
}
