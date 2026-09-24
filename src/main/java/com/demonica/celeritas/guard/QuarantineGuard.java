package com.demonica.celeritas.guard;

import com.demonica.loading.Environment;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Decides which quarantine patches may apply to the installed Celeritas (docs/celeritas/LEDGER.md, "The guard").
 *
 * <p>If the Celeritas jar is one of the builds the mod jar was made for (its SHA-256 is pinned), every patch applies.
 * Otherwise every anchor of every patch ({@link AnchorFile}) is checked against the jar's class bytes, and a group
 * whose anchors moved is turned off as {@link PatchGroup} says, before Mixin applies anything:
 * <ul>
 *   <li>{@link Level#L1}: {@link PatchGroup#SHADOW} failed; shader packs draw no terrain shadows.</li>
 *   <li>{@link Level#L2}: {@link PatchGroup#CORE_TERRAIN} failed; shaders are off, with the reason in the log and on
 *   the shader pack screen.</li>
 *   <li>{@link Level#L3}: the anchors could not be checked at all; shaders are off and only the patches outside the
 *   shader groups apply.</li>
 * </ul>
 * The other groups' failures cost their feature only. The decision is made once, from class bytes: this never loads a
 * Celeritas class.
 */
public final class QuarantineGuard {
    private static final Logger LOGGER = LogManager.getLogger("DemonicaQuarantine");
    /**
     * Group names or ledger ids (comma-separated) to treat as failed, to rehearse the guard in a dev client; {@code L3}
     * sets the anchor list aside, as if it could not be read.
     */
    public static final String DRILL_PROPERTY = "demonica.guard.drill";
    /** {@code always}: check the anchors even when the jar is pinned. */
    public static final String AUDIT_PROPERTY = "demonica.guard.audit";
    private static final String REFMAP = "mixins.demonica-refmap.json";
    private static final String DRILLED = "turned off by -D" + DRILL_PROPERTY;
    private static final Set<PatchGroup> SHADER_GROUPS = EnumSet.of(PatchGroup.CORE_TERRAIN, PatchGroup.SHADOW, PatchGroup.MESHING);

    /** What the installed Celeritas leaves of shaders. Failures outside the shader groups cost their own feature only. */
    public enum Level {
        L0("shaders on, with terrain shadows"),
        L1("shaders on, without terrain shadows"),
        L2("shaders off"),
        /** The patches could not be checked. */
        L3("shaders off, and only the patches outside the shader groups apply");

        private final String effect;

        Level(String effect) {
            this.effect = effect;
        }

        public String effect() {
            return this.effect;
        }
    }

    /** What the guard found and decided. */
    public static final class Verdict {
        private final Level level;
        private final List<AnchorAudit.Failure> failures;
        private final Set<PatchGroup> failedGroups;
        private final Set<String> disabledMixins;
        private final Set<PatchGroup> disabledGroups;
        private final @Nullable String shaderReason;
        private final List<String> notices;
        private final List<String> shaderNotices;

        Verdict(Level level, List<AnchorAudit.Failure> failures, Set<PatchGroup> failedGroups, Set<String> disabledMixins,
                Set<PatchGroup> disabledGroups, @Nullable String shaderReason, List<String> notices) {
            this.level = level;
            this.failures = List.copyOf(failures);
            this.failedGroups = Collections.unmodifiableSet(failedGroups);
            this.disabledMixins = Collections.unmodifiableSet(disabledMixins);
            this.disabledGroups = Collections.unmodifiableSet(disabledGroups);
            this.shaderReason = shaderReason;
            this.notices = List.copyOf(notices);
            List<String> shaderNotices = new ArrayList<>();
            if (shaderReason == null && failedGroups.contains(PatchGroup.SHADOW)) {
                shaderNotices.add("Terrain shadows are off: Demonica's shadow patches do not fit the installed Celeritas.");
            }
            if (shaderReason == null && failedGroups.contains(PatchGroup.MESHING)) {
                shaderNotices.add("Shader packs get no block IDs from terrain: Demonica's meshing patches do not fit the installed Celeritas.");
            }
            this.shaderNotices = List.copyOf(shaderNotices);
        }

        public Level level() {
            return this.level;
        }

        /** The anchors that did not hold (none if the jar is pinned). */
        public List<AnchorAudit.Failure> failures() {
            return this.failures;
        }

        public Set<PatchGroup> failedGroups() {
            return this.failedGroups;
        }

        /** The quarantine mixins (binary names) that must not apply. */
        public Set<String> disabledMixins() {
            return this.disabledMixins;
        }

        /**
         * Whether Mixin may apply the quarantine mixin. {@code group} is asked for only when the anchor list could not be
         * read (level L3), to keep the patches outside the shader groups; null means the group is not known either.
         */
        public boolean shouldApply(String mixin, GroupLookup group) {
            if (this.disabledMixins.contains(mixin)) {
                return false;
            }
            if (this.disabledGroups.isEmpty()) {
                return true;
            }
            PatchGroup known = group.groupOf(mixin);
            return known != null && !this.disabledGroups.contains(known);
        }

        /** Whether shader packs may be used. */
        public boolean shadersAllowed() {
            return this.level != Level.L2 && this.level != Level.L3;
        }

        /** Why shaders are off (L2, L3), or null. */
        public @Nullable String shaderReason() {
            return this.shaderReason;
        }

        /** What else is limited, one sentence per failed group or patch, with the evidence, for the log. */
        public List<String> notices() {
            return this.notices;
        }

        /** What shader packs lack while shaders are on, one short sentence each, for the shader pack screen. */
        public List<String> shaderNotices() {
            return this.shaderNotices;
        }
    }

    /** The group of a quarantine mixin, read from its {@link Patch}; null if it cannot be read. */
    @FunctionalInterface
    public interface GroupLookup {
        @Nullable PatchGroup groupOf(String mixin);
    }

    /** What {@link #decide} works from. */
    record Inputs(@Nullable AnchorFile.Contents anchors, @Nullable String anchorsProblem, @Nullable String jarName,
                  @Nullable String jarSha256, AnchorAudit.ClassSource classes, Map<String, Map<String, String>> refmap,
                  Set<String> drills, boolean forceAudit, boolean development) {
    }

    private static volatile @Nullable Verdict current;

    private QuarantineGuard() {
    }

    /**
     * The decision for this game, made (and logged) on first use: by the quarantine's config plugin before Mixin
     * applies any of its mixins, or by whatever asks first.
     */
    public static Verdict current() {
        Verdict verdict = current;
        if (verdict == null) {
            synchronized (QuarantineGuard.class) {
                verdict = current;
                if (verdict == null) {
                    verdict = current = evaluate();
                }
            }
        }
        return verdict;
    }

    private static Verdict evaluate() {
        if (Launch.classLoader == null) {
            // Unit tests, or any other code outside a launched game: there is no installed Celeritas to check.
            return new Verdict(Level.L0, List.of(), Set.of(), Set.of(), Set.of(), null, List.of());
        }
        try {
            ClassLoader loader = Launch.classLoader;
            AnchorFile.Contents anchors = null;
            String anchorsProblem = null;
            try (InputStream in = loader.getResourceAsStream(AnchorFile.RESOURCE)) {
                if (in == null) {
                    anchorsProblem = AnchorFile.RESOURCE + " is missing from the Demonica jar";
                } else {
                    anchors = AnchorFile.read(new InputStreamReader(in, StandardCharsets.UTF_8));
                }
            } catch (IOException | RuntimeException e) {
                anchorsProblem = AnchorFile.RESOURCE + " could not be read (" + e.getMessage() + ")";
            }
            File jar = Environment.celeritasJar();
            String sha256 = null;
            if (jar != null) {
                try {
                    sha256 = sha256(jar);
                } catch (IOException e) {
                    LOGGER.warn("Could not read {} to compare it with the pin", jar, e);
                }
            }
            Verdict verdict = decide(new Inputs(anchors, anchorsProblem, jar != null ? jar.getName() : null, sha256,
                name -> Launch.classLoader.getClassBytes(name.replace('/', '.')), readRefmap(loader), drills(),
                "always".equalsIgnoreCase(System.getProperty(AUDIT_PROPERTY)), isDevelopment()));
            return verdict;
        } catch (RuntimeException | LinkageError e) {
            LOGGER.error("The Celeritas patch guard failed; shaders are off and only Demonica's non-shader patches apply", e);
            return level3("the guard failed (" + e + ")", List.of());
        }
    }

    /** The decision from its inputs. Logs what it found; never throws for a missing or broken class. */
    static Verdict decide(Inputs inputs) {
        AnchorFile.Contents contents = inputs.drills().contains(Level.L3.name()) ? null : inputs.anchors();
        if (contents == null) {
            String problem = inputs.anchorsProblem() != null ? inputs.anchorsProblem()
                : inputs.anchors() != null ? "its anchor list was set aside by -D" + DRILL_PROPERTY : "its anchor list is missing";
            LOGGER.error("Cannot check Demonica's Celeritas patches: {}. Shaders are off, and only Demonica's non-shader patches apply.", problem);
            return level3(problem, List.of());
        }
        String upstream = abbreviate(contents.upstreamCommit());
        boolean pinned = inputs.jarSha256() != null && contents.pins().contains(inputs.jarSha256().toLowerCase(Locale.ROOT));
        String jar = inputs.jarName() != null ? inputs.jarName() : "Celeritas (not loaded from a jar)";
        if (pinned) {
            LOGGER.info("{} is the Celeritas build this Demonica was made for (upstream {}, SHA-256 {})", jar, upstream,
                abbreviate(inputs.jarSha256()));
        } else if (inputs.development()) {
            LOGGER.info("{} is not a pinned Celeritas build (the development workspace remaps Celeritas, so it never is); "
                + "checking the anchors of Demonica's patches", jar);
        } else {
            LOGGER.warn("{} is not the Celeritas build this Demonica was made for (upstream {}; SHA-256 {}, expected {}); "
                    + "checking the anchors of Demonica's patches", jar, upstream,
                inputs.jarSha256() != null ? abbreviate(inputs.jarSha256()) : "unknown", String.join(" or ", contents.pins()));
        }
        if (pinned && !inputs.forceAudit() && inputs.drills().isEmpty()) {
            return new Verdict(Level.L0, List.of(), Set.of(), Set.of(), Set.of(), null, List.of());
        }

        List<AnchorAudit.Failure> failures = new ArrayList<>(new AnchorAudit(inputs.classes(), inputs.refmap()).audit(contents.anchors()));
        failures.addAll(drillFailures(contents.anchors(), inputs.drills()));
        Map<String, PatchGroup> groups = new LinkedHashMap<>();
        Map<String, String> patches = new HashMap<>();
        for (Anchor anchor : contents.anchors()) {
            groups.putIfAbsent(anchor.mixin(), anchor.group());
            patches.putIfAbsent(anchor.mixin(), anchor.patches());
        }
        if (failures.isEmpty()) {
            LOGGER.info("All {} anchors of Demonica's {} Celeritas patches hold; every patch applies", contents.anchors().size(), groups.size());
            return new Verdict(Level.L0, List.of(), Set.of(), Set.of(), Set.of(), null, List.of());
        }

        Set<PatchGroup> failedGroups = EnumSet.noneOf(PatchGroup.class);
        Set<String> disabled = new LinkedHashSet<>();
        Map<String, List<AnchorAudit.Failure>> byMixin = new LinkedHashMap<>();
        for (AnchorAudit.Failure failure : failures) {
            Anchor anchor = failure.anchor();
            if (isDrill(failure)) {
                LOGGER.warn("Celeritas patch {} ({}) is {}", anchor.patches(), Anchor.simpleName(anchor.mixin().replace('.', '/')), failure.problem());
            } else {
                LOGGER.warn("Celeritas patch anchor missing: {}", failure);
            }
            failedGroups.add(anchor.group());
            byMixin.computeIfAbsent(anchor.mixin(), k -> new ArrayList<>()).add(failure);
            switch (anchor.group().gate()) {
                case GROUP -> groups.forEach((mixin, group) -> {
                    if (group == anchor.group()) {
                        disabled.add(mixin);
                    }
                });
                case MIXIN -> disabled.add(anchor.mixin());
                case NEVER -> { }
            }
        }

        Level level = failedGroups.contains(PatchGroup.CORE_TERRAIN) ? Level.L2 : failedGroups.contains(PatchGroup.SHADOW) ? Level.L1 : Level.L0;
        String reason = null;
        List<String> notices = new ArrayList<>();
        for (PatchGroup group : failedGroups) {
            List<AnchorAudit.Failure> groupFailures = failures.stream().filter(f -> f.anchor().group() == group).toList();
            String evidence = evidence(groupFailures);
            switch (group) {
                case CORE_TERRAIN -> reason = "Shaders are off: Demonica's terrain patches do not fit the installed Celeritas (" + evidence
                    + "). Install the Celeritas build of upstream commit " + upstream + ".";
                case SHADOW -> notices.add("Terrain shadows are off: Demonica's shadow patches do not fit the installed Celeritas ("
                    + evidence + ").");
                case MESHING -> notices.add("Shader packs get no block IDs from terrain, and water is drawn as translucent terrain: "
                    + "Demonica's meshing patches do not fit the installed Celeritas (" + evidence + ").");
                case OPTIONS -> notices.add("Video Settings keeps Celeritas's own screen: Reese's Sodium Options needs a patch that "
                    + "does not fit the installed Celeritas (" + evidence + ").");
                case BASE -> notices.add("Terrain fog may be drawn wrong: Demonica's fog patch does not fully fit the installed "
                    + "Celeritas (" + evidence + ").");
                case DEGRADE, COMPAT -> byMixin.forEach((mixin, mixinFailures) -> {
                    if (groups.get(mixin) == group) {
                        notices.add(patches.get(mixin) + " is off: it does not fit the installed Celeritas (" + evidence(mixinFailures) + ").");
                    }
                });
            }
        }
        if (reason != null) {
            LOGGER.error(reason);
        }
        for (String notice : notices) {
            LOGGER.warn(notice);
        }
        LOGGER.warn("Celeritas patch guard: level {} ({}); {} of {} patch mixins are turned off: {}", level, level.effect(), disabled.size(),
            groups.size(), disabled.stream().map(mixin -> mixin.substring(mixin.lastIndexOf('.') + 1)).toList());
        return new Verdict(level, failures, failedGroups, disabled, Set.of(), reason, notices);
    }

    private static Verdict level3(String problem, List<AnchorAudit.Failure> failures) {
        String reason = "Shaders are off: Demonica could not check its patches against the installed Celeritas (" + problem + ").";
        return new Verdict(Level.L3, failures, Set.of(), Set.of(), SHADER_GROUPS, reason, List.of());
    }

    /**
     * Failures that {@link #DRILL_PROPERTY} asks for: one per mixin of a named group or carrying a named ledger id.
     * {@code drills} are upper case.
     */
    private static List<AnchorAudit.Failure> drillFailures(List<Anchor> anchors, Set<String> drills) {
        List<AnchorAudit.Failure> failures = new ArrayList<>();
        Set<String> drilled = new LinkedHashSet<>();
        for (Anchor anchor : anchors) {
            boolean named = drills.contains(anchor.group().name())
                || Arrays.stream(anchor.patches().split(",")).anyMatch(id -> drills.contains(id.toUpperCase(Locale.ROOT)));
            if (named && drilled.add(anchor.mixin())) {
                failures.add(new AnchorAudit.Failure(anchor, DRILLED));
            }
        }
        return failures;
    }

    /** A failure {@link #DRILL_PROPERTY} asked for, which names no anchor that moved. */
    private static boolean isDrill(AnchorAudit.Failure failure) {
        return DRILLED.equals(failure.problem());
    }

    /** The first failure, and how many more there are. */
    private static String evidence(List<AnchorAudit.Failure> failures) {
        AnchorAudit.Failure first = failures.get(0);
        String evidence = isDrill(first) ? first.anchor().patches() + " " + first.problem()
            : first.anchor().patches() + ": " + first.anchor().describe() + ", " + first.problem();
        return failures.size() == 1 ? evidence : evidence + ", and " + (failures.size() - 1) + " more in the log";
    }

    /** The groups and ledger ids {@link #DRILL_PROPERTY} names, in upper case. */
    private static Set<String> drills() {
        Set<String> drills = new LinkedHashSet<>();
        for (String drill : System.getProperty(DRILL_PROPERTY, "").split(",")) {
            if (!drill.isBlank()) {
                drills.add(drill.trim().toUpperCase(Locale.ROOT));
            }
        }
        return drills;
    }

    private static boolean isDevelopment() {
        Object deobfuscated = Launch.blackboard != null ? Launch.blackboard.get("fml.deobfuscatedEnvironment") : null;
        return Boolean.TRUE.equals(deobfuscated);
    }

    /** The refmap Mixin reads for the quarantine: annotation strings of each mixin and their production names. */
    static Map<String, Map<String, String>> readRefmap(ClassLoader loader) {
        Map<String, Map<String, String>> refmap = new HashMap<>();
        try (InputStream in = loader.getResourceAsStream(REFMAP)) {
            if (in == null) {
                return refmap;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                JsonObject mappings = root.isJsonObject() ? root.getAsJsonObject().getAsJsonObject("mappings") : null;
                if (mappings != null) {
                    for (Map.Entry<String, JsonElement> mixin : mappings.entrySet()) {
                        Map<String, String> references = new HashMap<>();
                        for (Map.Entry<String, JsonElement> reference : mixin.getValue().getAsJsonObject().entrySet()) {
                            references.put(reference.getKey(), reference.getValue().getAsString());
                        }
                        refmap.put(mixin.getKey(), references);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read {}; the anchors of vanilla members are checked by their development names", REFMAP, e);
        }
        return refmap;
    }

    static String sha256(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        try (InputStream in = new DigestInputStream(Files.newInputStream(file.toPath()), digest)) {
            in.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String abbreviate(@Nullable String hash) {
        return hash == null ? "unknown" : hash.length() > 8 ? hash.substring(0, 8) : hash;
    }
}
