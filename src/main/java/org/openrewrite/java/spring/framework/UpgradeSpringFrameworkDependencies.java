package org.openrewrite.java.spring.framework;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.maven.UpgradeDependencyVersion;
import org.openrewrite.semver.Semver;

import java.util.List;

@Value
@EqualsAndHashCode(callSuper = true)
public class UpgradeSpringFrameworkDependencies extends Recipe {
    @Override
    public String getDisplayName() {
        return "Upgrade spring-framework Maven dependencies";
    }

    @Override
    public String getDescription() {
        return "Upgrade spring-framework 5.x Maven dependencies using a Node Semver advanced range selector.";
    }

    @Option(displayName = "New version",
            description = "An exact version number, or node-style semver selector used to select the version number.",
            example = "5.3.X")
    String newVersion;

    @Override
    public Validated validate() {
        Validated validated = super.validate();
        validated = validated.and(Semver.validate(newVersion, null));
        return validated;
    }

    @Override
    protected List<SourceFile> visit(List<SourceFile> before, ExecutionContext ctx) {
        String[] artifacts_5_1 = new String[]{
                "spring-bom",
                "spring-aop",
                "spring-aspects",
                "spring-beans",
                "spring-context",
                "spring-context-indexer",
                "spring-context-support",
                "spring-core",
                "spring-expression",
                "spring-instrument",
                "spring-jcl",
                "spring-jdbc",
                "spring-jms",
                "spring-messaging",
                "spring-orm",
                "spring-oxm",
                "spring-test",
                "spring-tx",
                "spring-web",
                "spring-webflux",
                "spring-webmvc",
                "spring-websocket"};

        for (String artifact : artifacts_5_1) {
            doNext(new UpgradeDependencyVersion("org.springframework", artifact, newVersion, null, true));
        }
        if (newVersion.startsWith("5.3")) {
            doNext(new UpgradeDependencyVersion("org.springframework", "spring-r2dbc", newVersion, null, true));
        }

        return super.visit(before, ctx);
    }
}
