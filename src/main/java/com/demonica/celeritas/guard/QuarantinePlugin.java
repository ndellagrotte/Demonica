package com.demonica.celeritas.guard;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * The plugin of {@code mixins.demonica.celeritas.json}, the quarantine: the only config allowed to patch Celeritas's
 * own classes (docs/celeritas/LEDGER.md). The config is non-fatal ({@code required: false}, {@code defaultRequire: 0}),
 * so a patch whose anchor moved is skipped instead of crashing the game. This plugin is where the pin check and the
 * anchor audit switch off failed patch groups; it must never load a Celeritas class.
 */
public class QuarantinePlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("DemonicaQuarantine");

    @Override
    public void onLoad(String mixinPackage) {
        LOGGER.info("Loaded the Celeritas patch quarantine ({})", mixinPackage);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
