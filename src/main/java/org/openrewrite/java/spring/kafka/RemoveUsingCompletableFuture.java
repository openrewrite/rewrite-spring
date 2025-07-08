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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.RemoveMethodInvocationsVisitor;

import java.util.Collections;

public class RemoveUsingCompletableFuture extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove `KafkaOperations.usingCompletableFuture()`";
    }

    @Override
    public String getDescription() {
        return "Remove the `KafkaOperations.usingCompletableFuture()` bridge during Spring Kafka 2.9 to 3.0 migration.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new RemoveMethodInvocationsVisitor(Collections.singletonList("org.springframework.kafka.core.KafkaOperations usingCompletableFuture()"));
    }
}
