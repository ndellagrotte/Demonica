package com.demonica.celeritas.guard;

import com.llamalad7.mixinextras.injector.LateInjectionApplicatorExtension;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethodApplicatorExtension;
import com.llamalad7.mixinextras.sugar.impl.SugarPostProcessingExtension;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The quarantine's injection reports wait for MixinExtras's late injectors: the audit extension runs right after
 * MixinExtras's applicators (the real ones, from the MixinExtras that Cleanroom ships), and fails soft.
 */
class InjectionAuditExtensionTest {
    private static final String TARGET = "org/example/Target";

    private final IExtension head = new Other();
    private final IExtension late = new LateInjectionApplicatorExtension();
    private final IExtension sugar = new SugarPostProcessingExtension();
    private final IExtension wrapMethod = new WrapMethodApplicatorExtension();
    // Stands for Mixin's class checker, which MixinExtras keeps last.
    private final IExtension tail = new Other();

    @Test
    void registersRightAfterMixinExtrasApplicators() throws ReflectiveOperationException {
        Extensions registry = registry(this.head, this.late, this.sugar, this.wrapMethod, this.tail);

        InjectionAuditExtension audit = InjectionAuditExtension.register(registry);

        assertNotNull(audit);
        List<IExtension> expected = List.of(this.head, this.late, this.sugar, this.wrapMethod, audit, this.tail);
        assertEquals(expected, registry.getExtensions());
        assertEquals(expected, registry.getActiveExtensions());
        // Mixin rebuilds the active list from the full one whenever new configs arrive.
        registry.select(null);
        assertEquals(expected, registry.getActiveExtensions());
    }

    @Test
    void staysOutWithoutMixinExtras() throws ReflectiveOperationException {
        Extensions registry = registry(this.head, this.tail);

        assertNull(InjectionAuditExtension.register(registry));
        assertEquals(List.of(this.head, this.tail), registry.getExtensions());
        assertEquals(List.of(this.head, this.tail), registry.getActiveExtensions());
    }

    @Test
    void writesDeferredReportsOnceTheTargetIsDone() throws ReflectiveOperationException {
        InjectionAuditExtension audit = InjectionAuditExtension.register(registry(this.head, this.late, this.wrapMethod, this.tail));
        List<String> written = new ArrayList<>();
        audit.defer(TARGET, late -> written.add("first " + late));
        audit.defer(TARGET, late -> written.add("second " + late));
        audit.defer("org/example/Other", late -> written.add("other " + late));

        audit.postApply(context(TARGET));
        audit.postApply(context(TARGET));

        assertEquals(List.of("first true", "second true"), written);
    }

    @Test
    void leavesLateInjectorsUncheckedWhenANewerMixinExtrasRegistersAfterIt() throws ReflectiveOperationException {
        Extensions registry = registry(this.head, this.late, this.wrapMethod, this.tail);
        InjectionAuditExtension audit = InjectionAuditExtension.register(registry);
        // A newer MixinExtras takes over and appends its extensions after the audit.
        registry.add(new LateInjectionApplicatorExtension());
        registry.select(null);
        List<Boolean> written = new ArrayList<>();
        audit.defer(TARGET, written::add);

        audit.postApply(context(TARGET));

        assertEquals(List.of(false), written);
    }

    @Test
    void aFailingReportDoesNotEscape() throws ReflectiveOperationException {
        InjectionAuditExtension audit = InjectionAuditExtension.register(registry(this.late));
        audit.defer(TARGET, late -> {
            throw new IllegalStateException("a broken report");
        });

        assertDoesNotThrow(() -> audit.postApply(context(TARGET)));
    }

    private static Extensions registry(IExtension... extensions) {
        Extensions registry = new Extensions(null);
        for (IExtension extension : extensions) {
            registry.add(extension);
        }
        // None of these extensions reads the environment.
        registry.select(null);
        return registry;
    }

    private static ITargetClassContext context(String name) {
        ClassNode target = new ClassNode();
        target.name = name;
        return new ITargetClassContext() {
            @Override
            public ClassInfo getClassInfo() {
                return null;
            }

            @Override
            public ClassNode getClassNode() {
                return target;
            }
        };
    }

    private static final class Other implements IExtension {
        @Override
        public boolean checkActive(MixinEnvironment environment) {
            return true;
        }

        @Override
        public void preApply(ITargetClassContext context) {
        }

        @Override
        public void postApply(ITargetClassContext context) {
        }

        @Override
        public void export(MixinEnvironment env, String name, boolean force, ClassNode classNode) {
        }
    }
}
