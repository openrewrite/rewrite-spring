package org.openrewrite.java.spring.framework;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.internal.RecipeIntrospectionUtils;

import java.util.List;

public class UpgradeSpringFrameworkDependenciesTest {

    @Test
    void constructRecipeWithRecipeIntrospectionUtilsShouldNotFail() {
        Recipe recipe = RecipeIntrospectionUtils.constructRecipe(UpgradeSpringFrameworkDependencies.class);
        List<Recipe> recipeList = recipe.getRecipeList();
        assert recipeList.size() != 0;
    }
}
