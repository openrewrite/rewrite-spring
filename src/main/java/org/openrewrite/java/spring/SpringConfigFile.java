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

import lombok.Value;
import lombok.With;
import org.openrewrite.marker.Marker;

import java.util.UUID;

/**
 * Marker indicating that a YAML or properties source file should be treated as Spring
 * configuration even though it does not carry a {@link org.openrewrite.marker.SourceSet}
 * marker (e.g. because it lives outside a standard {@code src/main/resources} layout).
 *
 * <p>Recognized by {@link IsPossibleSpringConfigFile} as an alternative pass condition.
 * Typically attached by {@link MarkAdditionalSpringConfigFiles}.
 */
@Value
@With
public class SpringConfigFile implements Marker {
    UUID id;
}
