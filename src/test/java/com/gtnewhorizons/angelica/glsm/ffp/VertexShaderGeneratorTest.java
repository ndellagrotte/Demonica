package com.gtnewhorizons.angelica.glsm.ffp;

import io.github.douira.glsl_transformer.ast.node.abstract_node.ASTNode;
import io.github.douira.glsl_transformer.ast.node.declaration.DeclarationMember;
import io.github.douira.glsl_transformer.ast.node.expression.Expression;
import io.github.douira.glsl_transformer.ast.node.expression.binary.BinaryExpression;
import io.github.douira.glsl_transformer.ast.node.type.qualifier.StorageQualifier.StorageType;
import io.github.douira.glsl_transformer.ast.traversal.ASTVoidVisitor;
import net.coderbot.iris.pipeline.transform.GlslTokens;
import net.coderbot.iris.pipeline.transform.transformer.ShaderAst;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VertexShaderGeneratorTest {
    private static final int BIT_HAS_VERTEX_TEX = 18;
    private static final int BIT_TEXTURE = 12;
    private static final int BIT_TEX_MATRIX = 37;
    private static final int BIT_TEXGEN_S = 23;
    private static final int BIT_TEXGEN_T = 26;
    private static final int BIT_TEXGEN_R = 29;
    private static final int BIT_UNIT2_TEX = 14;
    private static final int BIT_UNIT3_TEX = 15;
    private static final int BIT_UNIT23_UV_FROM_UNIT0 = 41;
    private static final int BIT_LINE_STIPPLE = 42;
    private static final int BIT_UNIT2_TEXMAT = 39;
    private static final int BIT_UNIT3_TEXMAT = 40;
    private static final int BIT_HAS_VERTEX_TEX2 = 43;
    private static final int BIT_HAS_VERTEX_TEX3 = 44;

    @Test
    void lineStippleEmitsFlatLineStartVaryingAndViewportUniform() {
        String shader = VertexShaderGenerator.generate(VertexKey.fromPacked(1L << BIT_LINE_STIPPLE));

        assertTrue(shader.contains("flat out vec2 v_LineStart;"), shader);
        assertTrue(shader.contains("uniform vec4 u_Viewport;"), shader);
        assertTrue(shader.contains("v_LineStart = u_Viewport.xy + (ndc * 0.5 + 0.5) * u_Viewport.zw;"), shader);
    }

    @Test
    void units2And3EmitOwnVaryingsFedByCurrentTexCoordUniforms() {
        String shader = VertexShaderGenerator.generate(VertexKey.fromPacked((1L << BIT_UNIT2_TEX) | (1L << BIT_UNIT3_TEX)));

        assertTrue(shader.contains("out vec4 v_TexCoord2;"), shader);
        assertTrue(shader.contains("out vec4 v_TexCoord3;"), shader);
        assertTrue(shader.contains("uniform vec4 u_CurrentTexCoord2;"), shader);
        assertTrue(shader.contains("uniform vec4 u_CurrentTexCoord3;"), shader);
        assertTrue(shader.contains("v_TexCoord2 = u_CurrentTexCoord2;"), shader);
        assertTrue(shader.contains("v_TexCoord3 = u_CurrentTexCoord3;"), shader);
    }

    @Test
    void unit23UvFromUnit0SourcesVaryingsFromUnit0Attribute() {
        final long packed = (1L << BIT_UNIT23_UV_FROM_UNIT0) | (1L << BIT_HAS_VERTEX_TEX) | (1L << BIT_UNIT2_TEX);
        String shader = VertexShaderGenerator.generate(VertexKey.fromPacked(packed));

        assertTrue(shader.contains("v_TexCoord2 = a_TexCoord0;"), shader);
        assertFalse(shader.contains("u_CurrentTexCoord2"), shader);
    }

    @Test
    void units2And3WithPerVertexTexcoordsUseDedicatedAttributes() {
        final long packed = (1L << BIT_UNIT2_TEX) | (1L << BIT_UNIT3_TEX)
            | (1L << BIT_HAS_VERTEX_TEX2) | (1L << BIT_HAS_VERTEX_TEX3);
        String shader = VertexShaderGenerator.generate(VertexKey.fromPacked(packed));

        // Each unit gets its own attribute slot (locations 5 and 6) instead of a per-draw constant.
        assertTrue(shader.contains("layout(location = 5) in vec4 a_TexCoord2;"), shader);
        assertTrue(shader.contains("layout(location = 6) in vec4 a_TexCoord3;"), shader);
        assertTrue(shader.contains("v_TexCoord2 = a_TexCoord2;"), shader);
        assertTrue(shader.contains("v_TexCoord3 = a_TexCoord3;"), shader);
        assertFalse(shader.contains("u_CurrentTexCoord2"), shader);
        assertFalse(shader.contains("u_CurrentTexCoord3"), shader);
    }

    @Test
    void unit2PerVertexTexCoordMultipliesTextureMatrix() {
        final long packed = (1L << BIT_UNIT2_TEX) | (1L << BIT_UNIT2_TEXMAT) | (1L << BIT_HAS_VERTEX_TEX2);
        String shader = VertexShaderGenerator.generate(VertexKey.fromPacked(packed));

        assertTrue(shader.contains("uniform mat4 u_TextureMatrix2;"), shader);
        assertTrue(shader.contains("v_TexCoord2 = u_TextureMatrix2 * a_TexCoord2;"), shader);
    }

    @Test
    void extendedUnitAttributesAreHomogeneousVec4() {
        // A 2-float UV attribute must still yield q == 1.0: declaring a vec4 and reading it directly
        // lets OpenGL fill the missing z/w with 0/1 (the fragment stage divides coord.st / q).
        final long packed = (1L << BIT_UNIT3_TEX) | (1L << BIT_HAS_VERTEX_TEX3);
        VertexKey key = VertexKey.fromPacked(packed);
        String shader = VertexShaderGenerator.generate(key);
        ShaderAst ast = ShaderAst.parse(shader);
        Map<String, ShaderAst.QualifiedDeclaration> inputs = ast.findQualifiers(StorageType.IN);

        assertVec4(inputs, "a_TexCoord3", shader);
    }

    @Test
    void primaryTextureAttributeAcceptsCompleteHomogeneousCoordinates() {
        VertexKey key = VertexKey.fromPacked(1L << BIT_HAS_VERTEX_TEX);
        String shader = VertexShaderGenerator.generate(key);
        ShaderAst ast = ShaderAst.parse(shader);
        Map<String, ShaderAst.QualifiedDeclaration> inputs = ast.findQualifiers(StorageType.IN);
        List<BinaryExpression> assignments = assignmentsTo(ast, "v_TexCoord0");

        assertVec4(inputs, "a_TexCoord0", shader);
        assertEquals(1, assignments.size(), shader);
        assertEquals(List.of("a_TexCoord0"), GlslTokens.of(ShaderAst.text(assignments.get(0).getRight())).tokens(), shader);
        assertTrue(GlslTokens.contains(shader, "v_TexCoord0 = a_TexCoord0 ;"), shader);

        // The program survives a print and a second parse, which fails on any syntax error.
        String printed = ast.print("#version 330 core\n");
        ShaderAst reparsed = ShaderAst.parse(printed);
        assertVec4(reparsed.findQualifiers(StorageType.IN), "a_TexCoord0", printed);
        assertEquals(1, assignmentsTo(reparsed, "v_TexCoord0").size(), printed);
        assertTrue(GlslTokens.contains(printed, "v_TexCoord0 = a_TexCoord0 ;"), printed);
    }

    @Test
    void eyeLinearTexGenFeedsEveryPlaneFromSingleConstructor() {
        final long packed = (1L << BIT_TEXTURE) | (1L << BIT_TEX_MATRIX)
            | ((long) VertexKey.TG_EYE_LINEAR << BIT_TEXGEN_S)
            | ((long) VertexKey.TG_EYE_LINEAR << BIT_TEXGEN_T)
            | ((long) VertexKey.TG_EYE_LINEAR << BIT_TEXGEN_R);
        final String shader = VertexShaderGenerator.generate(VertexKey.fromPacked(packed));
        final ShaderAst ast = ShaderAst.parse(shader);
        final Map<String, ShaderAst.QualifiedDeclaration> uniforms = ast.findQualifiers(StorageType.UNIFORM);

        assertVec4(uniforms, "u_TexGenEyePlaneS", shader);
        assertVec4(uniforms, "u_TexGenEyePlaneT", shader);
        assertVec4(uniforms, "u_TexGenEyePlaneR", shader);

        // Every eye plane must be consumed by the single vec4 constructor. The previous
        // per-component writes to a pre-initialized texGenCoord let NVIDIA's driver
        // dead-code-eliminate u_TexGenEyePlaneS, collapsing BPR's end-portal starfield
        // into stripes.
        assertFalse(ast.hasAssignment("texGenCoord."), shader);
        final List<DeclarationMember> initialized = new ArrayList<>();
        ast.root.nodeIndex.getStream(DeclarationMember.class)
            .filter(member -> "texGenCoord".equals(member.getName().getName()) && member.getInitializer() != null)
            .forEach(initialized::add);
        assertEquals(1, initialized.size(), shader);
        final GlslTokens initializer = GlslTokens.of(ShaderAst.text(initialized.get(0).getInitializer()));
        assertEquals(List.of("vec4", "("), initializer.tokens().subList(0, 2), initializer.text());
        assertTrue(initializer.contains("dot ( eyePos , u_TexGenEyePlaneS )"), initializer.text());
        assertTrue(initializer.contains("dot ( eyePos , u_TexGenEyePlaneT )"), initializer.text());
        assertTrue(initializer.contains("dot ( eyePos , u_TexGenEyePlaneR )"), initializer.text());
    }

    /** A declaration named {@code name} with the storage qualifier queried, of the plain type {@code vec4}. */
    private static void assertVec4(Map<String, ShaderAst.QualifiedDeclaration> declarations, String name, String shader) {
        final ShaderAst.QualifiedDeclaration declaration = declarations.get(name);
        assertNotNull(declaration, name + " not declared in\n" + shader);
        assertEquals("vec4", declaration.typeName(), shader);
        assertNull(declaration.arraySpecifierText(), shader);
        assertNull(declaration.member().getArraySpecifier(), shader);
    }

    private static final Set<Expression.ExpressionType> ASSIGNMENTS = EnumSet.of(Expression.ExpressionType.ASSIGNMENT,
        Expression.ExpressionType.MULTIPLICATION_ASSIGNMENT, Expression.ExpressionType.DIVISION_ASSIGNMENT,
        Expression.ExpressionType.MODULO_ASSIGNMENT, Expression.ExpressionType.ADDITION_ASSIGNMENT,
        Expression.ExpressionType.SUBTRACTION_ASSIGNMENT, Expression.ExpressionType.LEFT_SHIFT_ASSIGNMENT,
        Expression.ExpressionType.RIGHT_SHIFT_ASSIGNMENT, Expression.ExpressionType.BITWISE_AND_ASSIGNMENT,
        Expression.ExpressionType.BITWISE_XOR_ASSIGNMENT, Expression.ExpressionType.BITWISE_OR_ASSIGNMENT);

    /** Every assignment ({@code =}, {@code +=}, ...) whose left side is exactly the identifier {@code name}. */
    private static List<BinaryExpression> assignmentsTo(ShaderAst ast, String name) {
        final List<BinaryExpression> found = new ArrayList<>();
        new ASTVoidVisitor() {
            @Override
            public void visitVoid(ASTNode node) {
                if (node instanceof BinaryExpression binary && ASSIGNMENTS.contains(binary.getExpressionType())
                    && GlslTokens.of(ShaderAst.text(binary.getLeft())).tokens().equals(List.of(name))) {
                    found.add(binary);
                }
            }
        }.visit(ast.tree);
        return found;
    }
}
