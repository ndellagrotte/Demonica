package com.demonica.diagnostics;

import com.demonica.diagnostics.dev.DevHarness;
import com.demonica.diagnostics.probe.ShadowCallbackProbe;
import net.coderbot.iris.debug.Diagnostics;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

/** The diagnostics jar as a mod, so that it shows in the mod list and FML checks its Demonica version. */
@Mod(
    modid = DemonicaDiagnosticsMod.MODID,
    useMetadata = true,
    clientSideOnly = true,
    acceptableRemoteVersions = "*",
    dependencies = "required-after:demonica"
)
public class DemonicaDiagnosticsMod {
    public static final String MODID = "demonica_diagnostics";

    @EventHandler
    public void onInit(FMLInitializationEvent event) {
        if (Diagnostics.present()) {
            DevHarness.install();
            ShadowCallbackProbe.install();
        }
    }
}
