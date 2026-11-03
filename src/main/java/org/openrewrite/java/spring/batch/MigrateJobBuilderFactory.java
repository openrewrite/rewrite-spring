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
package org.openrewrite.java.spring.batch;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.search.UsesMethod;

public class MigrateJobBuilderFactory extends Recipe {

    private static final String JOB_BUILDER_FACTORY = "org.springframework.batch.core.configuration.annotation.JobBuilderFactory";

    @Getter
    final String displayName = "Migrate `JobBuilderFactory` to `JobBuilder`";

    @Getter
    final String description = "`JobBuilderFactory` was deprecated in spring-batch 5.x. It is replaced by `JobBuilder`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(JOB_BUILDER_FACTORY + " get(java.lang.String)"),
                new BuilderFactoryToBuilderVisitor(JOB_BUILDER_FACTORY, "org.springframework.batch.core.job.builder.JobBuilder"));
    }
}
