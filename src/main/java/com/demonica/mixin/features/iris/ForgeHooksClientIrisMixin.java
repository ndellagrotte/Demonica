package com.demonica.mixin.features.iris;

import com.demonica.config.DemonicaRuntimeOptions;
import com.demonica.render.FastLitItemDisplayListCache;
import com.demonica.render.ItemVertexAlphaOverrides;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.ForgeHooksClient;
import org.taumc.celeritas.impl.render.terrain.sprite.SpriteUtil;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = ForgeHooksClient.class, remap = false)
public abstract class ForgeHooksClientIrisMixin {
    @Unique
    private static final ThreadLocal<List<BakedQuad>> demonica$fastLitItemQuads =
            ThreadLocal.withInitial(ArrayList::new);

    @Inject(
        method = "renderLitItem(Lnet/minecraft/client/renderer/RenderItem;Lnet/minecraft/client/renderer/block/model/IBakedModel;ILnet/minecraft/item/ItemStack;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void demonica$renderSimpleLitItem(RenderItem renderItem, IBakedModel model, int color, ItemStack stack, CallbackInfo ci) {
        if (IrisApi.getInstance().isShaderPackInUse()) {
            return;
        }

        if (!DemonicaRuntimeOptions.useFastLitItemRendering()) {
            return;
        }

        List<BakedQuad> quads = demonica$fastLitItemQuads.get();
        quads.clear();

        for (EnumFacing facing : EnumFacing.VALUES) {
            quads.addAll(model.getQuads(null, facing, 0L));
        }
        quads.addAll(model.getQuads(null, null, 0L));

        if (quads.isEmpty()) {
            ci.cancel();
            return;
        }

        if (!demonica$isSimpleLitItemModel(quads)) {
            quads.clear();
            return;
        }

        // A registered mod is scaling item vertex alpha right now (NeverEnoughAnimation's GUI
        // open/close fade, issue #145). Both shortcuts below assume the vertex colours are a pure
        // function of the baked quad data: the raw append writes the quad bytes unchanged and drops
        // the scaling, and the display list bakes the alpha of the compile frame into a cache that
        // glCallList then replays, which left chest GUI items permanently transparent. Drawing the
        // quads through renderQuads keeps the scaling visible without caching it; falling through to
        // Forge's own renderLitItem instead would hand the frame's lightmap and lighting state to a
        // renderer Demonica does not own, which darkened the GUI.
        boolean vertexAlphaScaled = ItemVertexAlphaOverrides.isActive();

        try {
            Tessellator tessellator = Tessellator.getInstance();
            FastLitItemDisplayListCache.CachedDisplayList cached = !vertexAlphaScaled
                    && DemonicaRuntimeOptions.useFastLitItemDisplayLists()
                    ? FastLitItemDisplayListCache.getOrCompile(renderItem, model, quads, color, stack)
                    : null;
            if (cached != null) {
                cached.render();
            } else if (!vertexAlphaScaled && demonica$canAppendRawItemQuads(quads, color)) {
                BufferBuilder buffer = tessellator.getBuffer();
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.ITEM);
                demonica$appendRawItemQuads(buffer, quads);
                tessellator.draw();
            } else {
                BufferBuilder buffer = tessellator.getBuffer();
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.ITEM);
                renderItem.renderQuads(buffer, quads, color, stack);
                tessellator.draw();
            }
            ci.cancel();
        } finally {
            quads.clear();
        }
    }

    @Unique
    private static boolean demonica$isSimpleLitItemModel(List<BakedQuad> quads) {
        for (BakedQuad quad : quads) {
            if (!quad.shouldApplyDiffuseLighting()) {
                return false;
            }

            if (quad.getFormat() != DefaultVertexFormats.ITEM && quad.getFormat().hasUvOffset(1)) {
                return false;
            }
        }

        return true;
    }

    @Unique
    private static boolean demonica$canAppendRawItemQuads(List<BakedQuad> quads, int color) {
        if (color != -1) {
            return false;
        }

        for (BakedQuad quad : quads) {
            if (!DefaultVertexFormats.ITEM.equals(quad.getFormat()) || quad.hasTintIndex()) {
                return false;
            }
        }

        return true;
    }

    @Unique
    private static void demonica$appendRawItemQuads(BufferBuilder buffer, List<BakedQuad> quads) {
        for (BakedQuad quad : quads) {
            TextureAtlasSprite sprite = quad.getSprite();
            if (sprite != null) {
                SpriteUtil.markSpriteActive(sprite);
            }
            buffer.addVertexData(quad.getVertexData());
        }
    }
}

