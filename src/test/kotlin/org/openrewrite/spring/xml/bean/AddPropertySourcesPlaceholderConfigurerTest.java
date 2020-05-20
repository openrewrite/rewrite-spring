package org.openrewrite.spring.xml.bean;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AddPropertySourcesPlaceholderConfigurerTest {
    @Test
    void propertyPattern() {
        String value = "${projectConfigPrefix:file:///}${projectConfigDir}/environment.properties";
        assertThat(AddPropertySourcesPlaceholderConfigurer.PROPERTY_PATTERN.matcher(value)
                .results()
                .map(res -> res.group(1)))
            .containsExactlyInAnyOrder("projectConfigPrefix", "projectConfigDir");
    }
}
