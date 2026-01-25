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
package org.openrewrite.java.spring;

import java.util.Optional;

import lombok.Getter;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

/**
 * Replace method declaration @HttpExchange annotations with the associated variant
 * as defined by the request method type (GET, POST, PUT, PATCH, DELETE)
 * <p>
 * <ul>
 * <li> @HttpExchange(method = "GET") changes to @GetExchange
 * <li> @HttpExchange(method = "POST") changes to @PostExchange
 * <li> @HttpExchange(method = "PATCH") changes to @PatchExchange
 * <li> @HttpExchange(method = "PUT") changes to @PutExchange
 * <li> @HttpExchange(method = "DELETE") changes to @DeleteExchange
 * </ul>
 */

public class NoHttpExchangeAnnotation extends Recipe {

	@Getter
	final String displayName = "Remove `HttpExchange` annotations";

	@Getter
	final String description = "Replace method declaration `@HttpExchange` annotations with `@GetExchange`, `@PostExchange`, etc.";

	@Override
	public TreeVisitor<?, ExecutionContext> getVisitor() {
		return Preconditions.check(
			new UsesType<>("org.springframework.web.service.annotation.HttpExchange", false),
			new NoHttpExchangeAnnotationVisitor());
	}

	private static class NoHttpExchangeAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {
		private static final AnnotationMatcher HTTP_EXCHANGE_ANNOTATION_MATCHER = new AnnotationMatcher("@org.springframework.web.service.annotation.HttpExchange");

		@Override
		public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
			J.Annotation a = super.visitAnnotation(annotation, ctx);
			if (HTTP_EXCHANGE_ANNOTATION_MATCHER.matches(a) && getCursor().getParentOrThrow().getValue() instanceof J.MethodDeclaration) {
				Optional<J.Assignment> methodArg = findMethodArgument(a);
				Optional<String> httpMethod = methodArg.map(this::extractHttpMethod);
				String targetAnnotationClassName = httpMethod.map(this::toExchangeAnnotation).orElse(null);

				if (targetAnnotationClassName == null) {
					return a;
				}

				maybeRemoveImport("org.springframework.web.service.annotation.HttpExchange");

				if (a.getArguments() != null) {
					a = a.withArguments(ListUtils.map(a.getArguments(), arg ->
						methodArg.get().equals(arg) ? null : arg));
				}

				maybeAddImport("org.springframework.web.service.annotation." + targetAnnotationClassName);
				a = (J.Annotation)new ChangeType(
					"org.springframework.web.service.annotation.HttpExchange",
					"org.springframework.web.service.annotation." + targetAnnotationClassName,
					false
				).getVisitor().visit(a, ctx, getCursor().getParentOrThrow());

				if (a != null && a.getArguments() != null && a.getArguments().size() == 1) {
					a = a.withArguments(ListUtils.map(a.getArguments(), arg -> {
						if (arg instanceof J.Assignment && ((J.Assignment)arg).getVariable() instanceof J.Identifier) {
							J.Identifier ident = (J.Identifier)((J.Assignment)arg).getVariable();
							if ("value".equals(ident.getSimpleName()) || "url".equals(ident.getSimpleName())) {
								return ((J.Assignment)arg).getAssignment().withPrefix(Space.EMPTY);
							}
						}
						return arg;
					}));
				}
			}
			return a != null ? a : annotation;
		}

		private Optional<J.Assignment> findMethodArgument(J.Annotation annotation) {
			if (annotation.getArguments() == null) {
				return Optional.empty();
			}

			return annotation.getArguments().stream()
				.filter(arg -> arg instanceof J.Assignment &&
					((J.Assignment)arg).getVariable() instanceof J.Identifier &&
					"method".equals(((J.Identifier)((J.Assignment)arg).getVariable()).getSimpleName()))
				.map(J.Assignment.class::cast)
				.findFirst();
		}

		private @Nullable String extractHttpMethod(J.@Nullable Assignment assignment) {
			if (assignment == null) {
				return null;
			}

			if (assignment.getAssignment() instanceof J.Literal) {
				Object value = ((J.Literal)assignment.getAssignment()).getValue();
				if (value instanceof String) {
					return (String)value;
				}
			}

			return null;
		}

		private @Nullable String toExchangeAnnotation(String method) {
			switch (method) {
				case "GET":
				case "POST":
				case "PUT":
				case "PATCH":
				case "DELETE":
					return method.charAt(0) + method.toLowerCase().substring(1) + "Exchange";
				default:
					return null;
			}
		}
	}
}
