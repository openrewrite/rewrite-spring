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
package org.openrewrite.java.spring.boot2;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
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

    @Nullable
    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.springframework.boot.actuate.system.DiskSpaceHealthIndicator");
    }

    @Override
    protected JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            final String diskSpaceHealthIndicatorFqn = "org.springframework.boot.actuate.system.DiskSpaceHealthIndicator";

            @Override
            public J visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                if (TypeUtils.isOfClassType(newClass.getType(), diskSpaceHealthIndicatorFqn) &&
                        newClass.getConstructorType() != null &&
                        TypeUtils.isOfType(newClass.getConstructorType().getParameterTypes().get(0), JavaType.buildType("java.io.File")) &&
                        TypeUtils.isOfType(newClass.getConstructorType().getParameterTypes().get(1), JavaType.Primitive.Long) &&
                        newClass.getArguments() != null) {
                    maybeAddImport("org.springframework.util.unit.DataSize");
                    return newClass.withTemplate(
                            JavaTemplate.builder(this::getCursor, "new DiskSpaceHealthIndicator(#{any(java.io.File)}, DataSize.ofBytes(#{any(long)}))")
                                    .imports("org.springframework.util.unit.DataSize")
                                    .javaParser(() -> JavaParser.fromJavaVersion()
                                            .dependsOn(
                                                    "package org.springframework.boot.actuate.health;" +
                                                            "public interface HealthContributor {}" +
                                                    "package org.springframework.boot.actuate.health;" +
                                                            "public interface HealthIndicator extends HealthContributor {}",
                                                    "package org.springframework.boot.actuate.health;" +
                                                            "public abstract class AbstractHealthIndicator implements HealthIndicator {}",
                                                    "package org.springframework.util.unit;" +
                                                            "import java.io.Serializable;" +
                                                            "public final class DataSize implements Comparable<DataSize>, Serializable {" +
                                                            "public static DataSize ofBytes(long bytes) { return null; }" +
                                                            "public static DataSize parse(CharSequence text) { return null; }" +
                                                            "}",
                                                    "package org.springframework.boot.actuate.system;" +
                                                            "import java.io.File;" +
                                                            "import org.springframework.boot.actuate.health.AbstractHealthIndicator;" +
                                                            "import org.springframework.util.unit.DataSize;" +
                                                            "public class DiskSpaceHealthIndicator extends AbstractHealthIndicator {" +
                                                            "public DiskSpaceHealthIndicator(File path, long threshold) {}" +
                                                            "public DiskSpaceHealthIndicator(File path, DataSize threshold) {}" +
                                                            "}")
                                            .build())
                                    .build(),
                            newClass.getCoordinates().replace(),
                            newClass.getArguments().get(0),
                            newClass.getArguments().get(1));
                }

                return super.visitNewClass(newClass, ctx);
            }
        };
    }
}
