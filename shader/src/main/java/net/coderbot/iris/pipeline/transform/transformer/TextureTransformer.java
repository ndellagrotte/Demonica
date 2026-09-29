package net.coderbot.iris.pipeline.transform.transformer;

import io.github.douira.glsl_transformer.ast.node.type.specifier.BuiltinFixedTypeSpecifier.BuiltinType;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.coderbot.iris.gl.texture.TextureType;
import net.coderbot.iris.helpers.Tri;
import net.coderbot.iris.pipeline.transform.parameter.Parameters;
import net.coderbot.iris.shaderpack.texture.TextureStage;

/**
 * Renames the samplers a pack's custom texture map ({@code texture.<stage>.<name>=...} in shaders.properties) binds for
 * the stage being patched, when the declared sampler kind matches the texture's. Ported from the TauMC engine's class of
 * the same name (Step 5 of docs/glsl-transformer_adoption/ADOPTION_PLAN.md): TauMC's {@code findType} returned the
 * sampler keyword's token, here it is a {@link ShaderAst.DeclaredType}; {@code null} stands for TauMC's 0.
 */
public final class TextureTransformer {
	private TextureTransformer() {
	}

	public static void transform(ShaderAst transformer, Parameters parameters) {
		final Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap = parameters.getTextureMap();
		if (textureMap == null || textureMap.isEmpty()) {
			return;
		}

		final TextureStage stage = parameters.getTextureStage();
		textureMap.forEach((key, replacement) -> {
			if (key.third() != stage) {
				return;
			}

			final String name = key.first();
			final ShaderAst.DeclaredType type = transformer.findType(name);
			if (type != null && isTypeValid(key.second(), type)) {
				transformer.rename(name, replacement);
			}
		});
	}

	private static boolean isTypeValid(TextureType expectedType, ShaderAst.DeclaredType extractedType) {
		return switch (expectedType) {
			case TEXTURE_1D -> extractedType.is(BuiltinType.SAMPLER1D) ||
				extractedType.is(BuiltinType.ISAMPLER1D) ||
				extractedType.is(BuiltinType.USAMPLER1D);
			case TEXTURE_RECTANGLE -> extractedType.is(BuiltinType.SAMPLER2DRECT) ||
				extractedType.is(BuiltinType.ISAMPLER2DRECT) ||
				extractedType.is(BuiltinType.USAMPLER2DRECT);
			case TEXTURE_2D -> extractedType.is(BuiltinType.SAMPLER2D) ||
				extractedType.is(BuiltinType.ISAMPLER2D) ||
				extractedType.is(BuiltinType.USAMPLER2D);
			case TEXTURE_3D -> extractedType.is(BuiltinType.SAMPLER3D) ||
				extractedType.is(BuiltinType.ISAMPLER3D) ||
				extractedType.is(BuiltinType.USAMPLER3D);
		};
	}
}
