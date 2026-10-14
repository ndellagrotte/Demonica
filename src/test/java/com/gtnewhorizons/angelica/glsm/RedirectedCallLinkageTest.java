package com.gtnewhorizons.angelica.glsm;

import com.demonica.celeritas.CeleritasJar;
import com.gtnewhorizons.angelica.loading.shared.transformers.AngelicaRedirector;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The game-side redirector ({@link AngelicaRedirector}, with its exclusions) rewrites GL calls by owner and name but
 * keeps the caller's descriptor, so each rewritten call must find a {@code GLStateManager} method with exactly that
 * descriptor, or the caller dies with {@code NoSuchMethodError} when it first runs. Cleanroom ships GL callers of its
 * own: the built-in Kirino renderer's {@code GLDeviceInfo.captureSnapshot} calls {@code GL30.glGetIntegeri_v(II[I)V},
 * and before the {@code int[]} overload existed every client crashed there. This runs the redirector over every class of
 * the Cleanroom userdev jar and of the pinned Celeritas jar on the test classpath and checks each rewritten call against
 * the compiled {@code GLStateManager} (which cannot be loaded without a GL context).
 */
class RedirectedCallLinkageTest {
    private static final String GL_STATE_MANAGER = "com/gtnewhorizons/angelica/glsm/GLStateManager";
    private static final String KIRINO_DEVICE_INFO = "com/cleanroommc/kirino/gl/GLDeviceInfo";

    @Test
    void everyRedirectedCleanroomCallLinks() throws IOException {
        File cleanroom = jarOf(KIRINO_DEVICE_INFO);
        Scan scan = new Scan();
        try (JarFile jar = new JarFile(cleanroom)) {
            for (JarEntry entry : Collections.list(jar.entries())) {
                String name = entry.getName();
                if (entry.isDirectory() || !name.endsWith(".class") || name.startsWith("META-INF/")) {
                    continue;
                }
                try (InputStream in = jar.getInputStream(entry)) {
                    scan.accept(name.substring(0, name.length() - ".class".length()), in.readAllBytes());
                }
            }
        }
        assertTrue(scan.rewritten.contains(KIRINO_DEVICE_INFO),
            "the redirector did not rewrite " + KIRINO_DEVICE_INFO + " in " + cleanroom.getName()
                + "; Kirino or the redirector changed shape and this test no longer covers the call it was written for");
        scan.assertLinks(cleanroom.getName());
    }

    @Test
    void everyRedirectedCeleritasCallLinks() {
        CeleritasJar jar = CeleritasJar.get();
        Scan scan = new Scan();
        for (String className : jar.classNames()) {
            scan.accept(className, jar.bytes(className));
        }
        assertTrue(!scan.rewritten.isEmpty(), "the redirector rewrote no Celeritas class; the jar or the redirector changed shape");
        scan.assertLinks(jar.file().getName());
    }

    /** The jar on the test classpath that holds {@code internalName}. */
    private static File jarOf(String internalName) throws IOException {
        URL url = RedirectedCallLinkageTest.class.getClassLoader().getResource(internalName + ".class");
        assertNotNull(url, internalName + ".class is not on the test classpath; the Cleanroom userdev jar should be");
        assertTrue(url.getProtocol().equals("jar"), internalName + " is not loaded from a jar: " + url);
        JarURLConnection connection = (JarURLConnection) url.openConnection();
        connection.setUseCaches(false);
        try {
            return new File(connection.getJarFileURL().toURI());
        } catch (java.net.URISyntaxException e) {
            throw new IOException(e);
        }
    }

    private static final class Scan {
        private static final Set<String> GLSM_METHODS = staticMethods(GL_STATE_MANAGER);

        private final AngelicaRedirector redirector = new AngelicaRedirector();
        private final Set<String> rewritten = new HashSet<>();
        private final Set<String> unresolved = new TreeSet<>();

        void accept(String internalName, byte[] bytes) {
            String transformedName = internalName.replace('/', '.');
            for (String exclusion : this.redirector.getTransformerExclusions()) {
                if (transformedName.startsWith(exclusion)) {
                    return;
                }
            }
            if (!this.redirector.shouldTransform(bytes)) {
                return;
            }
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            if (!this.redirector.transformClassNode(transformedName, node)) {
                return;
            }
            this.rewritten.add(internalName);
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode insn : method.instructions) {
                    if (insn instanceof MethodInsnNode call && call.owner.equals(GL_STATE_MANAGER)) {
                        if (!GLSM_METHODS.contains(call.name + call.desc)) {
                            this.unresolved.add(call.name + call.desc + " <- " + transformedName + "." + method.name);
                        }
                    } else if (insn instanceof InvokeDynamicInsnNode indy) {
                        for (Object arg : indy.bsmArgs) {
                            if (arg instanceof Handle handle && handle.getOwner().equals(GL_STATE_MANAGER)
                                && !GLSM_METHODS.contains(handle.getName() + handle.getDesc())) {
                                this.unresolved.add(handle.getName() + handle.getDesc() + " <- method reference in " + transformedName);
                            }
                        }
                    }
                }
            }
        }

        void assertLinks(String jarName) {
            assertTrue(this.unresolved.isEmpty(), "GLStateManager lacks the methods the redirector produces for " + jarName
                + " (each is a NoSuchMethodError at run time):\n  " + String.join("\n  ", this.unresolved));
        }

        private static Set<String> staticMethods(String internalName) {
            Set<String> methods = new HashSet<>();
            String current = internalName;
            while (current != null && !current.equals("java/lang/Object")) {
                ClassNode node = new ClassNode();
                try (InputStream in = RedirectedCallLinkageTest.class.getClassLoader().getResourceAsStream(current + ".class")) {
                    if (in == null) {
                        throw new IllegalStateException(current + ".class is not on the test classpath");
                    }
                    new ClassReader(in).accept(node, ClassReader.SKIP_CODE);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                for (MethodNode method : node.methods) {
                    if ((method.access & org.objectweb.asm.Opcodes.ACC_STATIC) != 0) {
                        methods.add(method.name + method.desc);
                    }
                }
                current = node.superName;
            }
            return methods;
        }
    }
}
