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
package org.openrewrite.java.spring.boot3;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.spring.AddSpringProperty;
import org.openrewrite.java.tree.J;

public class UseReactorContextPropagation extends Recipe {
	@Override
	public String getDisplayName() {
		return "Use `spring.reactor.context-propagation` property";
	}

	@Override
	public String getDescription() {
		return "Replace `Hooks.enableAutomaticContextPropagation()` with `spring.reactor.context-propagation=true`.";
	}

	private static final String SPRING_BOOT_APPLICATION = "org.springframework.boot.autoconfigure.SpringBootApplication";
	private static final MethodMatcher MATCHER = new MethodMatcher("reactor.hooks.Hooks enableAutomaticContextPropagation()");

	@Override
	public TreeVisitor<?, ExecutionContext> getVisitor() {
		return Preconditions.check(
			new UsesType<>(SPRING_BOOT_APPLICATION, true),
			new JavaIsoVisitor<ExecutionContext>(){
				@Override
				public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
					J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
					if (MATCHER.matches(mi)) {
						doAfterVisit(new AddSpringProperty("spring.reactor.context-propagation", "true", null, null).getVisitor());
						return null;
					}
					return mi;
				}
			}
		);
	}
}
