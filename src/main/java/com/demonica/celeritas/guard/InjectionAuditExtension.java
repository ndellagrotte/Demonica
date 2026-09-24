package com.demonica.celeritas.guard;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.IExtensionRegistry;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the quarantine's injection reports back until MixinExtras has applied its late injectors. MixinExtras applies
 * {@code @ModifyExpressionValue}, {@code @WrapOperation} and {@code @WrapWithCondition} in its
 * {@code LateInjectionApplicatorExtension}, and {@code @WrapMethod} in its {@code WrapMethodApplicatorExtension}: Mixin
 * transformer extensions, whose {@code postApply} runs after every config plugin's. This extension is inserted right
 * after them, the way MixinExtras inserts its own (Mixin has no API for it), so its {@code postApply} sees each target
 * with all of its injectors in place.
 *
 * <p>It fails soft: if it cannot register, or a newer MixinExtras registers its extensions after it, the reports still
 * run, and say that MixinExtras's injectors were not checked. It never loads a Celeritas class.
 */
final class InjectionAuditExtension implements IExtension {
    private static final Logger LOGGER = LogManager.getLogger("DemonicaQuarantine");
    // MixinExtras's extensions that apply injectors, by simple name: a mod may ship a relocated MixinExtras.
    private static final Set<String> MIXINEXTRAS_APPLICATORS = Set.of("LateInjectionApplicatorExtension", "WrapMethodApplicatorExtension");

    private final IExtensionRegistry registry;
    private final Map<String, List<Report>> reports = new ConcurrentHashMap<>();
    private boolean warnedOutOfOrder;

    /** A quarantine mixin's injection report. */
    @FunctionalInterface
    interface Report {
        /** @param lateInjectorsApplied whether MixinExtras had applied its late injectors, so they can be checked */
        void write(boolean lateInjectorsApplied);
    }

    private InjectionAuditExtension(IExtensionRegistry registry) {
        this.registry = registry;
    }

    /** Registers the extension with the active Mixin transformer. Returns null, having said why, if it cannot. */
    static InjectionAuditExtension register() {
        try {
            if (MixinEnvironment.getDefaultEnvironment().getActiveTransformer() instanceof IMixinTransformer transformer
                && transformer.getExtensions() instanceof Extensions registry) {
                return register(registry);
            }
            LOGGER.warn("Found no Mixin transformer extensions; injectors MixinExtras applies late will not be checked");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOGGER.warn("Could not register the injection audit after MixinExtras; injectors MixinExtras applies late "
                + "will not be checked", e);
        }
        return null;
    }

    /** Inserts the extension right after MixinExtras's applicators in both of Mixin's extension lists. */
    @SuppressWarnings("unchecked")
    static InjectionAuditExtension register(Extensions registry) throws ReflectiveOperationException {
        Field extensionsField = Extensions.class.getDeclaredField("extensions");
        Field activeField = Extensions.class.getDeclaredField("activeExtensions");
        extensionsField.setAccessible(true);
        activeField.setAccessible(true);
        List<IExtension> extensions = (List<IExtension>) extensionsField.get(registry);
        List<IExtension> active = new ArrayList<>((List<IExtension>) activeField.get(registry));
        int extensionsIndex = afterMixinExtras(extensions);
        int activeIndex = afterMixinExtras(active);
        if (extensionsIndex < 0 || activeIndex < 0) {
            LOGGER.warn("Found no active MixinExtras injector applicator; injectors MixinExtras applies late will not be checked");
            return null;
        }
        InjectionAuditExtension audit = new InjectionAuditExtension(registry);
        extensions.add(extensionsIndex, audit);
        active.add(activeIndex, audit);
        activeField.set(registry, Collections.unmodifiableList(active));
        return audit;
    }

    /** The index after the last of MixinExtras's applicators in {@code extensions}, or -1 if there is none. */
    static int afterMixinExtras(List<IExtension> extensions) {
        for (int i = extensions.size() - 1; i >= 0; i--) {
            if (MIXINEXTRAS_APPLICATORS.contains(extensions.get(i).getClass().getSimpleName())) {
                return i + 1;
            }
        }
        return -1;
    }

    /** Writes {@code report} once Mixin has finished {@code targetClass} (internal name). */
    void defer(String targetClass, Report report) {
        this.reports.computeIfAbsent(targetClass, k -> new ArrayList<>()).add(report);
    }

    @Override
    public boolean checkActive(MixinEnvironment environment) {
        return true;
    }

    @Override
    public void preApply(ITargetClassContext context) {
    }

    @Override
    public void postApply(ITargetClassContext context) {
        ClassNode target = context.getClassNode();
        List<Report> due = this.reports.remove(target.name);
        if (due == null) {
            return;
        }
        // An exception here would abort the transformation of the target class, which is not the audit's to break.
        try {
            boolean lateInjectorsApplied = this.runsAfterMixinExtras();
            for (Report report : due) {
                report.write(lateInjectorsApplied);
            }
        } catch (RuntimeException | LinkageError e) {
            LOGGER.warn("Could not audit the injectors applied to {}", target.name, e);
        }
    }

    private boolean runsAfterMixinExtras() {
        List<IExtension> active = this.registry.getActiveExtensions();
        boolean inOrder = afterMixinExtras(active) <= active.indexOf(this);
        if (!inOrder && !this.warnedOutOfOrder) {
            this.warnedOutOfOrder = true;
            LOGGER.warn("A newer MixinExtras registered its extensions after the quarantine's injection audit; injectors "
                + "MixinExtras applies late are no longer checked");
        }
        return inOrder;
    }

    @Override
    public void export(MixinEnvironment env, String name, boolean force, ClassNode classNode) {
    }
}
