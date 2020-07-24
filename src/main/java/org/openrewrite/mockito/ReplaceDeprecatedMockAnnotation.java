package org.openrewrite.mockito;

import org.openrewrite.AutoConfigure;
import org.openrewrite.java.ChangeType;

/**
 * Replace Mockito 1's org.mockito.MockitoAnnotations.Mock with org.mockito.Mock
 */
@AutoConfigure
public class ReplaceDeprecatedMockAnnotation extends ChangeType {
    public ReplaceDeprecatedMockAnnotation() {
        setType("org.mockito.MockitoAnnotations.Mock");
        setTargetType("org.mockito.Mock");
    }
}
