package com.demonica.celeritas.guard;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each quarantine mixin's {@link Patch} (the ids and the group the guard turns off) matches docs/celeritas/LEDGER.md,
 * and every patch has its upstream draft in docs/celeritas/patches.
 */
class QuarantineLedgerTest {
    private static final String MIXIN_PACKAGE = "com.demonica.mixin.celeritas.";
    private static final Pattern PATCH_ROW = Pattern.compile("^\\| \\[([A-Za-z0-9]+)\\]\\(patches/\\1\\.md\\) \\|(.*)$");
    private static final Pattern MIXIN_NAME = Pattern.compile("`((?:internal|seam)\\.[A-Za-z]+)`");
    private static final Pattern GROUP_NAME = Pattern.compile("\\b(BASE|CORE_TERRAIN|SHADOW|MESHING|OPTIONS|DEGRADE|COMPAT)\\b");

    private record Row(String id, List<String> mixins, PatchGroup group) {
    }

    @Test
    void everyPatchMatchesItsLedgerRow() throws IOException {
        Map<String, PatchGroup> groups = new LinkedHashMap<>();
        Map<String, Set<String>> ids = new LinkedHashMap<>();
        for (Anchor anchor : QuarantineAnchors.generated().anchors()) {
            groups.putIfAbsent(anchor.mixin(), anchor.group());
            ids.putIfAbsent(anchor.mixin(), Set.of(anchor.patches().split(",")));
        }
        List<Row> rows = patchRows();
        assertFalse(rows.isEmpty(), "found no patch rows in LEDGER.md");

        List<String> problems = new ArrayList<>();
        Set<String> ledgerIds = new TreeSet<>();
        for (Row row : rows) {
            ledgerIds.add(row.id());
            for (String mixin : row.mixins()) {
                String name = MIXIN_PACKAGE + mixin;
                if (!ids.containsKey(name)) {
                    problems.add(row.id() + ": the ledger names " + mixin + ", which is not in the quarantine");
                } else if (!ids.get(name).contains(row.id())) {
                    problems.add(row.id() + ": " + mixin + "'s @Patch does not name it");
                } else if (groups.get(name) != row.group()) {
                    problems.add(row.id() + ": the ledger's group is " + row.group() + ", " + mixin + "'s @Patch says " + groups.get(name));
                }
            }
        }
        ids.forEach((mixin, mixinIds) -> mixinIds.stream().filter(id -> !ledgerIds.contains(id))
            .forEach(id -> problems.add(mixin + " carries " + id + ", which has no row in LEDGER.md")));
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /** The groups table lists each group's patches as the mixins' {@link Patch} declarations do. */
    @Test
    void theGroupsTableMatchesThePatches() throws IOException {
        Map<PatchGroup, Set<String>> declared = new LinkedHashMap<>();
        for (Anchor anchor : QuarantineAnchors.generated().anchors()) {
            declared.computeIfAbsent(anchor.group(), k -> new TreeSet<>()).addAll(List.of(anchor.patches().split(",")));
        }
        Map<PatchGroup, Set<String>> ledger = new LinkedHashMap<>();
        Pattern row = Pattern.compile("^\\| (BASE|CORE_TERRAIN|SHADOW|MESHING|OPTIONS|DEGRADE|COMPAT) \\| ([^|]+) \\|");
        for (String line : section("## Groups")) {
            Matcher matcher = row.matcher(line);
            if (matcher.find()) {
                Set<String> ids = new TreeSet<>();
                for (String id : matcher.group(2).split("[,+]")) {
                    ids.add(id.trim());
                }
                ledger.put(PatchGroup.valueOf(matcher.group(1)), ids);
            }
        }
        for (PatchGroup group : PatchGroup.values()) {
            assertEquals(declared.getOrDefault(group, Set.of()), ledger.getOrDefault(group, Set.of()), "LEDGER.md's " + group + " row");
        }
    }

    @Test
    void everyPatchHasAnUpstreamDraft() {
        Path patches = docs().resolve("patches");
        List<String> missing = new ArrayList<>();
        for (Anchor anchor : QuarantineAnchors.generated().anchors()) {
            for (String id : anchor.patches().split(",")) {
                if (!Files.isRegularFile(patches.resolve(id + ".md")) && !missing.contains(id)) {
                    missing.add(id);
                }
            }
        }
        assertTrue(missing.isEmpty(), "patches without a draft in docs/celeritas/patches: " + missing);
    }

    private static List<Row> patchRows() throws IOException {
        List<Row> rows = new ArrayList<>();
        for (String line : section("## Patches")) {
            Matcher row = PATCH_ROW.matcher(line);
            if (!row.find()) {
                continue;
            }
            String[] cells = row.group(2).split("\\|");
            List<String> mixins = new ArrayList<>();
            Matcher mixin = MIXIN_NAME.matcher(cells[0]);
            while (mixin.find()) {
                mixins.add(mixin.group(1));
            }
            Matcher group = GROUP_NAME.matcher(cells[3]);
            assertTrue(group.find(), row.group(1) + ": no group in \"" + cells[3].trim() + "\"");
            rows.add(new Row(row.group(1), mixins, PatchGroup.valueOf(group.group(1))));
        }
        return rows;
    }

    /** The lines of a {@code ##} section of LEDGER.md. */
    private static List<String> section(String heading) throws IOException {
        List<String> lines = Files.readAllLines(docs().resolve("LEDGER.md"));
        int start = lines.indexOf(heading);
        assertTrue(start >= 0, "LEDGER.md has no " + heading + " section");
        List<String> section = new ArrayList<>();
        for (int i = start + 1; i < lines.size() && !lines.get(i).startsWith("## "); i++) {
            section.add(lines.get(i));
        }
        return section;
    }

    private static Path docs() {
        return Path.of(System.getProperty("demonica.projectRoot", "."), "docs/celeritas");
    }
}
