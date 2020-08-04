/*
 * Copyright 2020 the original author or authors.
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
package org.openrewrite.spring;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import okhttp3.*;
import org.openrewrite.Validated;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.spring.internal.SpringBootReleases;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.ChangeTagValue;
import org.openrewrite.xml.XmlRefactorVisitor;
import org.openrewrite.xml.tree.Xml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Update the parent section of a POM to a version specified to this visitor.
 */
public class UseSpringBootVersionMaven extends XmlRefactorVisitor {
    private final XPathMatcher parentVersion = new XPathMatcher("/project/parent/version");

    /**
     * 2.3.0.RELEASE, 2.3.+, 2.+
     */
    private String requestedVersion;

    //VisibleForTesting
    @Nullable
    String latestMatchingVersion;

    public UseSpringBootVersionMaven() {
        setCursoringOn();
    }

    @Override
    public Iterable<Tag> getTags() {
        return Tags.of("version.requested", requestedVersion, "version", latestMatchingVersion);
    }

    public void setVersion(String version) {
        this.requestedVersion = version;
    }

    @Override
    public Validated validate() {
        return Validated.required("requestedVersion", requestedVersion);
    }

    @Override
    public Xml visitDocument(Xml.Document document) {
        this.latestMatchingVersion = new SpringBootReleases().latestMatchingVersion(requestedVersion);
        return super.visitDocument(document);
    }

    @Override
    public Xml visitTag(Xml.Tag tag) {
        if (parentVersion.matches(getCursor()) &&
                tag.getSibling("groupId", getCursor())
                        .flatMap(Xml.Tag::getValue)
                        .map(groupId -> groupId.equals("org.springframework.boot"))
                        .orElse(false) &&
                tag.getSibling("artifactId", getCursor())
                        .flatMap(Xml.Tag::getValue)
                        .map(artifactId -> artifactId.equals("spring-boot-starter-parent"))
                        .orElse(false)) {
            if (latestMatchingVersion != null && tag.getValue().map(v -> !v.equals(latestMatchingVersion))
                    .orElse(true)) {
                andThen(new ChangeTagValue.Scoped(tag, latestMatchingVersion));
            }
        }

        return super.visitTag(tag);
    }
}
