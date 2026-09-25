package com.demonica.mixin.fontrenderer;

import com.demonica.compat.Mods;
import com.gtnewhorizon.gtnhlib.util.font.IFontParameters;
import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.mixins.interfaces.FontRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.SimpleModelFontRenderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FontRenderer.class)
public abstract class MixinFontRenderer implements FontRendererAccessor, IFontParameters {
    @Shadow private int[] colorCode;
    @Shadow private float alpha;
    @Shadow private float red;
    @Shadow private float blue;
    @Shadow private float green;
    @Shadow protected int[] charWidth;
    @Shadow private boolean unicodeFlag;
    @Shadow protected float posX;
    @Shadow protected float posY;
    @Shadow @Final protected ResourceLocation locationFontTexture;
    @Shadow private boolean bidiFlag;

    @Shadow protected abstract String bidiReorder(String text);
    @Shadow protected abstract void bindTexture(ResourceLocation location);

    @Unique private BatchingFontRenderer demonica$batcher;
    @Unique private TextureManager demonica$textureManager;
    @Unique private static final boolean demonica$disableBatcher = Boolean.getBoolean("demonica.disableFontBatcher");
    @Unique private static final Logger demonica$LOGGER = LogManager.getLogger("Demonica");
    @Unique private static final boolean demonica$neoFontRenderLoaded = demonica$resolveNeoFontRenderLoaded();

    @Unique
    private static boolean demonica$resolveNeoFontRenderLoaded() {
        final boolean loaded = Mods.NEOFONTRENDER;
        if (Boolean.getBoolean("demonica.fontDebug")) {
            demonica$LOGGER.info("font-batcher-check neofontrender={} renderer={}",
                loaded, FontRenderer.class.getName());
        }
        return loaded;
    }

    @Unique
    private static boolean demonica$isFontBatcherDisabled() {
        return demonica$disableBatcher || demonica$neoFontRenderLoaded;
    }

    /**
     * Whether this renderer draws through the batcher. Forge's {@link SimpleModelFontRenderer} does not draw: it turns
     * text into baked quads (the label of {@code FancyMissingModel}, the model of a block whose model failed to load),
     * without GL and also on chunk mesh worker threads, so it keeps vanilla's path.
     */
    @Unique
    private boolean demonica$batches() {
        return !demonica$isFontBatcherDisabled() && !((Object) this instanceof SimpleModelFontRenderer);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void demonica$injectBatcher(GameSettings settings, ResourceLocation fontLocation, TextureManager texManager,
        boolean unicodeMode, CallbackInfo ci) {
        demonica$textureManager = texManager;
        if (Boolean.getBoolean("demonica.fontDebug")) {
            Logger log = LogManager.getLogger("DemonicaFontDebug");
            log.info("charWidth-probe renderer={} unicode={} space={} A={} a={} m={} M={} W={}",
                getClass().getName(), unicodeMode,
                this.charWidth[32], this.charWidth[65], this.charWidth[97], this.charWidth[109], this.charWidth[77], this.charWidth[87]);
            try (java.io.InputStream in = Minecraft.getMinecraft().getResourceManager()
                    .getResource(this.locationFontTexture).getInputStream()) {
                java.awt.image.BufferedImage img = net.minecraft.client.renderer.texture.TextureUtil.readBufferedImage(in);
                log.info("fontimg-probe renderer={} loc={} img={}x{} px(8,8)={} px(40,40)={}",
                    getClass().getName(), this.locationFontTexture, img.getWidth(), img.getHeight(),
                    Integer.toHexString(img.getRGB(8, 8)), Integer.toHexString(img.getRGB(40, 40)));
            } catch (Exception e) {
                log.info("fontimg-probe renderer={} loc={} FAILED {}", getClass().getName(), this.locationFontTexture, e);
            }
        }
    }

    @Inject(method = "drawString(Ljava/lang/String;FFIZ)I", at = @At("HEAD"), cancellable = true)
    private void demonica$drawStringBatched(String text, float x, float y, int argb, boolean dropShadow,
        CallbackInfoReturnable<Integer> cir) {
        if (this.demonica$batches() && GLStateManager.getListMode() == 0) {
            cir.setReturnValue(angelica$drawStringBatched(text, (int) x, (int) y, argb, dropShadow));
        }
    }

    @Inject(method = "renderString", at = @At("HEAD"), cancellable = true)
    private void demonica$renderStringBatched(String text, float x, float y, int argb, boolean dropShadow,
        CallbackInfoReturnable<Integer> cir) {
        if (this.demonica$batches() && GLStateManager.getListMode() == 0) {
            cir.setReturnValue(angelica$drawStringBatched(text, (int) x, (int) y, argb, dropShadow));
        }
    }

    @Override
    public int angelica$drawStringBatched(String text, int x, int y, int argb, boolean dropShadow) {
        if (text == null) {
            return 0;
        }
        if (this.bidiFlag) {
            text = this.bidiReorder(text);
        }
        if ((argb & 0xfc000000) == 0) {
            argb |= 0xff000000;
        }
        if (argb == 0xFF000000) {
            // Splash font renderers (Modern Splash's SplashFontRenderer) pass color 0 to draw
            // with the fixed-pipeline current color, which they set to their configured font
            // color beforehand. Vanilla 1.12.2 renderString forces black for color 0; honor the
            // intended behavior for splash fonts by sampling the GLSM current color instead.
            // Sampled before the GLStateManager.glColor4f reset below.
            int currentArgb = BatchingFontRenderer.readCurrentGlColorAsArgb();
            boolean splash = angelica$getBatcher().isSplash();
            if (splash) {
                argb = currentArgb;
            }
            if (Boolean.getBoolean("demonica.fontDebug")) {
                demonica$LOGGER.info("font-draw-zero text='{}' splash={} glColor=0x{} argbOut=0x{}",
                    text, splash, Integer.toHexString(currentArgb), Integer.toHexString(argb));
            }
        }

        this.red = (argb >> 16 & 255) / 255.0F;
        this.blue = (argb >> 8 & 255) / 255.0F;
        this.green = (argb & 255) / 255.0F;
        this.alpha = (argb >> 24 & 255) / 255.0F;
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        this.posX = x;
        this.posY = y;
        final float ret = angelica$getBatcher().drawString(x, y, argb, dropShadow, unicodeFlag, text, 0, text.length());
        // Honor the vanilla renderString contract: posX advances to the end of the text.
        // Segmented renderers such as CachedRGBFontRenderer chain super.drawString calls
        // and rely on this to stitch segments together.
        this.posX = ret;
        return (int) ret;
    }

    @Override
    public BatchingFontRenderer angelica$getBatcher() {
        // Third-party renderers (e.g. StellarCore's CachedRGBFontRenderer) may replace
        // Minecraft.fontRenderer without registering a resource reload listener, leaving charWidth
        // permanently zeroed. Backfill only when Demonica's batcher is actually going to be used;
        // NFR owns rendering when it is loaded and must not have its font state touched here.
        if (this.charWidth[65] == 0) {
            try {
                ((FontRenderer) (Object) this).onResourceManagerReload(Minecraft.getMinecraft().getResourceManager());
            } catch (Exception e) {
                demonica$LOGGER.warn("Failed to backfill charWidth for font renderer {}", getClass().getName(), e);
            }
        }
        if (demonica$batcher == null) {
            if (Boolean.getBoolean("demonica.fontDebug")) {
                demonica$LOGGER.info("font-batcher-lazy-create renderer={} textureManager={}",
                    ((Object) this).getClass().getName(), this.demonica$textureManager);
            }
            demonica$batcher = new BatchingFontRenderer(
                (FontRenderer) (Object) this,
                this.charWidth,
                this.colorCode,
                this.locationFontTexture,
                this.demonica$textureManager
            );
        }
        return demonica$batcher;
    }

    @Override
    public void angelica$bindTexture(ResourceLocation location) {
        this.bindTexture(location);
    }

    @Inject(method = "getCharWidth", at = @At("HEAD"), cancellable = true)
    private void demonica$getCharWidth(char c, CallbackInfoReturnable<Integer> cir) {
        if (this.demonica$batches()) {
            cir.setReturnValue((int) angelica$getBatcher().getCharWidthFine(c));
        }
    }

    @Override public float demonica$getGlyphScaleX() { return angelica$getBatcher().getGlyphScaleX(); }
    @Override public float demonica$getGlyphScaleY() { return angelica$getBatcher().getGlyphScaleY(); }
    @Override public float demonica$getGlyphSpacing() { return angelica$getBatcher().getGlyphSpacing(); }
    @Override public float demonica$getWhitespaceScale() { return angelica$getBatcher().getWhitespaceScale(); }
    @Override public float demonica$getShadowOffset() { return angelica$getBatcher().getShadowOffset(); }
    @Override public float demonica$getCharWidthFine(char chr) { return angelica$getBatcher().getCharWidthFine(chr); }
}
