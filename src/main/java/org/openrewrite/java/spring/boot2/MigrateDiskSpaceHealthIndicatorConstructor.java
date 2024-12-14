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
package org.openrewrite.java.spring.boot2;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

public class MigrateDiskSpaceHealthIndicatorConstructor extends Recipe {

    @Override
    public String getDisplayName() {
        return "Use `DiskSpaceHealthIndicator(File, DataSize)`";
    }

    @Override
    public String getDescription() {
        return "`DiskSpaceHealthIndicator(File, long)` was deprecated in Spring Data 2.1 for removal in 2.2.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.springframework.boot.actuate.system.DiskSpaceHealthIndicator", false), new JavaVisitor<ExecutionContext>() {
            final String diskSpaceHealthIndicatorFqn = "org.springframework.boot.actuate.system.DiskSpaceHealthIndicator";

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                if (TypeUtils.isOfClassType(newClass.getType(), diskSpaceHealthIndicatorFqn) &&
                        newClass.getConstructorType() != null &&
                        TypeUtils.isOfType(newClass.getConstructorType().getParameterTypes().get(0), JavaType.buildType("java.io.File")) &&
                        TypeUtils.isOfType(newClass.getConstructorType().getParameterTypes().get(1), JavaType.Primitive.Long)) {

                    maybeAddImport("org.springframework.util.unit.DataSize");
                    return JavaTemplate.builder("new DiskSpaceHealthIndicator(#{any(java.io.File)}, DataSize.ofBytes(#{any(long)}))")
                        .imports("org.springframework.util.unit.DataSize")
                        .javaParser(JavaParser.fromJavaVersion()
                            .classpathFromResources(ctx, "spring-boot-actuator-2.*", "spring-core-5.*"))
                        .build().apply(
                            getCursor(),
                            newClass.getCoordinates().replace(),
                            newClass.getArguments().get(0),
                            newClass.getArguments().get(1));
                }

                return super.visitNewClass(newClass, ctx);
            }
        });
    }
}
