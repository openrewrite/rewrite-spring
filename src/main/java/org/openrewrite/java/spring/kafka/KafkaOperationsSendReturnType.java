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
        return "Send operations used to return a `ListenableFuture` but as of 3.x return a `CompletableFuture`. " +
               "Adjust the usage to use `CompletableFuture` instead.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>("org.springframework.kafka.support.SendResult", true),
                new ListenableToCompletableFuture());
    }
}
