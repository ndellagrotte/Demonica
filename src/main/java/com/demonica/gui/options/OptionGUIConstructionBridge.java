package com.demonica.gui.options;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.taumc.celeritas.api.OptionGUIConstructionEvent;
import org.taumc.celeritas.api.options.OptionIdentifier;
import org.taumc.celeritas.api.options.structure.OptionPage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Asks {@link OptionGUIConstructionEvent} listeners for their pages, as Celeritas's own video settings screen does,
 * and keeps the Reese's Sodium Options screen working when a listener misbehaves.
 *
 * <p>Celeritas's event bus runs the listeners in one loop: a listener that throws ends the loop, and later listeners
 * are not asked. What the listeners added until then is kept. Pages RSO cannot show are dropped with a warning: a
 * null page, a page without an id, and a second page with an id already taken.
 */
public final class OptionGUIConstructionBridge {
    private static final Logger LOGGER = LogManager.getLogger("Demonica-OptionHost");

    private OptionGUIConstructionBridge() {
    }

    /**
     * Posts the event with {@code builtInPages} as its starting list and returns the list the listeners left.
     *
     * @param post the event bus, {@code OptionGUIConstructionEvent.BUS::post} outside tests
     */
    public static List<OptionPage> collect(List<OptionPage> builtInPages, Consumer<OptionGUIConstructionEvent> post) {
        List<OptionPage> pages = new ArrayList<>(builtInPages);
        try {
            post.accept(new OptionGUIConstructionEvent(pages));
        } catch (RuntimeException e) {
            LOGGER.error("An OptionGUIConstructionEvent listener failed; listeners after it added no pages", e);
        }
        return sanitize(pages);
    }

    static List<OptionPage> sanitize(List<OptionPage> pages) {
        List<OptionPage> result = new ArrayList<>(pages.size());
        Set<OptionIdentifier<?>> ids = new HashSet<>();
        for (OptionPage page : pages) {
            if (page == null) {
                LOGGER.warn("Dropped a null option page added by an OptionGUIConstructionEvent listener");
            } else if (!hasStableId(page)) {
                LOGGER.warn("Dropped option page '{}' without an id", page.getName());
            } else if (!ids.add(page.getId())) {
                LOGGER.warn("Dropped a second option page with id {}", page.getId());
            } else {
                result.add(page);
            }
        }
        return result;
    }

    private static boolean hasStableId(OptionPage page) {
        OptionIdentifier<?> id = page.getId();
        return OptionIdentifier.isPresent(id) && !id.getModId().isBlank() && !id.getPath().isBlank();
    }
}
