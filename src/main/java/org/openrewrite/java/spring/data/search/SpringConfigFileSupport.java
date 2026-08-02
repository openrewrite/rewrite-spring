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

import org.jspecify.annotations.Nullable;
import org.openrewrite.SourceFile;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.spring.SpringConfigFile;
import org.openrewrite.marker.SourceSet;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.tree.Yaml;

import java.nio.file.Path;

/**
 * Locating and ranking a project's main-source Spring application configuration files.
 * Shared by the scanner and diagnostics phases; independent of what those phases do with the result.
 */
final class SpringConfigFileSupport {

    private SpringConfigFileSupport() {
    }

    static boolean isMainSource(SourceFile source) {
        SourceSet sourceSet = source.getMarkers().findFirst(SourceSet.class).orElse(null);
        if (sourceSet != null) {
            return "main".equals(sourceSet.getName());
        }
        String path = source.getSourcePath().toString().replace('\\', '/');
        return !path.startsWith("src/test/") && !path.contains("/src/test/");
    }

    static @Nullable JavaProject javaProject(SourceFile source) {
        return source.getMarkers().findFirst(JavaProject.class).orElse(null);
    }

    static boolean isMainSpringConfigurationFile(SourceFile source) {
        if (!(source instanceof Properties.File || source instanceof Yaml.Documents) || !isMainSource(source)) {
            return false;
        }
        if (source.getMarkers().findFirst(SpringConfigFile.class).isPresent()) {
            return true;
        }
        String filename = source.getSourcePath().getFileName().toString();
        return isApplicationConfigurationFile(filename);
    }

    private static boolean isApplicationConfigurationFile(String filename) {
        if ("application.properties".equals(filename) || "application.yml".equals(filename) ||
                "application.yaml".equals(filename)) {
            return true;
        }
        return filename.startsWith("application-") &&
                (filename.endsWith(".properties") || filename.endsWith(".yml") || filename.endsWith(".yaml"));
    }

    static Path preferredConfigurationSource(Path left, Path right) {
        int leftPriority = configurationSourcePriority(left);
        int rightPriority = configurationSourcePriority(right);
        if (leftPriority != rightPriority) {
            return leftPriority < rightPriority ? left : right;
        }
        return left.toString().compareTo(right.toString()) <= 0 ? left : right;
    }

    private static int configurationSourcePriority(Path path) {
        String filename = path.getFileName().toString();
        if ("application.properties".equals(filename)) {
            return 0;
        }
        if ("application.yml".equals(filename)) {
            return 1;
        }
        return "application.yaml".equals(filename) ? 2 : 3;
    }
}
