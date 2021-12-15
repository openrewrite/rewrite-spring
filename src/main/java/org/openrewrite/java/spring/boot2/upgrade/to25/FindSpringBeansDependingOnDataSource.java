/*
 * Copyright 2021 VMware, Inc.
 * SPDX-License-Identifier: Apache License 2.0
 *
 * @author: fkrueger
 */
package org.openrewrite.java.spring.boot2.upgrade.to25;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Incubating;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.TypeMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;

import java.util.*;
import java.util.stream.Collectors;

@Incubating(since = "4.15.0")
public class FindSpringBeansDependingOnDataSource extends Recipe {

    private static final String JAVAX_DATA_SOURCE = "javax.sql.DataSource";
    public static final String MARKER_DESCRIPTION = "depends on javax.sql.DataSource";
    public static final String CLASSES_USING_DATA_SOURCE = "CLASSES_USING_DATA_SOURCE";

    @Override
    public String getDisplayName() {
        return "Searches for classes depending on javax.sql.DataSource.";
    }

    @Override
    protected JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDeclaration, ExecutionContext executionContext) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDeclaration, executionContext);

                Optional<SearchResult> searchResult = cd.getMarkers().findFirst(SearchResult.class);

                if (!searchResult.isPresent()) {

                    J.CompilationUnit compilationUnit = getCursor().dropParentUntil(J.CompilationUnit.class::isInstance).getValue();
                    List<JavaType> dataSourceUsages = compilationUnit.getTypesInUse().getTypesInUse().stream().filter(fqn -> isDataSource(fqn)).collect(Collectors.toList());
                    if (! dataSourceUsages.isEmpty()) {
                        cd = markType(cd, executionContext);
                        return cd;
                    }
                }

                return cd;
            }

            private J.ClassDeclaration markType(J.ClassDeclaration cd, ExecutionContext executionContext) {
                Markers markers = cd.getMarkers().addIfAbsent(new SearchResult(UUID.randomUUID(), MARKER_DESCRIPTION));
                if (executionContext.getMessage(CLASSES_USING_DATA_SOURCE) == null) {
                    Set<String> types = new HashSet<>();
                    executionContext.putMessage(CLASSES_USING_DATA_SOURCE, types);
                }
                Set messages = executionContext.getMessage(CLASSES_USING_DATA_SOURCE);
                messages.add(cd);
                return cd.withMarkers(markers);
            }

            private boolean isDataSource(JavaType fqn) {
                return new TypeMatcher(JAVAX_DATA_SOURCE, true).matches(fqn);
            }

        };
    }

}
