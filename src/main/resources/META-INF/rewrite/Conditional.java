package org.springframework.context.annotation;
import java.lang.annotation.*;
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Conditional {
    Class<? extends Condition>[] value();
}