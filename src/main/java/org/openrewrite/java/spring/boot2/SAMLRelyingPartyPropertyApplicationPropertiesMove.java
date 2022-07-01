package org.openrewrite.java.spring.boot2;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.properties.PropertiesVisitor;
import org.openrewrite.properties.tree.Properties;

import java.util.UUID;
import java.util.regex.Pattern;

public class SAMLRelyingPartyPropertyApplicationPropertiesMove extends Recipe {
//    private static Pattern IDENTITY_PROVIDER_PATTERN = Pattern.compile("spring\\.security\\.saml2\\.relyingparty\\.registration\\..*\\.(identityprovider).*");

    @Override
    public String getDisplayName() {
        return "Move SAML relying party identity provider property to asserting party";
    }

    @Override
    public String getDescription() {
        return "Renames spring.security.saml2.relyingparty.registration.(any).identityprovider to " +
                "spring.security.saml2.relyingparty.registration.(any).assertingparty";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {

        return new PropertiesVisitor<ExecutionContext>() {

            @Override
            public Properties visitEntry(Properties.Entry entry, ExecutionContext executionContext) {
                String updatedKey = entry.getKey().replaceAll("identityprovider", "assertingparty");
                Properties.Entry updatedEntry = new Properties.Entry(UUID.randomUUID(), entry.getPrefix(), entry.getMarkers(), updatedKey, entry.getBeforeEquals(), entry.getValue());
                return super.visitEntry(updatedEntry, executionContext);
            }
        };
    }


}
