package com.demonica.diagnostics;

import net.minecraftforge.fml.common.Mod;

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
}
