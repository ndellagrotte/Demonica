package com.gtnewhorizons.angelica.glsm.debug;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * {@link TransformCorpus#run}, the process call behind the corpus recorder's git provenance: its timeout must hold
 * (S2 verification: the output was read to its end before the timed wait, so a hanging git blocked the recorder).
 */
class TransformCorpusTest {

    @Test
    void aCommandThatOutlivesItsTimeoutIsStopped(@TempDir Path directory) {
        assumeFalse(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows"), "uses sleep");
        final long start = System.nanoTime();
        assertNull(TransformCorpus.run(directory, 1, "sleep", "30"));
        final double seconds = (System.nanoTime() - start) / 1e9;
        assertTrue(seconds < 10, "returned after " + seconds + " s");
    }

    @Test
    void theOutputIsTrimmedAndAFailureIsNull(@TempDir Path directory) {
        assumeFalse(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows"), "uses sh");
        assertEquals("hello", TransformCorpus.run(directory, 10, "sh", "-c", "echo '  hello  '"));
        // More than a pipe buffer (64 KiB) of output.
        assertEquals(588895 - 1, TransformCorpus.run(directory, 10, "seq", "1", "100000").length());
        assertNull(TransformCorpus.run(directory, 10, "sh", "-c", "exit 3"));
        assertNull(TransformCorpus.run(directory, 10, "demonica-no-such-command"));
    }
}
