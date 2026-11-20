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
package org.openrewrite.java.spring.kafka;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.RemoveMethodInvocationsVisitor;
import org.openrewrite.java.search.UsesMethod;

import static java.util.Collections.singletonList;

public class RemoveUsingCompletableFuture extends Recipe {

    private static final String USING_COMPLETABLE_FUTURE = "org.springframework.kafka.core.KafkaOperations usingCompletableFuture()";

    @Getter
    final String displayName = "Remove `KafkaOperations.usingCompletableFuture()`";

    @Getter
    final String description = "Remove the `KafkaOperations.usingCompletableFuture()` bridge during Spring Kafka 2.9 to 3.0 migration.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(USING_COMPLETABLE_FUTURE),
                new RemoveMethodInvocationsVisitor(singletonList(USING_COMPLETABLE_FUTURE)));
    }
}
