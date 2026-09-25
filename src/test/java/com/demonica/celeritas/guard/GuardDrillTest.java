package com.demonica.celeritas.guard;

import com.demonica.celeritas.CeleritasJar;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rehearses the guard against a Celeritas build that is not the pin: the pinned jar's classes are changed with ASM the
 * way a later upstream build might change them, and the guard must turn off the right patches, at the right level, with
 * a reason that names the change, and never throw.
 */
class GuardDrillTest {
    private static final String CWR = "org/taumc/celeritas/impl/render/terrain/CeleritasWorldRenderer";
    private static final String SWR = "org/embeddedt/embeddium/impl/render/terrain/SimpleWorldRenderer";
    private static final String DEFAULT_RENDERER = "org/embeddedt/embeddium/impl/render/chunk/DefaultChunkRenderer";
    private static final String ASYNC_OCCLUSION = "org/embeddedt/embeddium/impl/render/chunk/occlusion/AsyncOcclusionMode";
    private static final String MESHING_TASK = "org/taumc/celeritas/impl/render/terrain/compile/task/ChunkBuilderMeshingTask";
    private static final String TRACKER = "org/embeddedt/embeddium/impl/render/chunk/map/ChunkTracker";
    private static final String GL_PROGRAM = "org/embeddedt/embeddium/impl/gl/shader/GlProgram";
    private static final String FOG_SERVICE = "org/taumc/celeritas/impl/render/terrain/fog/GLStateManagerFogService";
    private static final String UPSTREAM_RENDER_GLOBAL = "org/taumc/celeritas/mixin/core/terrain/RenderGlobalMixin";

    private static final String QUARANTINE = "com.demonica.mixin.celeritas.";
    private static final String TRACKER_MIXIN = QUARANTINE + "internal.ChunkTrackerMixin";
    private static final String GL_PROGRAM_MIXIN = QUARANTINE + "internal.GlProgramMixin";
    private static final String COMPAT_MIXIN = QUARANTINE + "seam.ChunkBuilderMeshingTaskCompatMixin";
    private static final String FOG_MIXIN = QUARANTINE + "seam.GLStateManagerFogServiceMixin";
    private static final String RENDER_GLOBAL_MIXIN = QUARANTINE + "seam.RenderGlobalTerrainMixin";

    private static final String UNPINNED = "0".repeat(64);

    @Test
    void aPinnedJarIsNotAudited() {
        String pin = QuarantineAnchors.generated().pins().iterator().next();
        QuarantineGuard.Verdict verdict = decide(name -> {
            throw new IOException("the audit must not read " + name);
        }, pin, Set.of(), Map.of());

        assertEquals(QuarantineGuard.Level.L0, verdict.level());
        assertTrue(verdict.failures().isEmpty());
        assertTrue(verdict.disabledMixins().isEmpty());
    }

    @Test
    void anUnpinnedJarWhoseAnchorsHoldKeepsEveryPatch() {
        QuarantineGuard.Verdict verdict = decide(new ChangedJar(), UNPINNED, Set.of(), Map.of());

        assertEquals(QuarantineGuard.Level.L0, verdict.level(), () -> verdict.failures().toString());
        assertTrue(verdict.disabledMixins().isEmpty());
        assertTrue(verdict.shadersAllowed());
    }

    @Test
    void aRenamedTerrainHookTurnsShadersOff() {
        QuarantineGuard.Verdict verdict = decide(new ChangedJar().renameMethod(CWR, "chooseVertexType", "pickVertexType"),
            UNPINNED, Set.of(), Map.of());

        assertEquals(QuarantineGuard.Level.L2, verdict.level());
        assertFalse(verdict.shadersAllowed());
        assertEquals(Set.of(PatchGroup.CORE_TERRAIN), verdict.failedGroups());
        assertEquals(mixinsOf(PatchGroup.CORE_TERRAIN), verdict.disabledMixins());
        String reason = verdict.shaderReason();
        assertNotNull(reason);
        assertTrue(reason.startsWith("Shaders are off") && reason.contains("S5") && reason.contains("chooseVertexType"), reason);
        // The other groups' anchors hold, so their patches still apply (inert without shaders).
        assertTrue(apply(verdict, QUARANTINE + "internal.SimpleWorldRendererMixin"));
    }

    @Test
    void aMissingShadowClassTurnsTerrainShadowsOff() {
        QuarantineGuard.Verdict verdict = decide(new ChangedJar().remove(ASYNC_OCCLUSION), UNPINNED, Set.of(), Map.of());

        assertEquals(QuarantineGuard.Level.L1, verdict.level());
        assertTrue(verdict.shadersAllowed());
        assertEquals(Set.of(PatchGroup.SHADOW), verdict.failedGroups());
        assertEquals(mixinsOf(PatchGroup.SHADOW), verdict.disabledMixins());
        assertNull(verdict.shaderReason());
        assertTrue(verdict.notices().stream().anyMatch(n -> n.startsWith("Terrain shadows are off") && n.contains("I1")
            && n.contains("class missing")), verdict.notices()::toString);
    }

    @Test
    void aMissingCallInsideAShadowTargetTurnsTerrainShadowsOff() {
        QuarantineGuard.Verdict verdict = decide(new ChangedJar().renameMethodAndCalls(DEFAULT_RENDERER, "useBlockFaceCulling", "faceCulling"),
            UNPINNED, Set.of(), Map.of());

        assertEquals(QuarantineGuard.Level.L1, verdict.level());
        assertTrue(verdict.notices().stream().anyMatch(n -> n.contains("S16") && n.contains("call missing")), verdict.notices()::toString);
    }

    @Test
    void theWorstLevelWins() {
        QuarantineGuard.Verdict verdict = decide(new ChangedJar().renameMethod(CWR, "chooseVertexType", "pickVertexType").remove(ASYNC_OCCLUSION),
            UNPINNED, Set.of(), Map.of());

        assertEquals(QuarantineGuard.Level.L2, verdict.level());
        assertEquals(Set.of(PatchGroup.CORE_TERRAIN, PatchGroup.SHADOW), verdict.failedGroups());
        Set<String> expected = new HashSet<>(mixinsOf(PatchGroup.CORE_TERRAIN));
        expected.addAll(mixinsOf(PatchGroup.SHADOW));
        assertEquals(expected, verdict.disabledMixins());
    }

    @Test
    void aFailedMeshingPatchKeepsShaders() {
        QuarantineGuard.Verdict verdict = decide(new ChangedJar().renameFieldAndAccesses(MESHING_TASK, "USE_NEW_BLOCK_RENDERER", "FAST_RENDERER"),
            UNPINNED, Set.of(), Map.of());

        assertEquals(QuarantineGuard.Level.L0, verdict.level());
        assertTrue(verdict.shadersAllowed());
        assertEquals(mixinsOf(PatchGroup.MESHING), verdict.disabledMixins());
        assertTrue(verdict.notices().stream().anyMatch(n -> n.contains("no block IDs") && n.contains("USE_NEW_BLOCK_RENDERER")),
            verdict.notices()::toString);
    }

    @Test
    void aDegradedPatchFailsAlone() {
        QuarantineGuard.Verdict verdict = decide(new ChangedJar().renameFieldAndAccesses(TRACKER, "requiredNeighborRadius", "neighborRadius"),
            UNPINNED, Set.of(), Map.of());

        assertEquals(QuarantineGuard.Level.L0, verdict.level());
        assertEquals(Set.of(TRACKER_MIXIN), verdict.disabledMixins());
        assertTrue(apply(verdict, GL_PROGRAM_MIXIN));
        assertTrue(verdict.notices().stream().anyMatch(n -> n.startsWith("S19 is off")), verdict.notices()::toString);
    }

    @Test
    void aCompatPatchFailsAlone() {
        QuarantineGuard.Verdict verdict = decide(new ChangedJar().renameMethod(SWR, "scheduleRebuildForChunk", "rebuildChunk"),
            UNPINNED, Set.of(), Map.of());

        assertEquals(QuarantineGuard.Level.L0, verdict.level(), () -> verdict.failures().toString());
        assertEquals(Set.of(COMPAT_MIXIN), verdict.disabledMixins());
    }

    @Test
    void aFogFailureIsReportedButNothingIsTurnedOff() {
        QuarantineGuard.Verdict verdict = decide(new ChangedJar().renameMethod(FOG_SERVICE, "getFogCutoff", "getFogLimit"), UNPINNED, Set.of(), Map.of());

        assertEquals(QuarantineGuard.Level.L0, verdict.level());
        assertEquals(Set.of(PatchGroup.BASE), verdict.failedGroups());
        assertTrue(verdict.disabledMixins().isEmpty());
        assertTrue(apply(verdict, FOG_MIXIN));
        assertTrue(verdict.notices().stream().anyMatch(n -> n.startsWith("Terrain fog") && n.contains("getFogCutoff")), verdict.notices()::toString);
    }

    @Test
    void anUnreadableClassFailsItsAnchorsWithoutThrowing() {
        QuarantineGuard.Verdict verdict = decide(new ChangedJar().corrupt(TRACKER), UNPINNED, Set.of(), Map.of());

        assertEquals(Set.of(TRACKER_MIXIN), verdict.disabledMixins());
        assertTrue(verdict.failures().stream().allMatch(f -> f.problem().startsWith("class unreadable")), verdict.failures()::toString);
    }

    @Test
    void aClassThatCannotBeReadFailsItsAnchorsWithoutThrowing() {
        QuarantineGuard.Verdict verdict = decide(new ChangedJar().failToRead(GL_PROGRAM), UNPINNED, Set.of(), Map.of());

        // GlProgram carries S17, and the pack's programs are linked through it (S2's helpers).
        assertEquals(QuarantineGuard.Level.L2, verdict.level());
        assertTrue(verdict.disabledMixins().contains(GL_PROGRAM_MIXIN));
        assertTrue(verdict.failures().stream().allMatch(f -> f.problem().startsWith("class unreadable")), verdict.failures()::toString);
    }

    @Test
    void aMissingAnchorListLeavesOnlyTheNonShaderPatches() {
        QuarantineGuard.Verdict verdict = QuarantineGuard.decide(new QuarantineGuard.Inputs(null, "the anchor list is missing", "celeritas.jar",
            UNPINNED, new ChangedJar(), Map.of(), Set.of(), false, false));

        assertEquals(QuarantineGuard.Level.L3, verdict.level());
        assertFalse(verdict.shadersAllowed());
        assertTrue(verdict.shaderReason().contains("the anchor list is missing"), verdict.shaderReason());
        Map<String, PatchGroup> groups = groups();
        for (Map.Entry<String, PatchGroup> mixin : groups.entrySet()) {
            boolean shaderGroup = Set.of(PatchGroup.CORE_TERRAIN, PatchGroup.SHADOW, PatchGroup.MESHING).contains(mixin.getValue());
            assertEquals(!shaderGroup, verdict.shouldApply(mixin.getKey(), groups::get), mixin.getKey());
        }
        // A mixin whose group cannot be read is left out.
        assertFalse(verdict.shouldApply(FOG_MIXIN, mixin -> null));
    }

    @Test
    void theLevel3DrillSetsTheAnchorListAside() {
        QuarantineGuard.Verdict verdict = decide(new ChangedJar(), UNPINNED, Set.of("L3"), Map.of());

        assertEquals(QuarantineGuard.Level.L3, verdict.level());
        assertTrue(verdict.shaderReason().contains(QuarantineGuard.DRILL_PROPERTY), verdict.shaderReason());
    }

    @Test
    void drillsTurnGroupsAndPatchesOffEvenOnThePin() {
        String pin = QuarantineAnchors.generated().pins().iterator().next();

        QuarantineGuard.Verdict shadow = decide(new ChangedJar(), pin, Set.of("SHADOW"), Map.of());
        assertEquals(QuarantineGuard.Level.L1, shadow.level());
        assertEquals(mixinsOf(PatchGroup.SHADOW), shadow.disabledMixins());
        assertTrue(shadow.failures().stream().allMatch(f -> f.problem().contains(QuarantineGuard.DRILL_PROPERTY)));

        QuarantineGuard.Verdict eyeMatrices = decide(new ChangedJar(), pin, Set.of("S6M"), Map.of());
        assertEquals(QuarantineGuard.Level.L2, eyeMatrices.level());
    }

    /**
     * In the distributed jar, the anchors that name vanilla members are resolved through the refmap, as Mixin resolves
     * the annotations: upstream's SRG-named overwrite of renderBlockLayer is found by its production name.
     */
    @Test
    void theRefmapResolvesVanillaNamesAsInProduction() {
        String layer = "renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I";
        ChangedJar production = new ChangedJar()
            .renameMethod(UPSTREAM_RENDER_GLOBAL, "renderBlockLayer", "func_174977_a")
            .renameCalls(UPSTREAM_RENDER_GLOBAL, "enableLightmap", "func_180436_i");

        QuarantineGuard.Verdict withoutRefmap = decide(production, UNPINNED, Set.of(), Map.of());
        assertEquals(QuarantineGuard.Level.L2, withoutRefmap.level());
        assertTrue(withoutRefmap.failures().stream().allMatch(f -> f.anchor().mixin().equals(RENDER_GLOBAL_MIXIN)), withoutRefmap.failures()::toString);

        Map<String, Map<String, String>> refmap = Map.of(RENDER_GLOBAL_MIXIN.replace('.', '/'), Map.of(
            layer, "Lnet/minecraft/client/renderer/RenderGlobal;" + layer.replace("renderBlockLayer", "func_174977_a"),
            "Lnet/minecraft/client/renderer/EntityRenderer;enableLightmap()V", "Lnet/minecraft/client/renderer/EntityRenderer;func_180436_i()V"));
        QuarantineGuard.Verdict withRefmap = decide(production, UNPINNED, Set.of(), refmap);
        assertEquals(QuarantineGuard.Level.L0, withRefmap.level(), () -> withRefmap.failures().toString());
    }

    private static QuarantineGuard.Verdict decide(AnchorAudit.ClassSource classes, String sha256, Set<String> drills,
                                                  Map<String, Map<String, String>> refmap) {
        return QuarantineGuard.decide(new QuarantineGuard.Inputs(QuarantineAnchors.generated(), null, "celeritas.jar", sha256, classes,
            refmap, drills, false, false));
    }

    private static boolean apply(QuarantineGuard.Verdict verdict, String mixin) {
        return verdict.shouldApply(mixin, groups()::get);
    }

    private static Map<String, PatchGroup> groups() {
        Map<String, PatchGroup> groups = new HashMap<>();
        QuarantineAnchors.generated().anchors().forEach(anchor -> groups.putIfAbsent(anchor.mixin(), anchor.group()));
        return groups;
    }

    private static Set<String> mixinsOf(PatchGroup group) {
        return QuarantineAnchors.generated().anchors().stream().filter(anchor -> anchor.group() == group).map(Anchor::mixin)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** The pinned jar's classes, some of them changed the way a later Celeritas build might change them. */
    private static final class ChangedJar implements AnchorAudit.ClassSource {
        private final CeleritasJar jar = CeleritasJar.get();
        private final Map<String, List<Consumer<ClassNode>>> changes = new HashMap<>();
        private final Set<String> removed = new HashSet<>();
        private final Set<String> corrupted = new HashSet<>();
        private final Set<String> unreadable = new HashSet<>();

        ChangedJar renameMethod(String owner, String name, String newName) {
            return change(owner, node -> node.methods.stream().filter(m -> m.name.equals(name)).forEach(m -> m.name = newName));
        }

        ChangedJar renameMethodAndCalls(String owner, String name, String newName) {
            return renameMethod(owner, name, newName).renameCalls(owner, name, newName);
        }

        ChangedJar renameCalls(String owner, String name, String newName) {
            return change(owner, node -> instructions(node).forEach(insn -> {
                if (insn instanceof MethodInsnNode call && call.name.equals(name)) {
                    call.name = newName;
                }
            }));
        }

        ChangedJar renameFieldAndAccesses(String owner, String name, String newName) {
            return change(owner, node -> {
                for (FieldNode field : node.fields) {
                    if (field.name.equals(name)) {
                        field.name = newName;
                    }
                }
            }).change(owner, node -> instructions(node).forEach(insn -> {
                if (insn instanceof FieldInsnNode access && access.owner.equals(owner) && access.name.equals(name)) {
                    access.name = newName;
                }
            }));
        }

        ChangedJar remove(String owner) {
            this.removed.add(owner);
            return this;
        }

        ChangedJar corrupt(String owner) {
            this.corrupted.add(owner);
            return this;
        }

        ChangedJar failToRead(String owner) {
            this.unreadable.add(owner);
            return this;
        }

        private ChangedJar change(String owner, Consumer<ClassNode> change) {
            this.changes.computeIfAbsent(owner, k -> new ArrayList<>()).add(change);
            return this;
        }

        private static List<AbstractInsnNode> instructions(ClassNode node) {
            List<AbstractInsnNode> instructions = new ArrayList<>();
            for (MethodNode method : node.methods) {
                if (method.instructions != null) {
                    method.instructions.forEach(instructions::add);
                }
            }
            return instructions;
        }

        @Override
        public byte[] bytes(String name) throws IOException {
            if (this.unreadable.contains(name)) {
                throw new IOException("drill: " + name + " cannot be read");
            }
            if (this.removed.contains(name) || !this.jar.contains(name)) {
                return null;
            }
            if (this.corrupted.contains(name)) {
                return new byte[] {(byte) 0xCA, (byte) 0xFE, 0x00};
            }
            byte[] bytes = this.jar.bytes(name);
            List<Consumer<ClassNode>> classChanges = this.changes.get(name);
            if (classChanges == null) {
                return bytes;
            }
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES);
            classChanges.forEach(change -> change.accept(node));
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            return writer.toByteArray();
        }
    }
}
