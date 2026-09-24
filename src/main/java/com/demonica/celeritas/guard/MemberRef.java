package com.demonica.celeritas.guard;

import org.jetbrains.annotations.Nullable;

/**
 * A member reference in Mixin's notation: {@code Lowner;name(desc)}, {@code Lowner;name:desc}, or the same without the
 * owner, and a bare {@code name} as a method selector matching every descriptor.
 *
 * @param owner an internal name, or null when the reference names none
 * @param desc  the method or field descriptor, or null for a bare name
 */
record MemberRef(@Nullable String owner, String name, @Nullable String desc, boolean field) {
    static MemberRef parse(String reference) {
        String rest = reference;
        String owner = null;
        int semicolon = rest.indexOf(';');
        if (rest.startsWith("L") && semicolon > 0 && noneBefore(rest, semicolon, '(', ':')) {
            owner = rest.substring(1, semicolon);
            rest = rest.substring(semicolon + 1);
        }
        int paren = rest.indexOf('(');
        int colon = rest.indexOf(':');
        if (paren >= 0 && (colon < 0 || paren < colon)) {
            return new MemberRef(owner, rest.substring(0, paren), rest.substring(paren), false);
        }
        if (colon >= 0) {
            return new MemberRef(owner, rest.substring(0, colon), rest.substring(colon + 1), true);
        }
        return new MemberRef(owner, rest, null, false);
    }

    private static boolean noneBefore(String text, int end, char... characters) {
        for (int i = 0; i < end; i++) {
            for (char c : characters) {
                if (text.charAt(i) == c) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Whether a method or field with this name and descriptor is what this reference selects. */
    boolean selects(String name, String desc) {
        return this.name.equals(name) && (this.desc == null || this.desc.equals(desc));
    }

    /** Whether an instruction's owner, name and descriptor are what this reference names. */
    boolean isReferencedBy(String owner, String name, String desc) {
        return (this.owner == null || this.owner.equals(owner)) && selects(name, desc);
    }
}
