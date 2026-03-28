package com.pppopipupu.wish.util;

import com.pppopipupu.wish.Wish;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.CodeSource;
import java.util.LinkedHashSet;
import java.util.Set;

public class WishCompiler {

    private static String resolvedClasspath = null;

    private static final Class<?>[] PROBE_CLASSES = {
            net.minecraft.world.level.block.Blocks.class,
            net.minecraft.commands.CommandSourceStack.class,
            net.minecraft.network.chat.Component.class,
            net.minecraft.world.item.Items.class,
            net.minecraft.world.entity.EntityType.class,
            net.minecraft.world.effect.MobEffects.class,
            net.minecraft.server.MinecraftServer.class,
            com.mojang.brigadier.Command.class,
            com.google.gson.JsonObject.class,
            WishCompiler.class,
    };

    private static void addCodeSource(Set<String> paths, Class<?> clazz) {
        try {
            CodeSource cs = clazz.getProtectionDomain().getCodeSource();
            if (cs != null && cs.getLocation() != null) {
                File f = new File(cs.getLocation().toURI());
                paths.add(f.getAbsolutePath());
                if (f.isFile() && f.getParentFile() != null) {
                    scanJarDirectory(paths, f.getParentFile());
                }
            }
        } catch (Exception ignored) {}

        try {
            String resourceName = clazz.getName().replace('.', '/') + ".class";
            URL resourceUrl = clazz.getResource("/" + resourceName);
            if (resourceUrl == null) {
                resourceUrl = clazz.getClassLoader().getResource(resourceName);
            }
            if (resourceUrl != null) {
                String urlStr = resourceUrl.toString();
                if (urlStr.startsWith("jar:file:")) {
                    String jarPath = urlStr.substring("jar:file:".length());
                    int bangIdx = jarPath.indexOf('!');
                    if (bangIdx > 0) {
                        jarPath = URLDecoder.decode(jarPath.substring(0, bangIdx), StandardCharsets.UTF_8);
                        File jarFile = new File(jarPath);
                        if (jarFile.exists()) {
                            paths.add(jarFile.getAbsolutePath());
                            if (jarFile.getParentFile() != null) {
                                scanJarDirectory(paths, jarFile.getParentFile());
                            }
                        }
                    }
                } else if (urlStr.startsWith("file:")) {
                    String filePath = URLDecoder.decode(urlStr.substring("file:".length()), StandardCharsets.UTF_8);
                    int classPathIdx = filePath.indexOf(resourceName);
                    if (classPathIdx > 0) {
                        paths.add(new File(filePath.substring(0, classPathIdx)).getAbsolutePath());
                    }
                } else if (urlStr.startsWith("union:")) {
                    String inner = urlStr.substring("union:".length());
                    int bangIdx = inner.indexOf('%');
                    if (bangIdx < 0) bangIdx = inner.indexOf('!');
                    if (bangIdx > 0) {
                        inner = inner.substring(0, bangIdx);
                    }
                    inner = URLDecoder.decode(inner, StandardCharsets.UTF_8);
                    File jarFile = new File(inner);
                    if (jarFile.exists()) {
                        paths.add(jarFile.getAbsolutePath());
                        if (jarFile.getParentFile() != null) {
                            scanJarDirectory(paths, jarFile.getParentFile());
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static void scanJarDirectory(Set<String> paths, File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isFile() && f.getName().endsWith(".jar")) {
                paths.add(f.getAbsolutePath());
            }
        }
    }

    private static String buildClasspath() {
        if (resolvedClasspath != null) {
            return resolvedClasspath;
        }
        Set<String> paths = new LinkedHashSet<>();
        String sysCp = System.getProperty("java.class.path");
        if (sysCp != null && !sysCp.isEmpty()) {
            for (String p : sysCp.split(File.pathSeparator)) {
                paths.add(p);
            }
        }
        for (Class<?> probe : PROBE_CLASSES) {
            addCodeSource(paths, probe);
        }
        resolvedClasspath = String.join(File.pathSeparator, paths);
        return resolvedClasspath;
    }

    public static void eval(CommandSourceStack ctx, String code) {
        try {
            File tempDir = Files.createTempDirectory("wish_eval").toFile();
            tempDir.deleteOnExit();

            String className = "WishEval_" + System.currentTimeMillis();
            String source = "package com.pppopipupu.wish.eval;\n\n" +
                    "import net.minecraft.core.BlockPos;\n" +
                    "import net.minecraft.world.level.block.Blocks;\n" +
                    "import net.minecraft.commands.CommandSourceStack;\n" +
                    "import net.minecraft.network.chat.Component;\n" +
                    "import net.minecraft.client.Minecraft;\n\n" +
                    "public class " + className + " {\n" +
                    "    public static void execute(CommandSourceStack ctx) throws Exception {\n" +
                    "        " + code + (code.endsWith(";") || code.endsWith("}") ? "" : ";") + "\n" +
                    "    }\n" +
                    "}\n";

            File sourceFile = new File(tempDir, className + ".java");
            Files.writeString(sourceFile.toPath(), source, StandardCharsets.UTF_8);

            ByteArrayOutputStream errObj = new ByteArrayOutputStream();
            int result = ToolProvider.getSystemJavaCompiler().run(null, null, errObj,
                    "-encoding", "UTF-8",
                    "-d", tempDir.getAbsolutePath(),
                    "-cp", buildClasspath(),
                    sourceFile.getAbsolutePath());

            if (result != 0) {
                ctx.getPlayer().addItem(Items.DIAMOND_BLOCK.getDefaultInstance());
                ctx.sendFailure(Component.translatable("wish.compiler.compile_failed"));
                Wish.LOGGER.error(errObj.toString());
                return;
            }

            try (URLClassLoader loader = new URLClassLoader(new URL[]{tempDir.toURI().toURL()}, WishCompiler.class.getClassLoader())) {
                Class<?> clazz = loader.loadClass("com.pppopipupu.wish.eval." + className);
                clazz.getMethod("execute", CommandSourceStack.class).invoke(null, ctx);
                ctx.sendSuccess(() -> Component.translatable("wish.compiler.execute_success"), false);
            }
        } catch (Exception e) {
            ctx.getPlayer().addItem(Items.DIAMOND_BLOCK.getDefaultInstance());
            ctx.sendFailure(Component.translatable("wish.compiler.runtime_error"));
            Wish.LOGGER.error(e.getMessage());
        }
    }
}
