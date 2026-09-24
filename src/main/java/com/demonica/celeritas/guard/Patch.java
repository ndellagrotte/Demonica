package com.demonica.celeritas.guard;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The ledger patches a quarantine mixin carries (docs/celeritas/LEDGER.md) and the group its failure turns off.
 * {@link AnchorExtractor} reads it from the class file at build time, together with the anchors the mixin's injectors,
 * shadows and code name. Nothing reads it at runtime.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Patch {
    /** The ledger ids, such as {@code "S5"}. */
    String[] value();

    PatchGroup group();

    /**
     * What the patch relies on that no injector of the mixin names, in Mixin's member notation:
     * <ul>
     *   <li>{@code "Lowner;name(desc) calls Lowner;name(desc)"}: the first method still invokes the second, so the patched
     *   code is the code that runs;</li>
     *   <li>{@code "absent Lowner;name(desc)"}: the class does not declare the method, whose override would bypass the
     *   patch;</li>
     *   <li>{@code "Lowner;name(desc)"}: the member exists, declared by the class or one of its supertypes.</li>
     * </ul>
     */
    String[] context() default {};

    /** Demonica classes that carry the patch's behaviour: every Celeritas member their code uses is an anchor too. */
    Class<?>[] uses() default {};

    /** Packages (internal names, ending in {@code /}) whose classes all carry the patch's behaviour, as {@link #uses}. */
    String[] usesPackages() default {};
}
