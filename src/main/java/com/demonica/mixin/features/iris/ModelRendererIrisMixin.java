package com.demonica.mixin.features.iris;

import com.demonica.config.DemonicaRuntimeOptions;
import com.demonica.compat.gibbed.DemonicaModelRenderer;
import com.gtnewhorizons.angelica.mixins.interfaces.IModelRenderer;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.PositionTextureVertex;
import net.minecraft.client.model.TexturedQuad;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ModelRenderer.class)
public abstract class ModelRendererIrisMixin implements IModelRenderer, DemonicaModelRenderer {
    @Shadow public boolean isHidden;
    @Shadow public boolean showModel;
    @Shadow public float offsetX;
    @Shadow public float offsetY;
    @Shadow public float offsetZ;
    @Shadow public float rotationPointX;
    @Shadow public float rotationPointY;
    @Shadow public float rotationPointZ;
    @Shadow public float rotateAngleX;
    @Shadow public float rotateAngleY;
    @Shadow public float rotateAngleZ;
    @Shadow public List<ModelBox> cubeList;
    @Shadow public List<ModelRenderer> childModels;

    @Unique
    private int demonica$displayList;
    @Unique
    private int demonica$displayListScaleBits;

    @Inject(method = "render(F)V", at = @At("HEAD"), cancellable = true)
    private void demonica$renderWithoutDisplayList(float scale, CallbackInfo ci) {
        if (IrisApi.getInstance().isShaderPackInUse() || !DemonicaRuntimeOptions.useModelRendererBatching()) {
            return;
        }

        ci.cancel();
        if (this.isHidden || !this.showModel) {
            return;
        }

        GlStateManager.translate(this.offsetX, this.offsetY, this.offsetZ);

        if (this.rotateAngleX != 0.0F || this.rotateAngleY != 0.0F || this.rotateAngleZ != 0.0F) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(this.rotationPointX * scale, this.rotationPointY * scale, this.rotationPointZ * scale);
            this.demonica$applyRotation();
            this.demonica$drawModel(scale);
            this.demonica$renderChildren(scale);
            GlStateManager.popMatrix();
        } else if (this.rotationPointX == 0.0F && this.rotationPointY == 0.0F && this.rotationPointZ == 0.0F) {
            this.demonica$drawModel(scale);
            this.demonica$renderChildren(scale);
        } else {
            GlStateManager.translate(this.rotationPointX * scale, this.rotationPointY * scale, this.rotationPointZ * scale);
            this.demonica$drawModel(scale);
            this.demonica$renderChildren(scale);
            GlStateManager.translate(-this.rotationPointX * scale, -this.rotationPointY * scale, -this.rotationPointZ * scale);
        }

        GlStateManager.translate(-this.offsetX, -this.offsetY, -this.offsetZ);
    }

    @Inject(method = "renderWithRotation(F)V", at = @At("HEAD"), cancellable = true)
    private void demonica$renderWithRotationWithoutDisplayList(float scale, CallbackInfo ci) {
        if (IrisApi.getInstance().isShaderPackInUse() || !DemonicaRuntimeOptions.useModelRendererBatching()) {
            return;
        }

        ci.cancel();
        if (this.isHidden || !this.showModel) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(this.rotationPointX * scale, this.rotationPointY * scale, this.rotationPointZ * scale);

        if (this.rotateAngleY != 0.0F) {
            GlStateManager.rotate(this.rotateAngleY * (180.0F / (float) Math.PI), 0.0F, 1.0F, 0.0F);
        }

        if (this.rotateAngleX != 0.0F) {
            GlStateManager.rotate(this.rotateAngleX * (180.0F / (float) Math.PI), 1.0F, 0.0F, 0.0F);
        }

        if (this.rotateAngleZ != 0.0F) {
            GlStateManager.rotate(this.rotateAngleZ * (180.0F / (float) Math.PI), 0.0F, 0.0F, 1.0F);
        }

        this.demonica$drawModel(scale);
        GlStateManager.popMatrix();
    }

    @Override
    public void angelica$resetDisplayList() {
        this.demonica$resetDisplayList();
    }

    @Override
    public void demonica$renderGibbedSingleModelPart(float scale) {
        if (this.isHidden || !this.showModel) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(this.rotationPointX * scale, this.rotationPointY * scale, this.rotationPointZ * scale);

        if (this.rotateAngleY != 0.0F) {
            GlStateManager.rotate(this.rotateAngleY * (180.0F / (float) Math.PI), 0.0F, 1.0F, 0.0F);
        }

        if (this.rotateAngleX != 0.0F) {
            GlStateManager.rotate(this.rotateAngleX * (180.0F / (float) Math.PI), 1.0F, 0.0F, 0.0F);
        }

        if (this.rotateAngleZ != 0.0F) {
            GlStateManager.rotate(this.rotateAngleZ * (180.0F / (float) Math.PI), 0.0F, 0.0F, 1.0F);
        }

        this.demonica$drawModel(scale, false);
        GlStateManager.popMatrix();
    }

    @Override
    public void demonica$renderGibbedModel(float scale) {
        if (this.isHidden || !this.showModel) {
            return;
        }

        GlStateManager.translate(this.offsetX, this.offsetY, this.offsetZ);

        if (this.rotateAngleX != 0.0F || this.rotateAngleY != 0.0F || this.rotateAngleZ != 0.0F) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(this.rotationPointX * scale, this.rotationPointY * scale, this.rotationPointZ * scale);
            this.demonica$applyRotation();
            this.demonica$drawModel(scale, false);
            this.demonica$renderGibbedChildren(scale);
            GlStateManager.popMatrix();
        } else if (this.rotationPointX == 0.0F && this.rotationPointY == 0.0F && this.rotationPointZ == 0.0F) {
            this.demonica$drawModel(scale, false);
            this.demonica$renderGibbedChildren(scale);
        } else {
            GlStateManager.translate(this.rotationPointX * scale, this.rotationPointY * scale, this.rotationPointZ * scale);
            this.demonica$drawModel(scale, false);
            this.demonica$renderGibbedChildren(scale);
            GlStateManager.translate(-this.rotationPointX * scale, -this.rotationPointY * scale, -this.rotationPointZ * scale);
        }

        GlStateManager.translate(-this.offsetX, -this.offsetY, -this.offsetZ);
    }

    private void demonica$applyRotation() {
        if (this.rotateAngleZ != 0.0F) {
            GlStateManager.rotate(this.rotateAngleZ * (180.0F / (float) Math.PI), 0.0F, 0.0F, 1.0F);
        }

        if (this.rotateAngleY != 0.0F) {
            GlStateManager.rotate(this.rotateAngleY * (180.0F / (float) Math.PI), 0.0F, 1.0F, 0.0F);
        }

        if (this.rotateAngleX != 0.0F) {
            GlStateManager.rotate(this.rotateAngleX * (180.0F / (float) Math.PI), 1.0F, 0.0F, 0.0F);
        }
    }

    private void demonica$drawModel(float scale) {
        this.demonica$drawModel(scale, true);
    }

    @Unique
    private void demonica$drawModel(float scale, boolean allowDisplayLists) {
        if (this.cubeList.isEmpty()) {
            return;
        }

        // Batched model formats do not carry per-vertex color, so they inherit the managed current color state.
        // Refreshing it here keeps the default color attribute and u_CurrentColor in sync with vanilla-style model draws.
        if (DemonicaRuntimeOptions.useModelRendererBatching()) {
            final var color = com.gtnewhorizons.angelica.glsm.GLStateManager.getColor();
            if (color.getRed() < 0.0F || color.getGreen() < 0.0F || color.getBlue() < 0.0F || color.getAlpha() < 0.0F) {
                com.gtnewhorizons.angelica.glsm.GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            } else {
                com.gtnewhorizons.angelica.glsm.GLStateManager.glColor4f(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
            }
        }

        boolean batchModelQuads = DemonicaRuntimeOptions.useModelRendererBatching();
        boolean useDisplayLists = allowDisplayLists && batchModelQuads && DemonicaRuntimeOptions.useModelRendererDisplayLists();

        if (useDisplayLists) {
            int scaleBits = Float.floatToIntBits(scale);
            if (this.demonica$displayList != 0 && this.demonica$displayListScaleBits != scaleBits) {
                this.demonica$resetDisplayList();
            }

            if (this.demonica$displayList == 0) {
                this.demonica$compileDisplayList(scale, scaleBits);
            }

            com.gtnewhorizons.angelica.glsm.GLStateManager.glCallList(this.demonica$displayList);
            return;
        } else if (allowDisplayLists) {
            this.demonica$resetDisplayList();
        }

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        if (batchModelQuads) {
            buffer.begin(7, DefaultVertexFormats.OLDMODEL_POSITION_TEX_NORMAL);
            for (ModelBox cube : this.cubeList) {
                for (TexturedQuad quad : ((ModelBoxAccessor) cube).demonica$getQuadList()) {
                    demonica$appendQuad(buffer, quad, scale);
                }
            }
            tessellator.draw();
            return;
        }

        for (ModelBox cube : this.cubeList) {
            cube.render(buffer, scale);
        }
    }

    @Unique
    private void demonica$compileDisplayList(float scale, int scaleBits) {
        int list = com.gtnewhorizons.angelica.glsm.GLStateManager.glGenLists(1);
        if (list == 0) {
            throw new IllegalStateException("glGenLists returned 0 while compiling a ModelRenderer display list");
        }

        com.gtnewhorizons.angelica.glsm.GLStateManager.glNewList(list, GL11.GL_COMPILE);

        try {
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();

            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.OLDMODEL_POSITION_TEX_NORMAL);
            for (ModelBox cube : this.cubeList) {
                for (TexturedQuad quad : ((ModelBoxAccessor) cube).demonica$getQuadList()) {
                    demonica$appendQuad(buffer, quad, scale);
                }
            }
            tessellator.draw();
        } finally {
            com.gtnewhorizons.angelica.glsm.GLStateManager.glEndList();
        }

        this.demonica$displayList = list;
        this.demonica$displayListScaleBits = scaleBits;
    }

    @Unique
    private void demonica$resetDisplayList() {
        if (this.demonica$displayList > 0) {
            com.gtnewhorizons.angelica.glsm.GLStateManager.glDeleteLists(this.demonica$displayList, 1);
        }
        this.demonica$displayList = 0;
        this.demonica$displayListScaleBits = 0;
    }

    private static void demonica$appendQuad(BufferBuilder buffer, TexturedQuad quad, float scale) {
        Vec3d vec3d = quad.vertexPositions[1].vector3D.subtractReverse(quad.vertexPositions[0].vector3D);
        Vec3d vec3d1 = quad.vertexPositions[1].vector3D.subtractReverse(quad.vertexPositions[2].vector3D);
        Vec3d normal = vec3d1.crossProduct(vec3d).normalize();
        float normalX = (float) normal.x;
        float normalY = (float) normal.y;
        float normalZ = (float) normal.z;

        if (((TexturedQuadAccessor) quad).demonica$isInvertNormal()) {
            normalX = -normalX;
            normalY = -normalY;
            normalZ = -normalZ;
        }

        for (int i = 0; i < quad.nVertices; i++) {
            PositionTextureVertex vertex = quad.vertexPositions[i];
            buffer.pos(
                    vertex.vector3D.x * scale,
                    vertex.vector3D.y * scale,
                    vertex.vector3D.z * scale
                )
                .tex(vertex.texturePositionX, vertex.texturePositionY)
                .normal(normalX, normalY, normalZ)
                .endVertex();
        }
    }

    private void demonica$renderChildren(float scale) {
        if (this.childModels == null) {
            return;
        }

        for (ModelRenderer child : this.childModels) {
            child.render(scale);
        }
    }

    @Unique
    private void demonica$renderGibbedChildren(float scale) {
        if (this.childModels == null) {
            return;
        }

        for (ModelRenderer child : this.childModels) {
            if (child instanceof DemonicaModelRenderer) {
                ((DemonicaModelRenderer) child).demonica$renderGibbedModel(scale);
            } else {
                child.render(scale);
            }
        }
    }
}
