/*
 * Copyright 2025 the original author or authors.
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

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.FindMethods;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.Objects;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

public class MigrateJobStep extends Recipe {

	private static final String JOB_STEP_JOB_LAUNCHER_SETTER = "org.springframework.batch.core.step.job.JobStep " +
			"setJobLauncher(org.springframework.batch.core.launch.JobLauncher)";
	public static final String JOB_OPERATOR_FULLY_QUALIFIED_NAME = "org.springframework.batch.core.launch.JobOperator";
	public static final String JOB_LAUNCHER_FULLY_QUALIFIED_NAME = "org.springframework.batch.core.launch.JobLauncher";

	@Override
	public String getDisplayName() {
		return "Migrate `JobStep` to use `JobOperator` instead of `JobLauncher`";
	}

	@Override
	public String getDescription() {
		return "Since spring-batch 6.0 `JobLauncher` is deprecated in favor of `JobOperator`. This recipe migrates " +
				"`JobStep` to use `JobOperator` instead of `JobLauncher`.";
	}

	@Override
	public TreeVisitor<?, ExecutionContext> getVisitor() {
		return Preconditions.check(new UsesMethod<>(JOB_STEP_JOB_LAUNCHER_SETTER),
				new JavaVisitor<ExecutionContext>() {
					@Override
					public J visit(@Nullable Tree tree, ExecutionContext ctx) {
						return new JobOperatorVisitor().visit(tree, ctx);
					}
				}
		);
	}

	private static class JobOperatorVisitor extends JavaIsoVisitor<ExecutionContext> {

		@Override
		public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
			J.ClassDeclaration cd = super.visitClassDeclaration(classDeclaration, ctx);

			if (!FindMethods.find(classDeclaration, JOB_STEP_JOB_LAUNCHER_SETTER).isEmpty()) {
				cd = cd.withBody(cd.getBody().withStatements(ListUtils.map(cd.getBody().getStatements(), statement -> {
					if (statement instanceof J.VariableDeclarations &&
							((J.VariableDeclarations) statement).getTypeExpression() != null) {
						if (TypeUtils.isOfClassType(((J.VariableDeclarations) statement).getTypeExpression().getType(),
								JOB_LAUNCHER_FULLY_QUALIFIED_NAME)) {
							return null;
						}
					}
					return statement;
				})));
			}

			// Remove field
			cd = cd.withBody(
					cd.getBody().withStatements(
							ListUtils.map(cd.getBody().getStatements(), s -> s)
									.stream()
									.filter(statement -> !isJobLauncherParameter(statement))
									.collect(toList())
					)
			);

			// Remove constructor if it has zero parameters (after removing JobLauncher)
			return cd.withBody(
					cd.getBody().withStatements(
							cd.getBody().getStatements().stream()
									.map(this::cleanConstructor)
									.filter(Objects::nonNull) // remove empty constructors
									.collect(toList())
					)
			);
		}

		@Override
		public J.@Nullable MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext ctx) {
			if (!FindMethods.find(md, JOB_STEP_JOB_LAUNCHER_SETTER).isEmpty()) {
				List<Statement> params = ListUtils.filter(md.getParameters(), j -> !isJobLauncherParameter(j));
				if (params.isEmpty() && md.isConstructor()) {
					return null;
				}
				J.MethodDeclaration finalMd = md;
				params = ListUtils.mapFirst(params, p -> p.withPrefix(finalMd.getParameters().get(0).getPrefix().withComments(emptyList())));

				if (md.getParameters().stream().noneMatch(this::isJobOperatorParameter) && !md.isConstructor()) {
					maybeAddImport(JOB_OPERATOR_FULLY_QUALIFIED_NAME);
					maybeRemoveImport(JOB_LAUNCHER_FULLY_QUALIFIED_NAME);
					maybeRemoveImport("org.springframework.beans.factory.annotation.Autowired");
					boolean parametersEmpty = params.isEmpty() || params.get(0) instanceof J.Empty;
					J.VariableDeclarations vdd = JavaTemplate.builder("JobOperator jobOperator")
							.contextSensitive()
							.imports(JOB_OPERATOR_FULLY_QUALIFIED_NAME)
							.javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "spring-batch-core-6.0.+"))
							.build()
							.<J.MethodDeclaration>apply(getCursor(), md.getCoordinates().replaceParameters())
							.getParameters().get(0).withPrefix(parametersEmpty ? Space.EMPTY : Space.SINGLE_SPACE);
					if (parametersEmpty) {
						md = md.withParameters(singletonList(vdd))
								.withMethodType(md.getMethodType()
										.withParameterTypes(singletonList(vdd.getType())));
					}
					md = md.withParameters(ListUtils.concat(params, vdd))
							.withMethodType(md.getMethodType()
									.withParameterTypes(ListUtils.concat(md.getMethodType().getParameterTypes(), vdd.getType())));
				}
			}
			return super.visitMethodDeclaration(md, ctx);
		}

		@Override
		public J.MethodInvocation visitMethodInvocation(J.MethodInvocation m, ExecutionContext ctx) {
			MethodMatcher methodMatcher = new MethodMatcher(JOB_STEP_JOB_LAUNCHER_SETTER, false);
			if (methodMatcher.matches(m)) {
				JavaType.Method type = m.getMethodType();
				if (type != null) {
					type = type.withName("setJobOperator");
				}
				Expression newArg = renameJobLauncherArgument(m.getArguments().get(0));
				m = m.withName(m.getName().withSimpleName("setJobOperator").withType(type)).withMethodType(type)
						.withArguments(singletonList(newArg));
			}
			return super.visitMethodInvocation(m, ctx);
		}

		private Statement cleanConstructor(Statement stmt) {
			if (!(stmt instanceof J.MethodDeclaration)) {
				return stmt;
			}

			J.MethodDeclaration md = (J.MethodDeclaration) stmt;
			if (!md.isConstructor()) {
				return stmt;
			}

			// Remove JobLauncher parameter
			List<Statement> newParams = md.getParameters().stream()
					.filter(p -> !isJobLauncherParameter(p))
					.collect(toList());

			// Remove the assignment: this.jobLauncher = jobLauncher;
			J.Block newBody = md.getBody().withStatements(
					md.getBody().getStatements().stream()
							.filter(s -> !isJobLauncherAssignment(s))
							.collect(toList())
			);

			J.MethodDeclaration updated = md
					.withParameters(newParams)
					.withBody(newBody);

			// Remove entire constructor if empty
			boolean noParams = updated.getParameters().isEmpty();
			boolean noBody = updated.getBody().getStatements().isEmpty();

			return (noParams && noBody) ? null : updated;
		}

		private static Expression renameJobLauncherArgument(Expression oldArg) {
			return new J.Identifier(
					oldArg.getId(),
					oldArg.getPrefix(),
					oldArg.getMarkers(),
					null,
					"jobOperator",
					JavaType.buildType(JOB_OPERATOR_FULLY_QUALIFIED_NAME),
					new JavaType.Variable(null, 0, "jobOperator", null, null, null)
			);
		}

		private boolean isJobOperatorParameter(Statement statement) {
			return statement instanceof J.VariableDeclarations &&
					TypeUtils.isOfClassType(((J.VariableDeclarations) statement).getType(), JOB_OPERATOR_FULLY_QUALIFIED_NAME);
		}

		private boolean isJobLauncherParameter(Statement statement) {
			return statement instanceof J.VariableDeclarations &&
					TypeUtils.isOfClassType(((J.VariableDeclarations) statement).getType(), JOB_LAUNCHER_FULLY_QUALIFIED_NAME);
		}

		private boolean isJobLauncherAssignment(Statement stmt) {
			if (!(stmt instanceof J.Assignment)) {
				return false;
			}

			J.Assignment assign = (J.Assignment) stmt;
			if (!(assign.getVariable() instanceof J.FieldAccess)) {
				return false;
			}
			J.FieldAccess fa = (J.FieldAccess) assign.getVariable();

			return "jobLauncher".equals(fa.getName().getSimpleName());
		}
	}
}
