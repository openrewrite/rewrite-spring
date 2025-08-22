package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.yaml.Assertions.yaml;

class SpringBootProperties34Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .expectedCyclesThatMakeChanges(1)
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.spring")
            .build()
            .activateRecipes("org.openrewrite.java.spring.boot3.SpringBootProperties_3_4_Test")
          );
    }

    @Test
    void enabledTrueToAccessReadOnly() {
        rewriteRun(
          //language=properties
          properties(
            """
              management.endpoint.auditevents.enabled=true
              """,
            """
              management.endpoint.auditevents.access=read-only
              """,
            s -> s.path("src/main/resources/application.properties")
          )
        );
    }

    @Test
    void enableVirtualThreadsYaml() {
        rewriteRun(
          //language=yaml
          yaml("""
              management:
                endpoint:
                  auditevents:
                    enabled: true
              """,
            """
              management:
                endpoint:
                  auditevents:
                    access: read-only
              """,
            s -> s.path("src/main/resources/application.yml")
          )
        );
    }
}
