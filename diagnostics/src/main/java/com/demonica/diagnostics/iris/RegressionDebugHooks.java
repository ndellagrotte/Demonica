package com.demonica.diagnostics.iris;

import net.coderbot.iris.debug.ShaderRegressionDebug;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

/** Connects the {@link ShaderRegressionDebug} facade to {@link RegressionDiagnostics}. */
public final class RegressionDebugHooks implements ShaderRegressionDebug.Hooks {
    public static final RegressionDebugHooks INSTANCE = new RegressionDebugHooks();

    private RegressionDebugHooks() {
    }

    @Override
    public boolean isEnabled() {
        return RegressionDiagnostics.isEnabled();
    }

    @Override
    public void logEntityColor(String stage, EntityLivingBase entity, float r, float g, float b, float a) {
        RegressionDiagnostics.logEntityColor(stage, entity, r, g, b, a);
    }

    @Override
    public void logEntityPhase(String stage, Entity entity, Render<?> renderer, String previousPhase,
                               boolean beganEntityPhase) {
        RegressionDiagnostics.logEntityPhase(stage, entity, renderer, previousPhase, beganEntityPhase);
    }

    @Override
    public void logItemState(String stage, ItemStack stack) {
        RegressionDiagnostics.logItemState(stage, stack);
    }

    @Override
    public void logBlockMetaMap(String label, Block block, int... metadataKeys) {
        RegressionDiagnostics.logBlockMetaMap(label, block, metadataKeys);
    }
}
