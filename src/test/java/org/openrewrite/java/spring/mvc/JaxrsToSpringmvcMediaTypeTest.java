package org.openrewrite.java.spring.mvc;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class JaxrsToSpringmvcMediaTypeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new JaxrsToSpringmvcMediaType())
          .parser(JavaParser.fromJavaVersion()
            .classpath("jakarta.ws.rs-api", "javax.ws.rs-api"));
    }

    @Test
    void migrateMediaTypeJavaxTest() {
        rewriteRun(
          java(
            """
                    import javax.ws.rs.core.MediaType;
                    import javax.ws.rs.Produces;

                    public class TestExample {

                        @Produces(MediaType.APPLICATION_JSON)
                        public void updateUser() {}

                        public String getJsonType() {
                            return MediaType.APPLICATION_JSON;
                        }

                        public MediaType getFormType() {
                            return MediaType.APPLICATION_FORM_URLENCODED_TYPE;
                        }
                    }
                    """,
            """
                    import org.springframework.http.MediaType;

                    import javax.ws.rs.Produces;

                    public class TestExample {

                        @Produces(MediaType.APPLICATION_JSON_VALUE)
                        public void updateUser() {}

                        public String getJsonType() {
                            return MediaType.APPLICATION_JSON_VALUE;
                        }

                        public MediaType getFormType() {
                            return MediaType.APPLICATION_FORM_URLENCODED;
                        }
                    }
                    """
          )
        );
    }

    @Test
    void migrateMediaTypeJakartaTest() {
        rewriteRun(
          java(
            """
                    import jakarta.ws.rs.core.MediaType;
                    import jakarta.ws.rs.Produces;

                    public class TestExample {

                        @Produces(MediaType.APPLICATION_JSON)
                        public void updateUser() {}

                        public String getJsonType() {
                            return MediaType.APPLICATION_JSON;
                        }

                        public MediaType getFormType() {
                            return MediaType.APPLICATION_FORM_URLENCODED_TYPE;
                        }
                    }
                    """,
            """
                    import jakarta.ws.rs.Produces;
                    import org.springframework.http.MediaType;

                    public class TestExample {

                        @Produces(MediaType.APPLICATION_JSON_VALUE)
                        public void updateUser() {}

                        public String getJsonType() {
                            return MediaType.APPLICATION_JSON_VALUE;
                        }

                        public MediaType getFormType() {
                            return MediaType.APPLICATION_FORM_URLENCODED;
                        }
                    }
                    """
          )
        );
    }
}
