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
package org.openrewrite.java.spring.search;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.spring.table.ApiEndpoints;
import org.openrewrite.java.spring.trait.SpringRequestMapping;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.marker.SearchResult;

@Value
@EqualsAndHashCode(callSuper = false)
public class FindApiEndpoints extends Recipe {

    transient ApiEndpoints apis = new ApiEndpoints(this);

    @Override
    public String getDisplayName() {
        return "Find Spring API endpoints";
    }

    @Override
    public String getDescription() {
        //language=markdown
        return "Find all HTTP API endpoints exposed by Spring applications. " +
               "More specifically, this marks method declarations annotated with `@RequestMapping`, `@GetMapping`, " +
               "`@PostMapping`, `@PutMapping`, `@DeleteMapping`, and `@PatchMapping` as search results.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

                SpringRequestMapping.Matcher matcher = new SpringRequestMapping.Matcher();
                for (J.Annotation annotation : service(AnnotationService.class).getAllAnnotations(getCursor())) {
                    m = matcher.get(annotation, getCursor()).map(requestMapping -> {
                        String path = requestMapping.getPath();
                        String httpMethod = requestMapping.getHttpMethod();

                        apis.insertRow(ctx, new ApiEndpoints.Row(
                                getCursor().firstEnclosingOrThrow(JavaSourceFile.class).getSourcePath().toString(),
                                method.getSimpleName(),
                                httpMethod,
                                path
                        ));

                        return SearchResult.found(method, httpMethod + " " + path);
                    }).orElse(m);
                }
                return m;
            }
        };
    }
}
