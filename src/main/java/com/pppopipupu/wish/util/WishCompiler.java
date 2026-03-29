package com.pppopipupu.wish.util;

import com.pppopipupu.wish.Wish;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class WishCompiler {

    private static JavaCompiler compiler;
    private static StandardJavaFileManager fileManager;
    private static boolean fileManagerReady = false;

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

    private static boolean isValidLibrary(File f) {
        if (!f.isFile()) return false;
        String name = f.getName().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".jar")) return false;
        return !name.contains("native")
                && !name.contains("lwjgl")
                && !name.contains("oshi")
                && !name.contains("icu4j")
                && !name.contains("jline")
                && !name.contains("jopt-simple")
                && !name.contains("client")
                && !name.contains("realms")
                && !name.contains("openal")
                && !name.contains("sound")
                && !name.contains("text2speech")
                && !name.contains("glfw")
                && !name.contains("stb")
                && !name.contains("tinyfd");
    }

    private static final Set<String> scannedDirs = new HashSet<>();

    private static void scanJarDirectory(Set<String> paths, File dir) {
        if (!scannedDirs.add(dir.getAbsolutePath())) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (isValidLibrary(f)) {
                paths.add(f.getAbsolutePath());
            }
        }
    }

    private static void scanLibraries(Set<String> paths, File dir) {
        if (!dir.isDirectory() || !scannedDirs.add(dir.getAbsolutePath())) return;
        String dirName = dir.getName().toLowerCase(Locale.ROOT);
        if (dirName.contains("lwjgl") || dirName.contains("native") || 
            dirName.contains("client") || dirName.contains("realms") || 
            dirName.contains("openal") || dirName.contains("sound") || 
            dirName.contains("text2speech") || dirName.contains("glfw") || 
            dirName.contains("stb") || dirName.contains("tinyfd")) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                scanLibraries(paths, child);
            } else if (isValidLibrary(child)) {
                paths.add(child.getAbsolutePath());
            }
        }
    }

    private static void discoverLibraryRoot(Set<String> paths, File jarFile) {
        File dir = jarFile.getParentFile();
        while (dir != null) {
            if (dir.getName().equals("libraries")) {
                scanLibraries(paths, dir);
                return;
            }
            dir = dir.getParentFile();
        }
    }

    private static void initFileManager() {
        if (fileManagerReady) return;
        compiler = ToolProvider.getSystemJavaCompiler();
        fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);

        Set<String> pathSet = new LinkedHashSet<>();
        String sysCp = System.getProperty("java.class.path");
        if (sysCp != null && !sysCp.isEmpty()) {
            pathSet.addAll(Arrays.asList(sysCp.split(File.pathSeparator)));
        }
        for (Class<?> probe : PROBE_CLASSES) {
            addCodeSource(pathSet, probe);
        }
        for (String path : new LinkedHashSet<>(pathSet)) {
            File f = new File(path);
            if (f.isFile()) {
                discoverLibraryRoot(pathSet, f);
            }
        }
        try {
            ModList.get().forEachModFile(modFile -> {
                try {
                    Path modPath = modFile.getFilePath();
                    File f = modPath.toFile();
                    if (f.exists()) {
                        pathSet.add(f.getAbsolutePath());
                        if (f.isFile() && f.getParentFile() != null) {
                            scanJarDirectory(pathSet, f.getParentFile());
                        }
                    }
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}

        List<File> cpFiles = new ArrayList<>();
        for (String p : pathSet) {
            File f = new File(p);
            if (f.exists()) cpFiles.add(f);
        }
        try {
            fileManager.setLocation(StandardLocation.CLASS_PATH, cpFiles);
        } catch (Exception e) {
            Wish.LOGGER.error("WishCompiler setLocation failed", e);
        }
        fileManagerReady = true;
    }

    public static void eval(CommandSourceStack ctx, String code) {
        CompletableFuture.runAsync(() -> {
            try {
                initFileManager();

                File tempDir = Files.createTempDirectory("wish_eval").toFile();
                tempDir.deleteOnExit();

                String className = "WishEval_" + System.currentTimeMillis();
                String source = "package com.pppopipupu.wish.eval;\n\n" +
                        "import net.minecraft.core.BlockPos;\n" +
                        "import net.minecraft.world.level.block.Blocks;\n" +
                        "import net.minecraft.commands.CommandSourceStack;\n" +
                        "import net.minecraft.network.chat.Component;\n" +
                        "public class " + className + " {\n" +
                        "    public static void execute(CommandSourceStack ctx) throws Exception {\n" +
                        "        " + code + (code.endsWith(";") || code.endsWith("}") ? "" : ";") + "\n" +
                        "    }\n" +
                        "}\n";

                File sourceFile = new File(tempDir, className + ".java");
                Files.writeString(sourceFile.toPath(), source, StandardCharsets.UTF_8);

                fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(tempDir));

                Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjects(sourceFile);
                DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
                List<String> options = List.of("-encoding", "UTF-8");

                JavaCompiler.CompilationTask task = compiler.getTask(new StringWriter(), fileManager, diagnostics, options, null, units);
                boolean success = task.call();

                if (!success) {
                    StringBuilder sb = new StringBuilder();
                    diagnostics.getDiagnostics().forEach(d -> sb.append(d.toString()).append("\n"));
                    Wish.LOGGER.error(sb.toString());
                    ctx.getServer().execute(() -> {
                        ctx.getPlayer().addItem(Items.DIAMOND_BLOCK.getDefaultInstance());
                        ctx.sendFailure(Component.translatable("wish.compiler.compile_failed"));
                    });
                    return;
                }

                @SuppressWarnings("resource")
                URLClassLoader loader = new URLClassLoader(new URL[]{tempDir.toURI().toURL()}, WishCompiler.class.getClassLoader());
                Class<?> clazz = loader.loadClass("com.pppopipupu.wish.eval." + className);
                Method method = clazz.getMethod("execute", CommandSourceStack.class);

                ctx.getServer().execute(() -> {
                    try {
                        method.invoke(null, ctx);
                        ctx.sendSuccess(() -> Component.translatable("wish.compiler.execute_success"), false);
                    } catch (Exception e) {
                        ctx.getPlayer().addItem(Items.DIAMOND_BLOCK.getDefaultInstance());
                        ctx.sendFailure(Component.translatable("wish.compiler.runtime_error"));
                        Wish.LOGGER.error(e.getMessage());
                    }
                });
            } catch (Exception e) {
                ctx.getServer().execute(() -> {
                    ctx.getPlayer().addItem(Items.DIAMOND_BLOCK.getDefaultInstance());
                    ctx.sendFailure(Component.translatable("wish.compiler.runtime_error"));
                });
                Wish.LOGGER.error(e.getMessage());
            }
        });
    }
}
