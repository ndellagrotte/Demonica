package com.demonica.mixins;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MixinLateTest {
    @Test
    void selectsExactConfigsForLoadedMods() {
        assertEquals(Set.of(), Set.copyOf(MixinLate.configsFor(modId -> false, className -> false)));

        // The lumenized config is gated on the embedded bloom class, not on a mod id:
        // it loads when the class is present even with no matching mod, and stays off
        // when only the mod id matches but the class is absent.
        assertEquals(
            Set.of("mixins.demonica.lumenized.json"),
            Set.copyOf(MixinLate.configsFor(modId -> false, "gregtech.client.utils.BloomEffectUtil"::equals))
        );

        assertEquals(
            Set.of(),
            Set.copyOf(MixinLate.configsFor("lumenized"::equals, className -> false))
        );

        // Better Foliage (RLFoliage) ships its own mixin on Celeritas's meshing task; Demonica has no config for it.
        assertEquals(
            Set.of(),
            Set.copyOf(MixinLate.configsFor("betterfoliage"::equals, className -> false))
        );

        assertEquals(
            Set.of("mixins.demonica.distanthorizons.json"),
            Set.copyOf(MixinLate.configsFor("distanthorizons"::equals, className -> false))
        );

        assertEquals(
            Set.of("mixins.demonica.ccl.json"),
            Set.copyOf(MixinLate.configsFor("codechickenlib"::equals, className -> false))
        );

        assertEquals(
            Set.of("mixins.demonica.cofhcore.json"),
            Set.copyOf(MixinLate.configsFor("cofhcore"::equals, className -> false))
        );

        assertEquals(
            Set.of(
                "mixins.demonica.gibbed.json",
                "mixins.demonica.ichunutil.json",
                "mixins.demonica.lumenized.json",
                "mixins.demonica.revoui.json",
                "mixins.demonica.ccl.json",
                "mixins.demonica.voxelmap.json",
                "mixins.demonica.extrautils2.json",
                "mixins.demonica.cofhcore.json",
                "mixins.demonica.oldresearch.json",
                "mixins.demonica.botania.json",
                "mixins.demonica.hbm.json",
                "mixins.demonica.scannable.json",
                "mixins.demonica.littletiles.json",
                "mixins.demonica.obscuretooltips.json",
                "mixins.demonica.distanthorizons.json"
            ),
            Set.copyOf(MixinLate.configsFor(modId -> true, className -> true))
        );
    }
}
