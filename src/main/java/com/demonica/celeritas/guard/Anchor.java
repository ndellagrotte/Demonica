package com.demonica.celeritas.guard;

/**
 * Something in the Celeritas jar that a quarantine patch depends on: a class, a member, or a call or field access inside
 * a method. {@link AnchorExtractor} derives the anchors from the quarantine mixins at build time, and
 * {@link AnchorAudit} checks them against class bytes.
 *
 * <p>Members use Mixin's notation. {@code member} is a method selector ({@code name} or {@code name(desc)}) or a field
 * ({@code name:desc}); {@code target} is a full reference ({@code Lowner;name(desc)} or {@code Lowner;name:desc}). Both
 * may be strings a mixin annotation wrote, which the refmap maps to the production names of vanilla members.
 *
 * @param mixin   the quarantine mixin, as a binary name
 * @param patches the ledger ids the mixin carries, comma-separated
 * @param owner   the internal name of the class the anchor is checked in
 * @param member  the method or field in {@code owner}; empty for {@link Kind#CLASS}
 * @param target  the invoked method or accessed field, for {@link Kind#INVOKE} and {@link Kind#ACCESS}; empty otherwise
 */
public record Anchor(String mixin, PatchGroup group, String patches, Kind kind, String owner, String member, String target) {
    public enum Kind {
        /** {@code owner} exists. */
        CLASS,
        /** {@code owner} declares a method that {@code member} selects. */
        METHOD,
        /** {@code owner} declares the field {@code member}. */
        FIELD,
        /** {@code member} resolves from {@code owner}: {@code owner} or one of its supertypes declares it. */
        MEMBER,
        /** A supertype of {@code owner} declares the method {@code member}, and {@code owner} does not. */
        INHERITED,
        /** {@code owner} does not declare the method {@code member}. */
        ABSENT,
        /** A method of {@code owner} that {@code member} selects invokes {@code target}. */
        INVOKE,
        /** A method of {@code owner} that {@code member} selects reads or writes the field {@code target}. */
        ACCESS
    }

    /** A short description for the log: simple class names, and what must hold. */
    public String describe() {
        String owner = simpleName(this.owner);
        return switch (this.kind) {
            case CLASS -> "class " + owner;
            case METHOD, FIELD, MEMBER -> owner + "." + memberName(this.member);
            case INHERITED -> owner + " inheriting " + memberName(this.member);
            case ABSENT -> owner + " not overriding " + memberName(this.member);
            case INVOKE -> owner + "." + memberName(this.member) + " calling " + targetName(this.target);
            case ACCESS -> owner + "." + memberName(this.member) + " using " + targetName(this.target);
        };
    }

    @Override
    public String toString() {
        return this.patches + " " + this.kind + " " + this.owner + (this.member.isEmpty() ? "" : " " + this.member)
            + (this.target.isEmpty() ? "" : " -> " + this.target);
    }

    static String simpleName(String internalName) {
        return internalName.substring(internalName.lastIndexOf('/') + 1);
    }

    /** {@code name(desc)} or {@code name:desc} (with an optional {@code Lowner;} before it) as {@code name}. */
    private static String memberName(String member) {
        String name = MemberRef.parse(member).name();
        return name.equals("<init>") ? "constructor" : name;
    }

    private static String targetName(String target) {
        MemberRef ref = MemberRef.parse(target);
        String name = ref.name().equals("<init>") ? "constructor" : ref.name();
        return ref.owner() != null ? simpleName(ref.owner()) + "." + name : name;
    }
}
