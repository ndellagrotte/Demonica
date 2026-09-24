package com.demonica.celeritas.guard;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;

/**
 * The plugin of {@code mixins.demonica.celeritas.json}, the quarantine: the only config allowed to patch Celeritas's
 * own classes (docs/celeritas/LEDGER.md). The config is non-fatal ({@code required: false}, {@code defaultRequire: 0}),
 * so a patch whose anchor moved is skipped instead of crashing the game. This plugin is where the pin check and the
 * anchor audit switch off failed patch groups; it must never load a Celeritas class. After each patch is applied, it
 * logs any injector that found no target ({@link InjectionAudit}), which Mixin itself does not report here.
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
        String patch = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        List<InjectionAudit.Injector> injectors;
        try {
            // The mixin as compiled: IMixinInfo.getClassNode copies Mixin's preprocessed tree, in which MixinExtras has
            // replaced the annotation of an injector with sugared parameters.
            ClassNode mixin = MixinService.getService().getBytecodeProvider().getClassNode(mixinClassName, false, ClassReader.SKIP_CODE);
            injectors = InjectionAudit.audit(targetClass, mixin, mixinClassName);
        } catch (Exception e) {
            LOGGER.warn("Could not audit the injectors of {} in {}", patch, targetClassName, e);
            return;
        }
        int applied = 0;
        for (InjectionAudit.Injector injector : injectors) {
            if (injector.applied()) {
                applied++;
            } else if (injector.callingMethods() < 0) {
                LOGGER.warn("{}.{} was not merged into {}; that part of the patch is inactive", patch, injector.handler(), targetClassName);
            } else {
                LOGGER.warn("{}.{} found its target in {} of the {} methods it patches in {}; that part of the patch is inactive",
                    patch, injector.handler(), injector.callingMethods(), injector.selectors(), targetClassName);
            }
        }
        LOGGER.info("Applied {} to {}: {} of {} injectors found their targets", patch, targetClassName, applied, injectors.size());
    }
}
