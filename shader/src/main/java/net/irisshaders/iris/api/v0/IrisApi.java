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
     * <p>Demonica: the number is upstream Iris's, so a mod that gates
     * {@link #registerShadowRenderCallback} on revision 4 finds it. Demonica
     * carries a subset of upstream's API: the methods upstream added in
     * revisions 1 to 3 (the text vertex sink, the sun path rotation and the
     * render pipeline assignment) are absent, so a caller must check for them
     * by name, not by revision.
     *
     * @return The current minor revision. Currently, revision 4.
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
     * @since API v0.4 (Demonica reports upstream's revision; see {@link #getMinorApiRevision()})
     */
    void registerShadowRenderCallback(IrisShadowRenderCallback callback);
}
