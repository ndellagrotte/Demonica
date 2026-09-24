package com.demonica.render.vertex;

import com.demonica.celeritas.api.shader.vertex.VanillaQuadContext;
import net.coderbot.iris.celeritas.buffer.ShaderMaterialOverrideState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The block context of each quad a {@code BufferBuilder} draws (BufferBuilderMixin), for the vanilla meshing path of
 * the shader passes: entry n belongs to quad n. Once a context has been attached, every finished quad gets an entry,
 * null for one drawn without a context, so geometry that other code draws between two blocks (Fluidlogged's fluids,
 * another mod's wrapper around the block) cannot shift the contexts of the quads after it.
 *
 * <p>A mod can override the ID of the quads it draws through {@code Iris.setShaderMaterialOverride}.
 */
public final class QuadContextRecorder {
    private final List<VanillaQuadContext> contexts = new ArrayList<>();
    private @Nullable VanillaQuadContext active;
    private boolean tracking;

    /** Forgets every context, as the buffer starts over. */
    public void clear() {
        this.contexts.clear();
        this.active = null;
        this.tracking = false;
    }

    /**
     * Attaches {@code context} (or none) to the quads drawn from now on. The quads finished so far keep the context
     * they were drawn with.
     *
     * @param quads how many quads the buffer holds now
     */
    public void setActive(@Nullable VanillaQuadContext context, int quads) {
        this.record(quads);
        this.tracking = true;
        this.active = context;
    }

    /** @param quads how many quads the buffer holds now that vertices were added */
    public void onVerticesAdded(int quads) {
        if (this.tracking) {
            this.record(quads);
        }
    }

    /**
     * The contexts of the buffer's quads, one per quad, which are then forgotten. Empty if no context was attached
     * since the buffer started.
     */
    public List<VanillaQuadContext> consume(int quads) {
        if (!this.tracking) {
            return List.of();
        }
        this.record(quads);
        List<VanillaQuadContext> consumed = new ArrayList<>(this.contexts);
        this.clear();
        return consumed;
    }

    private void record(int quads) {
        if (this.contexts.size() >= quads) {
            return;
        }
        VanillaQuadContext context = this.active;
        int overrideBlockId = ShaderMaterialOverrideState.getBlockId();
        if (context != null && overrideBlockId >= 0) {
            context = context.withBlockStateId(overrideBlockId);
        }
        while (this.contexts.size() < quads) {
            this.contexts.add(context);
        }
    }
}
