package com.demonica.celeritas.guard;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The quarantine's injection audit tells an injector that reached every method it names from one that did not. */
class InjectionAuditTest {
    private static final String MIXIN = "com.demonica.mixin.celeritas.seam.ExampleMixin";
    private static final String TARGET = "org/example/Target";

    @Test
    void reportsInjectorsThatMissedTheirTargets() {
        ClassNode mixin = new ClassNode();
        mixin.name = MIXIN.replace('.', '/');
        mixin.methods.add(handler("demonica$everywhere", "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;", "setupTerrain", "setupShadowTerrain"));
        mixin.methods.add(handler("demonica$halfway", "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;", "setupTerrain", "setupShadowTerrain"));
        mixin.methods.add(handler("demonica$nowhere", "Lorg/spongepowered/asm/mixin/injection/ModifyArg;", "renderBlockLayer"));
        mixin.methods.add(handler("demonica$notMerged", "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;", "render"));
        mixin.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "demonica$notAnInjector", "()V", null, null));

        ClassNode target = new ClassNode();
        target.name = TARGET;
        MethodNode everywhere = merged("localvar$zca000$demonica$everywhere", MIXIN);
        MethodNode halfway = merged("localvar$zca000$demonica$halfway", MIXIN);
        MethodNode nowhere = merged("modify$zca000$demonica$nowhere", MIXIN);
        // Another mixin's handler of the same name must not stand in for the missing one.
        MethodNode foreign = merged("wrapOperation$zdb000$demonica$notMerged", "com.example.OtherMixin");
        target.methods.add(caller("setupTerrain", everywhere, halfway, foreign));
        target.methods.add(caller("setupShadowTerrain", everywhere));
        target.methods.add(caller("renderBlockLayer"));
        target.methods.addAll(List.of(everywhere, halfway, nowhere, foreign));

        Map<String, InjectionAudit.Injector> injectors = InjectionAudit.audit(target, mixin, MIXIN).stream()
            .collect(Collectors.toMap(InjectionAudit.Injector::handler, Function.identity()));

        assertEquals(4, injectors.size(), injectors.toString());
        assertTrue(injectors.get("demonica$everywhere").applied());
        assertEquals(2, injectors.get("demonica$everywhere").callingMethods());
        assertFalse(injectors.get("demonica$halfway").applied());
        assertEquals(1, injectors.get("demonica$halfway").callingMethods());
        assertFalse(injectors.get("demonica$nowhere").applied());
        assertEquals(0, injectors.get("demonica$nowhere").callingMethods());
        assertFalse(injectors.get("demonica$notMerged").applied());
        assertEquals(-1, injectors.get("demonica$notMerged").callingMethods());
    }

    private static MethodNode handler(String name, String injector, String... targets) {
        MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE, name, "(I)I", null, null);
        AnnotationNode annotation = new AnnotationNode(injector);
        // As ClassReader builds it: an array value is a List.
        AnnotationVisitor selectors = annotation.visitArray("method");
        for (String target : targets) {
            selectors.visit(null, target);
        }
        selectors.visitEnd();
        method.visibleAnnotations = List.of(annotation);
        return method;
    }

    private static MethodNode merged(String name, String mixin) {
        MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE, name, "(I)I", null, null);
        AnnotationNode annotation = new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
        annotation.visit("mixin", mixin);
        method.visibleAnnotations = List.of(annotation);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        return method;
    }

    private static MethodNode caller(String name, MethodNode... handlers) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, "(I)V", null, null);
        for (MethodNode handler : handlers) {
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, handler.name, handler.desc, false));
            method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 1));
        }
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        return method;
    }
}
