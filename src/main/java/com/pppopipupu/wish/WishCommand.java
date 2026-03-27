package com.pppopipupu.wish;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.pppopipupu.wish.util.WishCompiler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class WishCommand implements Command<CommandSourceStack> {


    @Override
    public int run(CommandContext<CommandSourceStack> context) {
        String inputText = StringArgumentType.getString(context, "wish_text");
        CommandSourceStack source = context.getSource();
        executeWish(source, inputText);
        return 1;
    }

    public static void executeWish(CommandSourceStack source, String inputText) {
        source.sendSystemMessage(Component.translatable("wish.command.thinking"));
        CompletableFuture.runAsync(() -> {
            try {
                JsonArray messages = new JsonArray();
                
                JsonObject systemMessage = new JsonObject();
                systemMessage.addProperty("role", "system");
                systemMessage.addProperty("content", Config.OPENAI_SYSTEM_PROMPT.get());
                messages.add(systemMessage);
                
                JsonObject userMessage = new JsonObject();
                userMessage.addProperty("role", "user");
                userMessage.addProperty("content", inputText);
                messages.add(userMessage);

                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", Config.OPENAI_MODEL.get());
                requestBody.add("messages", messages);

                String baseUrl = Config.OPENAI_BASE_URL.get();
                if (!baseUrl.endsWith("/")) {
                    baseUrl += "/";
                }
                String endpoint = baseUrl + "chat/completions";

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json; charset=utf-8")
                        .header("Authorization", "Bearer " + Config.OPENAI_API_KEY.get())
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString(), StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() != 200) {
                    throw new RuntimeException("API request failed: HTTP " + response.statusCode() + " - " + response.body());
                }

                JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();

                String generatedCode = responseJson.getAsJsonArray("choices")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content").getAsString();
                if (generatedCode.contains("```java")) {
                    generatedCode = generatedCode.substring(generatedCode.indexOf("```java") + 7);
                    if (generatedCode.contains("```")) {
                        generatedCode = generatedCode.substring(0, generatedCode.indexOf("```"));
                    }
                } else if (generatedCode.contains("```")) {
                    generatedCode = generatedCode.replaceAll("```", "");
                }

                final String finalCode = generatedCode.trim();

                source.getServer().execute(() -> {
                    source.sendSystemMessage(Component.translatable("wish.command.success"));
                    WishCompiler.eval(source, finalCode);
                });

            } catch (Exception e) {
                source.getServer().execute(() -> {
                    source.sendFailure(Component.translatable("wish.command.error"));
                });
                Wish.LOGGER.error("LLM", e);
            }
        });
    }
}
