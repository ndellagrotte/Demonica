package net.irisshaders.iris.compat.sodium;

import net.coderbot.iris.gui.option.IrisVideoSettings;
import org.taumc.celeritas.api.options.structure.Option;
import org.taumc.celeritas.api.options.structure.OptionPage;
import org.embeddedt.embeddium.impl.gui.framework.TextComponent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrisConfigEntryPointTest {
    @Test
    void buildsShadowOptionPageAndAppliesInjectedBinding() {
        AtomicInteger persisted = new AtomicInteger(32);
        IrisConfigEntryPoint entryPoint = new IrisConfigEntryPoint(persisted::set, persisted::get);

        OptionPage videoSettings = entryPoint.createPage();

        assertEquals("iris", videoSettings.getId().getModId());
        assertEquals("video_settings", videoSettings.getId().getPath());
        assertTrue(videoSettings.getName() instanceof TextComponent.Translatable);
        assertEquals("options.iris.title", ((TextComponent.Translatable) videoSettings.getName()).keys().get(0));

        @SuppressWarnings("unchecked")
        Option<Integer> shadow = (Option<Integer>) videoSettings.getOptions().stream()
                .filter(option -> "shadow_distance".equals(option.getId().getPath()))
                .findFirst().orElseThrow();
        assertEquals("options.iris.shadowDistance",
                ((TextComponent.Translatable) shadow.getName()).keys().get(0));
        String expectedTooltipKey = IrisVideoSettings.isShadowDistanceSliderEnabled()
                ? "options.iris.shadowDistance.enabled"
                : "options.iris.shadowDistance.disabled";
        assertEquals(expectedTooltipKey,
                ((TextComponent.Translatable) shadow.getTooltip()).keys().get(0));

        shadow.setValue(64);
        shadow.applyChanges();
        assertEquals(64, persisted.get());
    }
}
