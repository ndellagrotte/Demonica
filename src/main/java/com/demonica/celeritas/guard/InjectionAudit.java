package com.demonica.celeritas.guard;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds the quarantine's injectors that applied nowhere. The quarantine is non-fatal ({@code defaultRequire: 0}), so an
 * injector whose anchor moved does nothing, and Mixin does not say so. Once a quarantine mixin has been applied, each
 * of its injector handlers must be called from as many methods of the target as the injector names.
 *
 * <p>Mixin merges a handler into the target under a new name that ends with {@code $} and the handler's own name (the
 * source-id part it adds is {@code demonica$} for Demonica's {@code demonica$} handlers), and marks it
 * {@code @MixinMerged} with the mixin's class name.
 */
final class InjectionAudit {
    private static final String MIXIN_MERGED = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
    private static final Set<String> INJECTORS = Set.of(
        "Lorg/spongepowered/asm/mixin/injection/Inject;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
        "Lorg/spongepowered/asm/mixin/injection/Redirect;",
        "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;",
        "Lcom/llamalad7/mixinextras/injector/ModifyReceiver;",
        "Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;",
        "Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;",
        "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;",
        "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;");

    private InjectionAudit() {
    }

    /**
     * An injector handler of the mixin, how many target methods its {@code method} selectors name, and how many
     * methods of the target now call it (-1 if Mixin did not merge the handler at all).
     */
    record Injector(String handler, int selectors, int callingMethods) {
        boolean applied() {
            return this.callingMethods >= this.selectors;
        }
    }

    /** The injectors of {@code mixin} (read without code) and whether each reached the target it names. */
    static List<Injector> audit(ClassNode target, ClassNode mixin, String mixinClassName) {
        List<Injector> injectors = new ArrayList<>();
        for (MethodNode handler : mixin.methods) {
            AnnotationNode injector = injectorAnnotation(handler);
            if (injector == null) {
                continue;
            }
            MethodNode merged = findMerged(target, handler.name, mixinClassName);
            int calling = merged == null ? -1 : callingMethods(target, merged);
            injectors.add(new Injector(handler.name, selectorCount(injector), calling));
        }
        return injectors;
    }

    private static AnnotationNode injectorAnnotation(MethodNode method) {
        for (List<AnnotationNode> annotations : List.of(nullToEmpty(method.visibleAnnotations), nullToEmpty(method.invisibleAnnotations))) {
            for (AnnotationNode annotation : annotations) {
                if (INJECTORS.contains(annotation.desc)) {
                    return annotation;
                }
            }
        }
        return null;
    }

    private static int selectorCount(AnnotationNode injector) {
        Object selectors = value(injector, "method");
        return selectors instanceof List<?> list ? Math.max(1, list.size()) : 1;
    }

    private static MethodNode findMerged(ClassNode target, String handlerName, String mixinClassName) {
        for (MethodNode method : target.methods) {
            if ((method.name.equals(handlerName) || method.name.endsWith("$" + handlerName))
                && mixinClassName.equals(mergedFrom(method))) {
                return method;
            }
        }
        return null;
    }

    private static String mergedFrom(MethodNode method) {
        for (AnnotationNode annotation : nullToEmpty(method.visibleAnnotations)) {
            if (MIXIN_MERGED.equals(annotation.desc) && value(annotation, "mixin") instanceof String mixin) {
                return mixin;
            }
        }
        return null;
    }

    private static int callingMethods(ClassNode target, MethodNode handler) {
        Set<String> callers = new HashSet<>();
        for (MethodNode method : target.methods) {
            if (method == handler || method.instructions == null) {
                continue;
            }
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode call && call.owner.equals(target.name)
                    && call.name.equals(handler.name) && call.desc.equals(handler.desc)) {
                    callers.add(method.name + method.desc);
                    break;
                }
            }
        }
        return callers.size();
    }

    private static Object value(AnnotationNode annotation, String key) {
        List<Object> values = annotation.values;
        if (values == null) {
            return null;
        }
        for (int i = 0; i + 1 < values.size(); i += 2) {
            if (key.equals(values.get(i))) {
                return values.get(i + 1);
            }
        }
        return null;
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list != null ? list : List.of();
    }
}
