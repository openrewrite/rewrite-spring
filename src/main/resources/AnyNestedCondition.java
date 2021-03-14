package org.springframework.context.annotation;
public interface ConfigurationCondition {
    public static enum ConfigurationPhase {PARSE_CONFIGURATION, REGISTER_BEAN;}
}
---
package org.springframework.boot.autoconfigure.condition;

import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;

public abstract class AnyNestedCondition {
    public AnyNestedCondition(ConfigurationPhase configurationPhase) {}
}

