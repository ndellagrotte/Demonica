package net.coderbot.iris.shaderpack.include;

import com.google.common.collect.ImmutableList;
import net.coderbot.iris.shaderpack.error.RusticError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Plan item 4.3: with debug options on, a {@code #include} whose path differs from a pack file only in case names the
 * on-disk spelling (upstream Iris's "did you mean" hint); folder packs only.
 */
class IncludeGraphCaseHintTest {
    private static final AbsolutePackPath COMPOSITE = AbsolutePackPath.fromAbsolutePath("/composite.fsh");

    @TempDir
    Path shaders;

    private void writePack(String includeLine) throws IOException {
        Files.createDirectories(shaders.resolve("lib"));
        Files.writeString(shaders.resolve("lib/common.glsl"), "const float commonValue = 1.0;\n");
        Files.writeString(shaders.resolve("composite.fsh"), "#version 120\n" + includeLine + "\nvoid main() {}\n");
    }

    private static String onlyFailure(IncludeGraph graph, String includedPath) {
        assertEquals(1, graph.getFailures().size(), graph.getFailures().toString());
        RusticError error = graph.getFailures().get(AbsolutePackPath.fromAbsolutePath(includedPath));
        assertNotNull(error, graph.getFailures().toString());
        return error.toString();
    }

    @Test
    void caseMismatchedFileNamesTheOnDiskPathWhenDebugOptionsAreOn() throws IOException {
        writePack("#include \"/lib/Common.glsl\"");

        IncludeGraph graph = new IncludeGraph(shaders, ImmutableList.of(COMPOSITE), true);

        String error = onlyFailure(graph, "/lib/Common.glsl");
        assertTrue(error.contains("failed to resolve #include directive\n"
            + "'/lib/Common.glsl' doesn't exist, did you mean 'lib/common.glsl'?"), error);
        assertTrue(error.contains("file not found"), error);
        assertTrue(error.contains("#include \"/lib/Common.glsl\""), error);
    }

    @Test
    void caseMismatchedDirectoryAndRelativeIncludeAreResolvedToo() throws IOException {
        writePack("#include \"LIB/COMMON.GLSL\"");

        IncludeGraph graph = new IncludeGraph(shaders, ImmutableList.of(COMPOSITE), true);

        String error = onlyFailure(graph, "/LIB/COMMON.GLSL");
        assertTrue(error.contains("'/LIB/COMMON.GLSL' doesn't exist, did you mean 'lib/common.glsl'?"), error);
    }

    @Test
    void exactIncludeLoadsWithDebugOptionsOn() throws IOException {
        writePack("#include \"/lib/common.glsl\"");

        IncludeGraph graph = new IncludeGraph(shaders, ImmutableList.of(COMPOSITE), true);

        assertTrue(graph.getFailures().isEmpty(), graph.getFailures().toString());
        assertTrue(graph.getNodes().containsKey(AbsolutePackPath.fromAbsolutePath("/lib/common.glsl")));
    }

    @Test
    void fileMissingInEveryCaseGetsNoHint() throws IOException {
        writePack("#include \"/lib/absent.glsl\"");

        IncludeGraph graph = new IncludeGraph(shaders, ImmutableList.of(COMPOSITE), true);

        String error = onlyFailure(graph, "/lib/absent.glsl");
        assertTrue(error.contains("failed to resolve #include directive"), error);
        assertFalse(error.contains("did you mean"), error);
    }

    @Test
    void noHintWhenDebugOptionsAreOff() throws IOException {
        // A case-insensitive file system would read the mismatched file and report nothing; the hint needs Linux-like
        // case sensitivity to show up as a failure without the gate.
        writePack("#include \"/lib/Common.glsl\"");
        assumeTrue(!Files.exists(shaders.resolve("lib/Common.glsl")), "case-insensitive temp file system");

        IncludeGraph graph = new IncludeGraph(shaders, ImmutableList.of(COMPOSITE), false);

        String error = onlyFailure(graph, "/lib/Common.glsl");
        assertTrue(error.contains("failed to resolve #include directive"), error);
        assertFalse(error.contains("did you mean"), error);
    }

    @Test
    void symlinkedIncludeIsNotReportedMissing() throws IOException {
        // Upstream's getCanonicalPath() comparison resolves symlinks and would flag this include; the listing walk does not.
        Path elsewhere = Files.createDirectories(shaders.resolve("../shared-lib"));
        Files.writeString(elsewhere.resolve("common.glsl"), "const float commonValue = 1.0;\n");
        Files.writeString(shaders.resolve("composite.fsh"), "#version 120\n#include \"/lib/common.glsl\"\nvoid main() {}\n");
        try {
            Files.createSymbolicLink(shaders.resolve("lib"), elsewhere.toRealPath());
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links unavailable: " + e);
        }

        IncludeGraph graph = new IncludeGraph(shaders, ImmutableList.of(COMPOSITE), true);

        assertTrue(graph.getFailures().isEmpty(), graph.getFailures().toString());
    }

    @Test
    void zipPackIsNotWalkedAndGetsNoHint(@TempDir Path zipDir) throws IOException {
        Path zip = zipDir.resolve("pack.zip");
        try (OutputStream out = Files.newOutputStream(zip); ZipOutputStream zos = new ZipOutputStream(out)) {
            zos.putNextEntry(new ZipEntry("shaders/composite.fsh"));
            zos.write("#version 120\n#include \"/lib/Common.glsl\"\nvoid main() {}\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("shaders/lib/common.glsl"));
            zos.write("const float commonValue = 1.0;\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        try (FileSystem fs = FileSystems.newFileSystem(zip, (ClassLoader) null)) {
            Path root = fs.getPath("shaders");

            IncludeGraph graph = new IncludeGraph(root, ImmutableList.of(COMPOSITE), true);

            String error = onlyFailure(graph, "/lib/Common.glsl");
            assertTrue(error.contains("failed to resolve #include directive"), error);
            assertFalse(error.contains("did you mean"), error);
        }
    }

    @Test
    void publicConstructorWithoutIrisConfigLeavesTheGateOff() throws IOException {
        // Iris.getIrisConfig() is null in unit tests; the public constructor must not throw and must not hint.
        writePack("#include \"/lib/Common.glsl\"");
        assumeTrue(!Files.exists(shaders.resolve("lib/Common.glsl")), "case-insensitive temp file system");

        IncludeGraph graph = new IncludeGraph(shaders, ImmutableList.of(COMPOSITE));

        assertFalse(onlyFailure(graph, "/lib/Common.glsl").contains("did you mean"));
    }
}
