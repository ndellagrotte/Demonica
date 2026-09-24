package com.demonica.compat.ichunutil;

import org.embeddedt.embeddium.impl.render.viewport.ViewportProvider;

/**
 * Marks iChun's portal frustum as a Celeritas viewport source. Celeritas's {@code RenderGlobal.setupTerrain} casts every
 * camera to {@link ViewportProvider}; iChun's frustum implements {@code ICamera} directly, so without this the portal
 * render would fail with a {@code ClassCastException}.
 */
public interface PortalViewportProvider extends ViewportProvider {
}
