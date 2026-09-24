package net.coderbot.iris.celeritas;

import org.jetbrains.annotations.Nullable;

/**
 * Registration point for the world-terrain renderer used by the shadow pass.
 * The host mod registers its implementation at startup.
 */
public final class WorldRendererCompatBridge {

    /**
     * Supplies the current world renderer instance, or null when there is none or it cannot draw terrain into the
     * shadow map.
     */
    public interface Provider {
        @Nullable
        WorldRendererCompat instance();
    }

    private static volatile Provider provider;

    private WorldRendererCompatBridge() {
    }

    public static void setProvider(Provider newProvider) {
        provider = newProvider;
    }

    public static WorldRendererCompat instance() {
        Provider p = provider;
        if (p == null) {
            throw new IllegalStateException("WorldRendererCompat provider is not registered");
        }
        return p.instance();
    }

    /**
     * The world renderer, or null when no provider is registered or it has no renderer to offer; the shadow pass then
     * renders without terrain.
     */
    @Nullable
    public static WorldRendererCompat instanceNullable() {
        Provider p = provider;
        return p != null ? p.instance() : null;
    }
}
