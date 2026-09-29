package com.demonica;

import com.demonica.celeritas.guard.InjectionAuditDevCheck;
import com.demonica.celeritas.guard.QuarantineGuard;
import com.demonica.celeritas.terrain.CeleritasWorldRendererCompat;
import com.demonica.compat.kirino.KirinoCompat;
import com.demonica.config.DemonicaOptions;
import com.demonica.config.DemonicaRuntimeOptions;
import com.demonica.debug.DemonicaDiagnostics;
import com.demonica.gui.DemonicaOptionPages;
import com.demonica.loading.ActiniumConflictException;
import com.demonica.loading.Environment;
import com.demonica.mixin.core.terrain.AccessorEntityRenderer;
import com.demonica.mixins.MixinEarly;
import com.demonica.runtime.DemonicaRuntime;
import com.gtnewhorizon.gtnhlib.client.renderer.RuntimeOptionsBridge;
import com.gtnewhorizon.gtnhlib.client.renderer.postprocessing.PostProcessingBridge;
import com.demonica.compat.Mods;
import com.gtnewhorizons.angelica.glsm.debug.GLSMPerfDebugHooks;
import com.gtnewhorizons.angelica.iris.IrisGLSMBridge;
import com.mojang.realmsclient.gui.ChatFormatting;
import net.coderbot.iris.Iris;
import net.coderbot.iris.celeritas.WorldRendererCompatBridge;
import net.coderbot.iris.debug.Diagnostics;
import net.coderbot.iris.compat.dh.DHCompat;
import net.coderbot.iris.pipeline.AdaptiveShadowBoundsStats;
import net.coderbot.iris.rendertarget.IRenderTargetExt;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;

@Mod(
    modid = Demonica.MODID,
    useMetadata = true,
    clientSideOnly = true,
    acceptableRemoteVersions = "*",
    dependencies = "required-after:celeritas",
    guiFactory = "com.demonica.gui.DemonicaGuiFactory"
)
public class Demonica {
    public static final String MODID = DemonicaRuntime.MODID;

    @EventHandler
    public void onConstruct(FMLConstructionEvent event) {
        if (Environment.isActiniumPresent()) {
            throw new ActiniumConflictException();
        }
        if (!MixinEarly.ACTIVE) {
            return;
        }

        ModContainer container = Loader.instance().getIndexedModList().get(MODID);
        DemonicaRuntime.setVersion(container != null ? container.getVersion() : "unknown");
        RuntimeOptionsBridge.setAllowDirectMemoryAccess(DemonicaRuntimeOptions::allowDirectMemoryAccess);
        PostProcessingBridge.setDepthTextureProvider(framebuffer -> ((IRenderTargetExt) framebuffer).iris$getDepthTextureId());
        PostProcessingBridge.setLightmapColorAccessor(renderer -> ((AccessorEntityRenderer) renderer).getLightmapColors());
        PostProcessingBridge.setLightmapTextureAccessor(renderer -> ((AccessorEntityRenderer) renderer).getLightmapTexture());
        PostProcessingBridge.setNightVisionBrightnessInvoker(
            (entity, partialTicks) -> ((AccessorEntityRenderer) Minecraft.getMinecraft().entityRenderer)
                .invokeGetNightVisionBrightness(entity, partialTicks));
        // Iris's shadow pass draws Celeritas's terrain through this adapter (S7).
        WorldRendererCompatBridge.setProvider(CeleritasWorldRendererCompat::current);
        GLSMPerfDebugHooks.addStatsProvider(AdaptiveShadowBoundsStats::dumpStatsAndReset);
        GLSMPerfDebugHooks.setConfiguredEnabled(
            DemonicaRuntimeOptions.resolvePerfDebugEnabled(DemonicaRuntime.options().debug.enablePerfDebug)
        );
        GLSMPerfDebugHooks.setEnabledChangeListener(Demonica::reloadShaderPipelineForPerfDebug);

        // Demonica's settings in Celeritas's video settings pages.
        DemonicaOptionPages.register();

        DemonicaDiagnostics.logConstruction();
        if (Iris.enabled && Mods.DISTANTHORIZONS) {
            // Distant Horizons binds its own Iris integration; Demonica only installs the shader-side LOD programs.
            DHCompat.run();
        }
        MinecraftForge.EVENT_BUS.register(this);
    }

    @EventHandler
    public void onInit(FMLInitializationEvent event) {
        if (!MixinEarly.ACTIVE) {
            return;
        }
        if (QuarantineGuard.isDevelopment()) {
            // A quarantine injector that found no target stops a dev client (docs/celeritas/PIN.md, "Moving the pin").
            InjectionAuditDevCheck.register();
        }
        KirinoCompat.install();
        if (Iris.enabled) {
            IrisGLSMBridge.register();
            Iris.INSTANCE.fmlInitEvent();
            MinecraftForge.EVENT_BUS.register(Iris.INSTANCE);
        }
        String devScript = System.getProperty("demonica.dev.script");
        if (devScript != null && !devScript.isBlank() && !Diagnostics.present()) {
            LogManager.getLogger("Demonica").warn("demonica.dev.script is set, but the dev harness is part of the "
                + "diagnostics jar, which is not installed: add Demonica-diagnostics-{}.jar", DemonicaRuntime.version());
        }
        DemonicaDiagnostics.logInitialization(DemonicaRuntime.version());
    }

    /** Demonica's settings. The diagnostics jar's GLSM draw logs read this reflectively (GlsmDrawLog). */
    public static DemonicaOptions options() {
        return DemonicaRuntime.options();
    }

    private static void reloadShaderPipelineForPerfDebug() {
        if (!Iris.enabled || Minecraft.getMinecraft().world == null) {
            return;
        }
        try {
            Iris.reload();
        } catch (IOException | RuntimeException exception) {
            Iris.logger.error("Failed to reload shader pipeline after changing Demonica perf debug", exception);
        }
    }

    @SubscribeEvent
    public void onF3Text(RenderGameOverlayEvent.Text event) {
        if (!Minecraft.getMinecraft().gameSettings.showDebugInfo) {
            return;
        }
        // Celeritas adds its renderer's own lines; Demonica only names itself.
        event.getRight().add(String.format("%sDemonica (%s)", ChatFormatting.LIGHT_PURPLE, DemonicaRuntime.version()));
        String kirinoStatus = KirinoCompat.debugStatus();
        if (kirinoStatus != null && !Minecraft.getMinecraft().isReducedDebug()) {
            event.getRight().add(kirinoStatus);
        }
    }
}
