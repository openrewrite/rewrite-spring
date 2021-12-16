/*
 * Copyright 2021 VMware, Inc.
 * SPDX-License-Identifier: Apache License 2.0
 *
 * @author: fkrueger
 */
package org.openrewrite.java.spring.boot2.upgrade.to25;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Incubating;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.marker.SearchResult;

import java.util.*;
import java.util.stream.Collectors;

@Incubating(since = "4.15.0")
public class FindClassesDependingOnDataSource extends Recipe {

    private static final String JAVAX_DATA_SOURCE = "javax.sql.DataSource";
    public static final String CLASSES_USING_DATA_SOURCE = "CLASSES_USING_DATA_SOURCE";

    public static boolean isMatch(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
        return executionContext.getMessage(CLASSES_USING_DATA_SOURCE) != null &&
                Matches.class.isAssignableFrom(executionContext.getMessage(CLASSES_USING_DATA_SOURCE).getClass()) &&
                ((Matches) executionContext.getMessage(CLASSES_USING_DATA_SOURCE) ).contains(classDecl.getType().getFullyQualifiedName());
    }

    public static List<String> getMatches(ExecutionContext executionContext) {
        if(executionContext.getMessage(CLASSES_USING_DATA_SOURCE) == null) {
            executionContext.putMessage(CLASSES_USING_DATA_SOURCE, new Matches());
        }
        return ((Matches)executionContext.getMessage(CLASSES_USING_DATA_SOURCE)).getAll();
    }

    @Override
    public String getDisplayName() {
        return "Searches for classes depending on javax.sql.DataSource.";
    }

    public static class Matches {

        private List<String> classDeclartions = new ArrayList<>();

        public void add(List<String> classDeclarations) {
            this.classDeclartions.addAll(classDeclarations);
        }

        public boolean contains(String classDecl) {
            return classDeclartions.contains(classDecl);
        }

        public List<String> getAll() {
            return Collections.unmodifiableList(classDeclartions);
        }
    }

    @Override
    protected JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext executionContext) {
                J.CompilationUnit cu = super.visitCompilationUnit(compilationUnit, executionContext);

                if (executionContext.getMessage(CLASSES_USING_DATA_SOURCE) == null) {
                    Matches classDeclsDependingOnDataSource = new Matches();
                    executionContext.putMessage(CLASSES_USING_DATA_SOURCE, classDeclsDependingOnDataSource);
                }

                if(isNotProcessed(cu, executionContext) && hasImportsOfTypeDataSource(cu)) {
                    List<J.ClassDeclaration> matchingClassDecls = findClassDeclarationsDependingOnDataSource(cu, executionContext);
                    if( ! matchingClassDecls.isEmpty()) {
                        storeClassDeclInExecutionContext(matchingClassDecls, executionContext);
                    }
                    return cu;
                }

                return cu;
            }

            private boolean isNotProcessed(J.CompilationUnit cu, ExecutionContext executionContext) {
                return cu.getClasses().stream().noneMatch(c -> FindClassesDependingOnDataSource.isMatch(c, executionContext));
            }

            private void storeClassDeclInExecutionContext(List<J.ClassDeclaration> classDeclarations, ExecutionContext executionContext) {
                Matches messages = executionContext.getMessage(CLASSES_USING_DATA_SOURCE);
                messages.add(classDeclarations.stream().map(cd -> cd.getType().getFullyQualifiedName()).collect(Collectors.toList()));
            }


            private List<J.ClassDeclaration> findClassDeclarationsDependingOnDataSource(J.CompilationUnit cu, ExecutionContext executionContext) {
                return cu.getClasses().stream()
                        .filter(cd -> typesDependingOnDataSource(cd, executionContext, cu))
                        .collect(Collectors.toList());
            }

            private boolean typesDependingOnDataSource(J.ClassDeclaration classDeclaration, ExecutionContext executionContext, J.CompilationUnit cu) {
                // FIXME: does not recognize dependency on DataSource
                J.ClassDeclaration classDeclaration1 = (J.ClassDeclaration) new UsesType<>(JAVAX_DATA_SOURCE).visitClassDeclaration(classDeclaration, executionContext);
                J result = new UsesType<>(JAVAX_DATA_SOURCE).visit(cu, executionContext);
                return result.getMarkers().findFirst(SearchResult.class).isPresent();
            }

            private boolean hasImportsOfTypeDataSource(J.CompilationUnit cu) {
                return cu.getTypesInUse().getTypesInUse().stream()
                        .anyMatch(type -> JavaType.FullyQualified.class.isAssignableFrom(type.getClass()) && ((JavaType.FullyQualified)type).isAssignableTo(JAVAX_DATA_SOURCE));
            }
        };
    }

}
