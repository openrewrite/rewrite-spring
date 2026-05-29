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
package org.openrewrite.java.spring.boot3;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.xml.Assertions.xml;

class UseRfc6265CookieProcessorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.java.spring.boot3.UseRfc6265CookieProcessor");
    }

    @DocumentExample
    @Test
    void replaceLegacyCookieProcessorInContextXml() {
        rewriteRun(
          xml(
            """
              <?xml version="1.0" encoding="UTF-8"?>
              <Context>
                  <CookieProcessor className="org.apache.tomcat.util.http.LegacyCookieProcessor"/>
              </Context>
              """,
            """
              <?xml version="1.0" encoding="UTF-8"?>
              <Context>
                  <CookieProcessor className="org.apache.tomcat.util.http.Rfc6265CookieProcessor"/>
              </Context>
              """,
            spec -> spec.path("src/main/webapp/META-INF/context.xml")
          )
        );
    }

    @Test
    void leavesRfc6265CookieProcessorUnchanged() {
        rewriteRun(
          xml(
            """
              <?xml version="1.0" encoding="UTF-8"?>
              <Context>
                  <CookieProcessor className="org.apache.tomcat.util.http.Rfc6265CookieProcessor"/>
              </Context>
              """,
            spec -> spec.path("src/main/webapp/META-INF/context.xml")
          )
        );
    }

    @Test
    void leavesUnrelatedCookieProcessorClassUnchanged() {
        rewriteRun(
          xml(
            """
              <?xml version="1.0" encoding="UTF-8"?>
              <Context>
                  <CookieProcessor className="com.example.CustomCookieProcessor"/>
              </Context>
              """,
            spec -> spec.path("src/main/webapp/META-INF/context.xml")
          )
        );
    }
}
