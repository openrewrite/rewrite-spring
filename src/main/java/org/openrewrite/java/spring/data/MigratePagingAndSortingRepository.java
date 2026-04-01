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
package org.openrewrite.java.spring.data;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.openrewrite.Tree.randomId;

public class MigratePagingAndSortingRepository extends Recipe {

    private static final String PAGING_AND_SORTING_REPOSITORY = "org.springframework.data.repository.PagingAndSortingRepository";
    private static final String CRUD_REPOSITORY = "org.springframework.data.repository.CrudRepository";
    private static final String LIST_CRUD_REPOSITORY = "org.springframework.data.repository.ListCrudRepository";
    private static final String LIST_PAGING_AND_SORTING_REPOSITORY = "org.springframework.data.repository.ListPagingAndSortingRepository";
    private static final String JPA_REPOSITORY = "org.springframework.data.jpa.repository.JpaRepository";

    @Getter
    final String displayName = "Add `CrudRepository` to interfaces extending `PagingAndSortingRepository`";

    @Getter
    final String description = "In Spring Data 3.0, `PagingAndSortingRepository` no longer extends `CrudRepository`. " +
            "Interfaces that extend only `PagingAndSortingRepository` must also explicitly extend `CrudRepository` " +
            "to retain CRUD methods like `save()`, `findById()`, and `delete()`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(PAGING_AND_SORTING_REPOSITORY, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                if (cd.getKind() != J.ClassDeclaration.Kind.Type.Interface || cd.getImplements() == null) {
                    return cd;
                }

                J.ParameterizedType pagingAndSortingType = null;
                boolean alreadyHasCrud = false;

                for (TypeTree impl : cd.getImplements()) {
                    JavaType type = impl.getType();
                    JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
                    if (fq == null) {
                        continue;
                    }
                    String fqn = fq.getFullyQualifiedName();
                    if (PAGING_AND_SORTING_REPOSITORY.equals(fqn) && impl instanceof J.ParameterizedType) {
                        pagingAndSortingType = (J.ParameterizedType) impl;
                    } else if (CRUD_REPOSITORY.equals(fqn) ||
                               LIST_CRUD_REPOSITORY.equals(fqn) ||
                               LIST_PAGING_AND_SORTING_REPOSITORY.equals(fqn) ||
                               JPA_REPOSITORY.equals(fqn)) {
                        alreadyHasCrud = true;
                    }
                }

                if (pagingAndSortingType != null && !alreadyHasCrud &&
                        pagingAndSortingType.getTypeParameters() != null &&
                        pagingAndSortingType.getTypeParameters().size() == 2) {

                    // Build CrudRepository type with the same type parameters
                    JavaType.FullyQualified crudRepoType = JavaType.ShallowClass.build(CRUD_REPOSITORY)
                            .withTypeParameters(Arrays.asList(
                                    pagingAndSortingType.getTypeParameters().get(0).getType(),
                                    pagingAndSortingType.getTypeParameters().get(1).getType()
                            ));

                    // Clone the PagingAndSortingRepository parameterized type, replacing name and type
                    J.Identifier crudRepoIdent = new J.Identifier(
                            randomId(),
                            Space.EMPTY,
                            Markers.EMPTY,
                            null,
                            "CrudRepository",
                            crudRepoType,
                            null
                    );

                    J.ParameterizedType crudRepoParamType = pagingAndSortingType
                            .withId(randomId())
                            .withPrefix(Space.SINGLE_SPACE)
                            .withClazz(crudRepoIdent)
                            .withType(crudRepoType);

                    List<TypeTree> newImplements = new ArrayList<>(cd.getImplements());
                    newImplements.add(crudRepoParamType);
                    cd = cd.withImplements(newImplements);
                    maybeAddImport(CRUD_REPOSITORY);
                }
                return cd;
            }
        });
    }
}
