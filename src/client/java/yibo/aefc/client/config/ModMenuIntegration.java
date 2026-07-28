package yibo.aefc.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            AefcConfig config = AefcConfig.get();

            ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("title.aefc.config"));

            ConfigEntryBuilder entry = builder.entryBuilder();
            ConfigCategory category = builder.getOrCreateCategory(
                Component.translatable("category.aefc.general"));

            category.addEntry(entry.startBooleanToggle(
                    Component.translatable("option.aefc.detectScreenOpen"),
                    config.detectWhenScreenOpen
                )
                                  .setDefaultValue(true)
                                  .setTooltip(Component.translatable("option.aefc.detectScreenOpen.tooltip"))
                                  .setSaveConsumer(v -> config.detectWhenScreenOpen = v)
                                  .build());

            category.addEntry(entry.startBooleanToggle(
                    Component.translatable("option.aefc.detectBehind"),
                    config.detectBehindPlayer
                )
                                  .setDefaultValue(true)
                                  .setTooltip(Component.translatable("option.aefc.detectBehind.tooltip"))
                                  .setSaveConsumer(v -> config.detectBehindPlayer = v)
                                  .build());

            category.addEntry(entry.startBooleanToggle(
                    Component.translatable("option.aefc.autoLookBack"),
                    config.autoLookBack
                )
                                  .setDefaultValue(true)
                                  .setTooltip(Component.translatable("option.aefc.autoLookBack.tooltip"))
                                  .setSaveConsumer(v -> config.autoLookBack = v)
                                  .build());

            category.addEntry(entry.startBooleanToggle(
                    Component.translatable("option.aefc.shieldBlock"),
                    config.shieldBlockWhenTrapped
                )
                                  .setDefaultValue(true)
                                  .setTooltip(Component.translatable("option.aefc.shieldBlock.tooltip"))
                                  .setSaveConsumer(v -> config.shieldBlockWhenTrapped = v)
                                  .build());

            category.addEntry(entry.startBooleanToggle(
                    Component.translatable("option.aefc.shieldBlockEscapeFails"),
                    config.shieldBlockWhenEscapeFails
                )
                                  .setDefaultValue(false)
                                  .setTooltip(
                                      Component.translatable("option.aefc.shieldBlockEscapeFails.tooltip")
                                          .append("\n")
                                          .append(
                                              Component.translatable(
                                                      "option.aefc.shieldBlockEscapeFails.tooltip.warning")
                                                  .withStyle(ChatFormatting.GOLD)
                                          )
                                  )
                                  .setSaveConsumer(v -> config.shieldBlockWhenEscapeFails = v)
                                  .build());

            category.addEntry(entry.startBooleanToggle(
                    Component.translatable("option.aefc.blockProtection"),
                    config.blockProtectionEnabled
                )
                                  .setDefaultValue(false)
                                  .setTooltip(Component.translatable("option.aefc.blockProtection.tooltip"))
                                  .setSaveConsumer(v -> config.blockProtectionEnabled = v)
                                  .build());

            category.addEntry(entry.startStrList(
                    Component.translatable("option.aefc.protectedBlocks"),
                    config.protectedBlocks
                )
                                  .setDefaultValue(() -> new ArrayList<>(List.of(
                                      "minecraft:chest",
                                      "minecraft:barrel"
                                  )))
                                  .setTooltip(Component.translatable("option.aefc.protectedBlocks.tooltip"))
                                  .setSaveConsumer(v -> {
                                      config.protectedBlocks.clear();
                                      config.protectedBlocks.addAll(v);
                                  })
                                  .build());

            category.addEntry(entry.startBooleanToggle(
                    Component.translatable("option.aefc.allowLavaProtection"),
                    config.allowLavaProtection
                )
                                  .setDefaultValue(false)
                                  .setTooltip(
                                      Component.translatable("option.aefc.allowLavaProtection.tooltip")
                                          .append("\n")
                                          .append(
                                              Component.translatable(
                                                      "option.aefc.allowLavaProtection.tooltip.warning")
                                                  .withStyle(ChatFormatting.GOLD)
                                          )
                                  )
                                  .setSaveConsumer(v -> config.allowLavaProtection = v)
                                  .build());

            builder.setSavingRunnable(config::save);
            return builder.build();
        };
    }
}
