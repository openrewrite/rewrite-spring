/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.java.spring.data.search;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.spring.data.MongoValueRepresentationAccumulator;
import org.openrewrite.java.spring.data.MongoValueRepresentationAccumulator.Occurrence;
import org.openrewrite.java.spring.data.MongoValueRepresentationScanner;
import org.openrewrite.java.spring.data.SpringConfigFileSupport;
import org.openrewrite.java.spring.table.MongoValueRepresentationFields;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

import java.util.List;
import java.util.UUID;

/**
 * Find explicitly MongoDB-mapped UUID and big-number fields for which Spring Data MongoDB 5
 * no longer supplies a default representation. Pure search recipe: marks affected fields with a
 * {@link SearchResult} and records them in a data table; configuration itself is added by
 * {@link org.openrewrite.java.spring.data.AddMongoValueRepresentationProperty}.
 */
public class FindMissingMongoValueRepresentation extends ScanningRecipe<MongoValueRepresentationAccumulator> {

    final transient MongoValueRepresentationFields affectedFields = new MongoValueRepresentationFields(this);

    @Getter
    final String displayName = "Find missing MongoDB value representation configuration";

    @Getter
    final String description = "Find explicitly MongoDB-mapped UUID, BigInteger, and BigDecimal fields that require an " +
            "explicit representation when migrating to Spring Data MongoDB 5. The recipe reports affected fields " +
            "without choosing a storage representation.";

    @Override
    public MongoValueRepresentationAccumulator getInitialValue(ExecutionContext ctx) {
        return new MongoValueRepresentationAccumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(MongoValueRepresentationAccumulator acc) {
        return MongoValueRepresentationScanner.scanner(acc);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(MongoValueRepresentationAccumulator acc) {
        return Preconditions.check(MongoValueRepresentationScanner.javaPrecondition(), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                JavaProject project = SpringConfigFileSupport.javaProject(cu);
                if (project == null) {
                    return cu;
                }
                insertRowsOnce(project, acc, ctx);
                getCursor().putMessage("unresolved", acc.unresolvedOccurrences(project));
                return super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations declarations,
                                                                     ExecutionContext ctx) {
                J.VariableDeclarations visitedDeclarations = super.visitVariableDeclarations(declarations, ctx);
                J.ClassDeclaration owner = getCursor().firstEnclosing(J.ClassDeclaration.class);
                List<Occurrence> unresolved = getCursor().getNearestMessage("unresolved");
                if (owner == null || unresolved == null) {
                    return visitedDeclarations;
                }
                for (J.VariableDeclarations.NamedVariable variable : visitedDeclarations.getVariables()) {
                    if (isUnresolvedOccurrence(unresolved, owner.getId(), variable.getSimpleName())) {
                        return SearchResult.found(visitedDeclarations, "missing MongoDB value representation configuration");
                    }
                }
                return visitedDeclarations;
            }
        });
    }

    private static boolean isUnresolvedOccurrence(List<Occurrence> unresolved, UUID owningClassId, String field) {
        for (Occurrence occurrence : unresolved) {
            if (occurrence.getOwningClassId().equals(owningClassId) && occurrence.getField().equals(field)) {
                return true;
            }
        }
        return false;
    }

    private void insertRowsOnce(JavaProject project, MongoValueRepresentationAccumulator acc, ExecutionContext ctx) {
        if (!acc.markRowsInserted(project)) {
            return;
        }
        for (Occurrence occurrence : acc.unresolvedOccurrences(project)) {
            affectedFields.insertRow(ctx, new MongoValueRepresentationFields.Row(
                    occurrence.getSourcePath().toString(), occurrence.getOwningType(), occurrence.getField(),
                    occurrence.getKind().configurationProperty));
        }
    }
}
