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
package org.openrewrite.java.spring.boot4;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateOriginLookupMetadataTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateOriginLookupMetadata())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-boot-3.5", "spring-core-6"));
    }

    @Test
    void interfaceForwardingMetadataAndOrigin() {
        rewriteRun(
          java(
            """
              import org.springframework.boot.origin.Origin;
              import org.springframework.boot.origin.OriginLookup;
              import org.springframework.core.env.PropertySource;

              interface EncryptablePropertySource<T> extends OriginLookup<String> {
                  PropertySource<T> getDelegate();

                  @SuppressWarnings("unchecked")
                  @Override
                  default Origin getOrigin(String key) {
                      if (getDelegate() instanceof OriginLookup) {
                          return ((OriginLookup<String>) getDelegate()).getOrigin(key);
                      }
                      return null;
                  }

                  @Override
                  default boolean isImmutable() {
                      if (getDelegate() instanceof OriginLookup) {
                          return ((OriginLookup<?>) getDelegate()).isImmutable();
                      }
                      return OriginLookup.super.isImmutable();
                  }

                  @Override
                  default String getPrefix() {
                      if (getDelegate() instanceof OriginLookup) {
                          return ((OriginLookup<?>) getDelegate()).getPrefix();
                      }
                      return OriginLookup.super.getPrefix();
                  }
              }
              """,
            """
              import org.springframework.boot.env.PropertySourceInfo;
              import org.springframework.boot.origin.Origin;
              import org.springframework.boot.origin.OriginLookup;
              import org.springframework.core.env.PropertySource;

              interface EncryptablePropertySource<T> extends OriginLookup<String>, PropertySourceInfo {
                  PropertySource<T> getDelegate();

                  @SuppressWarnings("unchecked")
                  @Override
                  default Origin getOrigin(String key) {
                      if (getDelegate() instanceof OriginLookup) {
                          return ((OriginLookup<String>) getDelegate()).getOrigin(key);
                      }
                      return null;
                  }

                  @Override
                  default boolean isImmutable() {
                      if (getDelegate() instanceof PropertySourceInfo) {
                          return ((PropertySourceInfo) getDelegate()).isImmutable();
                      }
                      return PropertySourceInfo.super.isImmutable();
                  }

                  @Override
                  default String getPrefix() {
                      if (getDelegate() instanceof PropertySourceInfo) {
                          return ((PropertySourceInfo) getDelegate()).getPrefix();
                      }
                      return PropertySourceInfo.super.getPrefix();
                  }
              }
              """
          )
        );
    }

    @Test
    void classOverridingMetadata() {
        rewriteRun(
          java(
            """
              import org.springframework.boot.origin.Origin;
              import org.springframework.boot.origin.OriginLookup;

              class CustomSource implements OriginLookup<String> {
                  public Origin getOrigin(String key) { return null; }
                  @Override
                  public boolean isImmutable() { return OriginLookup.super.isImmutable(); }
                  @Override
                  public String getPrefix() { return "custom."; }
              }
              """,
            """
              import org.springframework.boot.env.PropertySourceInfo;
              import org.springframework.boot.origin.Origin;
              import org.springframework.boot.origin.OriginLookup;

              class CustomSource implements OriginLookup<String>, PropertySourceInfo {
                  public Origin getOrigin(String key) { return null; }
                  @Override
                  public boolean isImmutable() { return PropertySourceInfo.super.isImmutable(); }
                  @Override
                  public String getPrefix() { return "custom."; }
              }
              """
          )
        );
    }

    @Test
    void reachableFromBoot4Composite() {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0"),
          java(
            """
              import org.springframework.boot.origin.OriginLookup;
              interface CustomSource extends OriginLookup<String> {
                  @Override
                  default String getPrefix() { return "encrypted."; }
              }
              """,
            """
              import org.springframework.boot.env.PropertySourceInfo;
              import org.springframework.boot.origin.OriginLookup;

              interface CustomSource extends OriginLookup<String>, PropertySourceInfo {
                  @Override
                  default String getPrefix() { return "encrypted."; }
              }
              """
          )
        );
    }

    @Nested
    class NoChange {
        @Test
        void alreadyMigrated() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "spring-boot-4")),
              java(
                """
                  import org.springframework.boot.env.PropertySourceInfo;
                  import org.springframework.boot.origin.OriginLookup;
                  interface CustomSource extends OriginLookup<String>, PropertySourceInfo {
                      @Override
                      default boolean isImmutable() { return true; }
                  }
                  """
              )
            );
        }

        @Test
        void mixedOriginAndMetadataUse() {
            rewriteRun(java(
              """
                import org.springframework.boot.origin.OriginLookup;
                interface CustomSource extends OriginLookup<String> {
                    Object getDelegate();
                    @Override
                    default String getPrefix() {
                        if (getDelegate() instanceof OriginLookup) {
                            OriginLookup<?> source = (OriginLookup<?>) getDelegate();
                            return OriginLookup.getOrigin(source, "key") == null ? source.getPrefix() : "origin.";
                        }
                        return OriginLookup.super.getPrefix();
                    }
                }
                """
            ));
        }

        @Test
        void originOnly() {
            rewriteRun(java(
              """
                import org.springframework.boot.origin.Origin;
                import org.springframework.boot.origin.OriginLookup;
                class OriginOnly implements OriginLookup<String> {
                    public Origin getOrigin(String key) { return null; }
                }
                """
            ));
        }

        @Test
        void standaloneCaller() {
            rewriteRun(java(
              """
                import org.springframework.boot.origin.OriginLookup;
                class Caller {
                    boolean immutable(OriginLookup<?> source) { return source.isImmutable(); }
                }
                """
            ));
        }

        @Test
        void unsupportedNestedMetadataCall() {
            rewriteRun(java(
              """
                import org.springframework.boot.origin.OriginLookup;
                interface CustomSource extends OriginLookup<String> {
                    OriginLookup<?> getSource();
                    Object getDelegate(boolean immutable);
                    @Override
                    default boolean isImmutable() {
                        if (getDelegate(getSource().isImmutable()) instanceof OriginLookup) {
                            return ((OriginLookup<?>) getDelegate(getSource().isImmutable())).isImmutable();
                        }
                        return OriginLookup.super.isImmutable();
                    }
                }
                """
            ));
        }

        @Test
        void unsupportedDirectForwarding() {
            rewriteRun(java(
              """
                import org.springframework.boot.origin.OriginLookup;
                interface CustomSource extends OriginLookup<String> {
                    OriginLookup<?> getDelegate();
                    @Override
                    default boolean isImmutable() { return getDelegate().isImmutable(); }
                }
                """
            ));
        }

        @Test
        void unsupportedVariableForwarding() {
            rewriteRun(java(
              """
                import org.springframework.boot.origin.OriginLookup;
                interface CustomSource extends OriginLookup<String> {
                    OriginLookup<?> getDelegate();
                    @Override
                    default boolean isImmutable() {
                        OriginLookup<?> source = getDelegate();
                        return source.isImmutable();
                    }
                }
                """
            ));
        }
    }
}
