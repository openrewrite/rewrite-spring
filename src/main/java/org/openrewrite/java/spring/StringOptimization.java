package org.openrewrite.java.spring;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import org.openrewrite.java.template.RecipeDescriptor;
import org.springframework.util.StringUtils;

@SuppressWarnings("ALL")
public class StringOptimization {

    @RecipeDescriptor(
            name = "Use `!StringUtils.hasLength`",
            description = "Replace usage of deprecated `StringUtils.isEmpty` with `!StringUtils.hasLength` https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/util/StringUtils.html#isEmpty(java.lang.Object).")
    static class ReplaceDeprecatedStringUtilsIsEmpty {


        @BeforeTemplate
        boolean before(String s) {
            return StringUtils.isEmpty(s);
        }

        @AfterTemplate
        boolean after(String s) {
            return !StringUtils.hasLength(s);
        }
    }
}
