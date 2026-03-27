package com.pppopipupu.wish.datagen;

import com.pppopipupu.wish.Wish;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.concurrent.CompletableFuture;

public class WishRecipeProvider extends RecipeProvider {

    public WishRecipeProvider(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> registries) {
        super(packOutput, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput recipeOutput) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Wish.WISH_SCROLL.get())
                .requires(Blocks.DIAMOND_BLOCK, 2)
                .requires(Items.PAPER, 1)
                .unlockedBy("has_diamond_block", has(Blocks.DIAMOND_BLOCK))
                .save(recipeOutput);
    }
}
