package net.irisshaders.iris.api.v0;

import net.coderbot.iris.apiimpl.IrisApiV0Impl;

public interface IrisApi {
    static IrisApi getInstance() {
        return IrisApiV0Impl.INSTANCE;
    }

    /**
     * Gets the minor revision of this API. This is incremented when
     * new methods are added without breaking API. Mods can check this
     * if they wish to check whether given API calls are available on
     * the currently installed Iris version.
     *
     * <p>Demonica: Demonica counts its own revisions, since it carries a subset
     * of upstream Iris's API: revision 2 adds
     * {@link #registerShadowRenderCallback} (upstream's API v0.4), revision 1 is
     * the methods before it.
     *
     * @return The current minor revision. Currently, revision 2.
     */
    int getMinorApiRevision();

    boolean isShaderPackInUse();

    boolean isRenderingShadowPass();

    Object openMainIrisScreenObj(Object parent);

    String getMainScreenLanguageKey();

    IrisApiConfig getConfig();

    /**
     * Registers a callback invoked during the shadow pass, after opaque terrain.
     * See {@link IrisShadowRenderCallback} for the GL state it runs in.
     *
     * @since Demonica API v0.2 (upstream Iris: API v0.4)
     */
    void registerShadowRenderCallback(IrisShadowRenderCallback callback);
}
