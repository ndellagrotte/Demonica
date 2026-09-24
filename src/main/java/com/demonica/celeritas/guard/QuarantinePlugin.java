package com.demonica.celeritas.guard;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The plugin of {@code mixins.demonica.celeritas.json}, the quarantine: the only config allowed to patch Celeritas's
 * own classes (docs/celeritas/LEDGER.md). The config is non-fatal ({@code required: false}, {@code defaultRequire: 0}),
 * so a patch whose anchor moved is skipped instead of crashing the game. Before Mixin applies anything,
 * {@link QuarantineGuard} checks the installed Celeritas against the pin and the patches' anchors, and this plugin
 * leaves out the mixins of the groups that failed. It must never load a Celeritas class. Once Mixin has finished a
 * patched class, including the injectors MixinExtras applies late ({@link InjectionAuditExtension}), it logs any
 * injector that found no target ({@link InjectionAudit}), which Mixin itself does not report here.
 */
public class QuarantinePlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("DemonicaQuarantine");
    private static final String PATCH = "Lcom/demonica/celeritas/guard/Patch;";

    // Null if it could not be registered: the reports are then written at once, without MixinExtras's late injectors.
    private InjectionAuditExtension auditExtension;
    private final Set<String> reportedSkips = ConcurrentHashMap.newKeySet();

    @Override
    public void onLoad(String mixinPackage) {
        LOGGER.info("Loaded the Celeritas patch quarantine ({})", mixinPackage);
        QuarantineGuard.current();
        this.auditExtension = InjectionAuditExtension.register();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        QuarantineGuard.Verdict verdict = QuarantineGuard.current();
        if (verdict.shouldApply(mixinClassName, QuarantinePlugin::groupOf)) {
            return true;
        }
        if (this.reportedSkips.add(mixinClassName)) {
            LOGGER.warn("Not applying {} to {}: the Celeritas patch guard turned it off (level {})", patchName(mixinClassName),
                targetClassName, verdict.level());
        }
        return false;
    }

    /** The group a quarantine mixin's {@link Patch} names, read from its class file; null if it cannot be read. */
    private static PatchGroup groupOf(String mixinClassName) {
        try {
            ClassNode mixin = MixinService.getService().getBytecodeProvider().getClassNode(mixinClassName, false, ClassReader.SKIP_CODE);
            for (AnnotationNode annotation : mixin.invisibleAnnotations != null ? mixin.invisibleAnnotations : List.<AnnotationNode>of()) {
                if (PATCH.equals(annotation.desc)) {
                    for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                        if ("group".equals(annotation.values.get(i)) && annotation.values.get(i + 1) instanceof String[] group) {
                            return PatchGroup.valueOf(group[1]);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Could not read the patch group of {}", mixinClassName, e);
        }
        return null;
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
        try {
            // The mixin as compiled: IMixinInfo.getClassNode copies Mixin's preprocessed tree, in which MixinExtras has
            // replaced the annotation of an injector with sugared parameters.
            ClassNode mixin = MixinService.getService().getBytecodeProvider().getClassNode(mixinClassName, false, ClassReader.SKIP_CODE);
            if (this.auditExtension != null) {
                this.auditExtension.defer(targetClass.name,
                    lateInjectorsApplied -> report(targetClassName, targetClass, mixinClassName, mixin, lateInjectorsApplied));
            } else {
                report(targetClassName, targetClass, mixinClassName, mixin, false);
            }
        } catch (Exception e) {
            LOGGER.warn("Could not audit the injectors of {} in {}", patchName(mixinClassName), targetClassName, e);
        }
    }

    private static void report(String targetClassName, ClassNode targetClass, String mixinClassName, ClassNode mixin, boolean lateInjectorsApplied) {
        String patch = patchName(mixinClassName);
        List<InjectionAudit.Injector> injectors = InjectionAudit.audit(targetClass, mixin, mixinClassName);
        int applied = 0;
        int unchecked = 0;
        for (InjectionAudit.Injector injector : injectors) {
            if (injector.late() && !lateInjectorsApplied) {
                unchecked++;
            } else if (injector.applied()) {
                applied++;
            } else if (injector.callingMethods() < 0) {
                LOGGER.warn("{}.{} was not merged into {}; that part of the patch is inactive", patch, injector.handler(), targetClassName);
            } else {
                LOGGER.warn("{}.{} found its target in {} of the {} methods it patches in {}; that part of the patch is inactive",
                    patch, injector.handler(), injector.callingMethods(), injector.selectors(), targetClassName);
            }
        }
        if (unchecked == 0) {
            LOGGER.info("Applied {} to {}: {} of {} injectors found their targets", patch, targetClassName, applied, injectors.size());
        } else {
            LOGGER.info("Applied {} to {}: {} of {} injectors found their targets; {} more are applied later by MixinExtras "
                + "and not checked", patch, targetClassName, applied, injectors.size() - unchecked, unchecked);
        }
    }

    private static String patchName(String mixinClassName) {
        return mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
    }
}
