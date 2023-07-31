/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.apache.httpclient5;

import org.junit.jupiter.api.Test;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeApacheHttpClient5Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion().classpath(
            "httpclient", "httpcore",
            "httpclient5", "httpcore5"))
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite")
            .build()
            .activateRecipes("org.openrewrite.java.apache.httpclient5.UpgradeApacheHttpClient_5")
          );
    }

    @Test
    void migrateDependencies() {
        rewriteRun(
          //language=xml
          pomXml("""
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>org.example</groupId>
                <artifactId>example</artifactId>
                <version>1.0.0</version>
                <dependencies>
                    <dependency>
                        <groupId>org.apache.httpcomponents</groupId>
                        <artifactId>httpclient</artifactId>
                        <version>4.5.14</version>
                    </dependency>
                </dependencies>
            </project>
            """, spec -> spec.after(pom -> {
              Matcher version = Pattern.compile("5\\.1\\.\\d+").matcher(pom);
              assertThat(version.find()).describedAs("Expected 5.1.x in %s", pom).isTrue();
              return """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.example</groupId>
                    <artifactId>example</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.httpcomponents.client5</groupId>
                            <artifactId>httpclient5</artifactId>
                            <version>%s</version>
                        </dependency>
                    </dependencies>
                </project>
                """.formatted(version.group(0));
          })));
    }

    @Test
    void importReplacementsInGroupsWithSomeSpecificMappings() {
        rewriteRun(
          //language=java
          java("""
            import org.apache.http.HttpEntity;
            import org.apache.http.client.methods.HttpGet;
            import org.apache.http.client.methods.HttpUriRequest;
            import org.apache.http.util.EntityUtils;
                          
            class A {
                void method(HttpEntity entity, String urlStr) throws Exception {
                    HttpUriRequest getRequest = new HttpGet(urlStr);
                    EntityUtils.consume(entity);
                }
            }
            """, """
            import org.apache.hc.core5.http.io.entity.EntityUtils;
            import org.apache.hc.core5.http.HttpEntity;
            import org.apache.hc.client5.http.classic.methods.HttpGet;
            import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
                          
            class A {
                void method(HttpEntity entity, String urlStr) throws Exception {
                    HttpUriRequest getRequest = new HttpGet(urlStr);
                    EntityUtils.consume(entity);
                }
            }
            """)
        );
    }

    @Test
    void useTimeoutClass() {
        rewriteRun(
          //language=java
          java("""
            import org.apache.http.client.config.RequestConfig;
            import org.apache.http.config.SocketConfig;

            class A {
                void method() {
                    RequestConfig.custom()
                        .setConnectionRequestTimeout(300)
                        .setConnectTimeout(500)
                        .setSocketTimeout(1500);
                        
                    SocketConfig.custom()
                        .setSoTimeout(1000);
                }
            }
            """, """
            import org.apache.hc.client5.http.config.RequestConfig;
            import org.apache.hc.core5.http.io.SocketConfig;
            import org.apache.hc.core5.util.Timeout;

            class A {
                void method() {
                    RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofMilliseconds(300))
                        .setConnectTimeout(Timeout.ofMilliseconds(500))
                        .setResponseTimeout(Timeout.ofMilliseconds(1500));
                        
                    SocketConfig.custom()
                        .setSoTimeout(Timeout.ofMilliseconds(1000));
                }
            }
            """)
        );
    }

    @Test
    void useTimeValueClass() {
        rewriteRun(
          //language=java
          java("""
            import org.apache.http.config.SocketConfig;
            
            class A {
                void method() {
                    SocketConfig.custom()
                        .setSoLinger(500);
                }
            }
            """, """
            import org.apache.hc.core5.http.io.SocketConfig;
            import org.apache.hc.core5.util.TimeValue;
            
            class A {
                void method() {
                    SocketConfig.custom()
                        .setSoLinger(TimeValue.ofMilliseconds(500));
                }
            }
            """)
        );
    }
}
