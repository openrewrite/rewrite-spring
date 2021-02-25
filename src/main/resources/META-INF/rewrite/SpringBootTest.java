package org.springframework.boot.test.context;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface SpringBootTest {
    @AliasFor("properties")
    String[] value() default {};

    @AliasFor("value")
    String[] properties() default {};

    String[] args() default {};

    Class<?>[] classes() default {};

    SpringBootTest.WebEnvironment webEnvironment() default SpringBootTest.WebEnvironment.MOCK;

    public static enum WebEnvironment {
        MOCK(false),
        RANDOM_PORT(true),
        DEFINED_PORT(true),
        NONE(false);

        private final boolean embedded;

        private WebEnvironment(boolean embedded) {
            this.embedded = embedded;
        }

        public boolean isEmbedded() {
            return this.embedded;
        }
    }
}
---
package org.springframework.core.annotation;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Documented
public @interface AliasFor {
    @AliasFor("attribute")
    String value() default "";

    @AliasFor("value")
    String attribute() default "";

    Class<? extends Annotation> annotation() default Annotation.class;
}
