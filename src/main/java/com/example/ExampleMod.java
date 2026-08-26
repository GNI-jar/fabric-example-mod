package com.example;

import com.example.api.GigaChatAPI;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("gigachatai");

    @Override
    public void onInitialize() {
        LOGGER.info("GigaChat AI Mod загружается...");

        try {
            GigaChatAPI.authenticate();
        } catch (Exception e) {
            LOGGER.error("Не удалось авторизоваться в GigaChat!", e);
        }

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("ai")
                    .then(CommandManager.argument("вопрос", StringArgumentType.greedyString())
                            .executes(context -> {
                                ServerCommandSource source = context.getSource();
                                String question = StringArgumentType.getString(context, "вопрос");
                                
                                source.sendFeedback(() -> Text.literal("§b[ИИ] §7Думаю над вопросом..."), false);

                                GigaChatAPI.askGigaChat(question).thenAccept(answer -> {
                                    source.getServer().execute(() -> {
                                        source.sendFeedback(() -> Text.literal("§b[ИИ] §f" + answer), false);
                                    });
                                });

                                return 1;
                            })
                    )
            );
        });
    }
}
