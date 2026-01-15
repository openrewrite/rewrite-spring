package org.openrewrite.java.spring.mvc;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class MigrateJaxrsToSpringmvcTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
          .scanRuntimeClasspath()
          .build()
          .activateRecipes("org.openrewrite.java.spring.mvc.MigrateJaxRsToSpringMvc"));
    }

    @Test
    public void jaxrsToSpringmvcJavaxTest() {
        rewriteRun(
          java(
            """
                    import java.io.IOException;

                    import javax.ws.rs.core.CacheControl;
                    import javax.ws.rs.core.HttpHeaders;
                    import javax.ws.rs.core.Request;
                    import javax.ws.rs.core.StreamingOutput;

                    public class Test {

                        public StreamingOutput test(Request request, HttpHeaders headers, CacheControl cc) {
                            return output -> {
                                try {
                                    String message = "Request: " + request.getMethod() + "\\n" +
                                            "Headers: " + headers.toString() + "\\n" +
                                            "CacheControl: " + cc.toString();
                                    output.write(message.getBytes());
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            };
                        }
                    }
                    """,
            """
                    import java.io.IOException;

                    import javax.servlet.http.HttpServletRequest;

                    import org.springframework.http.CacheControl;
                    import org.springframework.http.HttpHeaders;
                    import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

                    public class Test {

                        public StreamingResponseBody test(HttpServletRequest request, HttpHeaders headers, CacheControl cc) {
                            return output -> {
                                try {
                                    String message = "Request: " + request.getMethod() + "\\n" +
                                            "Headers: " + headers.toString() + "\\n" +
                                            "CacheControl: " + cc.toString();
                                    output.write(message.getBytes());
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            };
                        }
                    }
                    """
          )
        );
    }

    @Test
    public void jaxrsToSpringmvcJakartaTest() {
        rewriteRun(
          java(
            """
                    import java.io.IOException;

                    import jakarta.ws.rs.core.CacheControl;
                    import jakarta.ws.rs.core.HttpHeaders;
                    import jakarta.ws.rs.core.Request;
                    import jakarta.ws.rs.core.StreamingOutput;

                    public class Test {

                        public StreamingOutput test(Request request, HttpHeaders headers, CacheControl cc) {
                            return output -> {
                                try {
                                    String message = "Request: " + request.getMethod() + "\\n" +
                                            "Headers: " + headers.toString() + "\\n" +
                                            "CacheControl: " + cc.toString();
                                    output.write(message.getBytes());
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            };
                        }
                    }
                    """,
            """
                    import java.io.IOException;

                    import jakarta.servlet.http.HttpServletRequest;
                    import org.springframework.http.CacheControl;
                    import org.springframework.http.HttpHeaders;
                    import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

                    public class Test {

                        public StreamingResponseBody test(HttpServletRequest request, HttpHeaders headers, CacheControl cc) {
                            return output -> {
                                try {
                                    String message = "Request: " + request.getMethod() + "\\n" +
                                            "Headers: " + headers.toString() + "\\n" +
                                            "CacheControl: " + cc.toString();
                                    output.write(message.getBytes());
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            };
                        }
                    }
                    """
          )
        );
    }

}
