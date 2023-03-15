package org.openrewrite.java.spring.boot3;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MaintainTrailingSlashURLMappings extends Recipe {

    private static final String WEB_MVC_CONFIGUER = "org.springframework.web.servlet.config.annotation.WebMvcConfigurer";
    private static final String WEB_FLUX_CONFIGUER = "org.springframework.web.reactive.config.WebFluxConfigurer";

    @Override
    public String getDisplayName() {
        return "Maintain trailing slash URL mappings";
    }

    @Override
    public String getDescription() {
        return "This is part of Spring MVC and WebFlux URL Matching Changes, as of Spring Framework 6.0, the trailing" +
               " slash matching configuration option has been deprecated and its default value set to false. " +
               "This means that previously, a controller  \"@GetMapping(\"/some/greeting\")\" would match both" +
               " \"GET /some/greeting\" and \"GET /some/greeting/\", but it doesn't match  \"GET /some/greeting/\" " +
               "anymore by default and will result in an HTTP 404 error. This recipe is to maintain trailing slash in " +
               "all HTTP url mappings.";
    }

    @Override
    protected List<SourceFile> visit(List<SourceFile> before, ExecutionContext ctx) {
        boolean anyConfigOverridden = false;

        for (SourceFile s : before) {
            if (s instanceof JavaSourceFile) {
                JavaSourceFile cu = (JavaSourceFile) s;
                anyConfigOverridden = FindWebMvcConfigurer.find(cu);
                if (anyConfigOverridden) {
                    break;
                }
            }
        }

        if (anyConfigOverridden) {
            return before;
        }

        doNext(new AddRouteTrailingSlash());
        return before;
    }

    private static class FindWebMvcConfigurer extends JavaIsoVisitor<AtomicBoolean> {
        static boolean find(J j) {
            return new FindWebMvcConfigurer()
                .reduce(j, new AtomicBoolean()).get();
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, AtomicBoolean atomicBoolean) {
            if (classDecl.getImplements() != null) {
                for (TypeTree impl : classDecl.getImplements()) {
                    JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(impl.getType());
                    if (fullyQualified != null &&
                        (WEB_MVC_CONFIGUER.equals(fullyQualified.getFullyQualifiedName()) ||
                         WEB_FLUX_CONFIGUER.equals(fullyQualified.getFullyQualifiedName()))
                    ) {
                        atomicBoolean.set(true);
                        return classDecl;
                    }
                }
            }
            return classDecl;
        }
    }
}
