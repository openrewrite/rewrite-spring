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
/**
 * Recipes for migrating to OpenTelemetry from various tracing solutions.
 * <p>
 * This package contains recipes for migrating:
 * <ul>
 *   <li>Spring Cloud Sleuth to Micrometer Tracing with OpenTelemetry backend</li>
 *   <li>Sleuth annotations to OpenTelemetry annotations</li>
 *   <li>OpenTracing to OpenTelemetry</li>
 * </ul>
 *
 * @see <a href="https://opentelemetry.io/docs/zero-code/java/spring-boot-starter/">OpenTelemetry Spring Boot Starter</a>
 * @see <a href="https://micrometer.io/docs/tracing">Micrometer Tracing</a>
 */
@org.jspecify.annotations.NullMarked
@org.openrewrite.internal.lang.NonNullFields
package org.openrewrite.java.spring.opentelemetry;
