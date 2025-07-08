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
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.spring.util.concurrent.ListenableToCompletableFuture;

public class KafkaOperationsSendReturnType extends Recipe {
    @Override
    public String getDisplayName() {
        return "Change `KafkaOperations.send*` return type to `CompletableFuture`";
    }

    @Override
    public String getDescription() {
        return "Send operations used to return a `ListenableFuture` but as of 3.0 return a `CompletableFuture`. " +
               "Adjust the usage to use `CompletableFuture` instead.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>("org.springframework.kafka.support.SendResult", true),
                new ListenableToCompletableFuture());
    }
}
