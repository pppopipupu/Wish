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
import java.nio.file.Files;

public class WishCompiler {

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
            Files.writeString(sourceFile.toPath(), source);

            ByteArrayOutputStream errObj = new ByteArrayOutputStream();
            int result = ToolProvider.getSystemJavaCompiler().run(null, null, errObj,
                    "-d", tempDir.getAbsolutePath(),
                    "-cp", System.getProperty("java.class.path"),
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
