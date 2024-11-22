package org.openrewrite.java.spring;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

public class InlineCommentSpringPropertiesTest implements RewriteTest {

    @Test
    void shouldInsertInlineCommentsIntoProperties() {
        rewriteRun(
          spec -> spec.recipe(new InlineCommentSpringProperties(List.of("test.propertyKey1", "test.propertyKey2"), "my comment")),
          yaml("""
              test.propertyKey1: xxx
              test.propertyKey2: yyy""",
            """
              test.propertyKey1: xxx # my comment
              test.propertyKey2: yyy # my comment""",
            spec -> spec.path("application.yaml")),
          properties("""
              test.propertyKey1: xxx
              test.propertyKey2: yyy""",
            """
              test.propertyKey1: xxx # my comment
              test.propertyKey2: yyy # my comment""",
            spec -> spec.path("application.properties")));
    }
}
