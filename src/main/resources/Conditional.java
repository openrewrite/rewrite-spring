package org.springframework.context.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Conditional {
    Class<? extends Condition>[] value();
}