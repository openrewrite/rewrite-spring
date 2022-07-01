package org.openrewrite.java.spring.boot2;

import org.openrewrite.Recipe;

public class SAMLRelyingPartyPropertyApplicationPropertiesMove extends Recipe {
    @Override
    public String getDisplayName() {
        return "Move SAML relying party identity provider property to asserting party";
    }

    @Override
    public String getDescription() {
        return "Renames spring.security.saml2.relyingparty.registration.(any).identityprovider to " +
                "spring.security.saml2.relyingparty.registration.(any).assertingparty";
    }

}
