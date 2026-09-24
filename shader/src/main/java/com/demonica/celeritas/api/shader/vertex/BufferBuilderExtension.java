package com.demonica.celeritas.api.shader.vertex;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface BufferBuilderExtension {
    void demonica$setActiveQuadContext(@Nullable VanillaQuadContext context);

    List<VanillaQuadContext> demonica$consumeQuadContexts();

    boolean demonica$isDrawing();

    void demonica$discard();
}
