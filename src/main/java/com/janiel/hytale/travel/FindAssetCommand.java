package com.janiel.hytale.travel;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class FindAssetCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> needleArg;

    public FindAssetCommand() {
        super("findasset", "Debug: searches base assets for a substring in file path or file contents (.json/.xml).");
        this.needleArg = withRequiredArg(
                "needle",
                "Substring to search (e.g. ShopPage, ShopElement, Forgotten_Temple_Portal_Enter)",
                ArgTypes.STRING
        );
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        String needleRaw = needleArg.get(context);
        if (needleRaw == null || needleRaw.trim().isEmpty()) {
            context.sendMessage(Message.raw("Usage: /findasset <needle>"));
            return;
        }

        final String needle = needleRaw.trim();
        final String needleLower = needle.toLowerCase();

        Path root = AssetModule.get().getBaseAssetPack().getRoot();
        context.sendMessage(Message.raw("Searching base assets for: " + needle));

        int[] found = new int[]{0};

        try (Stream<Path> paths = Files.walk(root)) {
            paths
                    .filter(p -> {
                        String s = p.toString().toLowerCase();
                        return s.endsWith(".json") || s.endsWith(".xml");
                    })
                    .limit(400000)
                    .forEach(p -> {
                        if (found[0] >= 20) {
                            return;
                        }
                        try {
                            String relPath = root.relativize(p).toString();
                            String relLower = relPath.toLowerCase();

                            // 1) Match by file path/name
                            if (relLower.contains(needleLower)) {
                                found[0]++;
                                context.sendMessage(Message.raw("FOUND (path): " + relPath));
                                return;
                            }

                            // 2) Match by file contents (skip huge files)
                            long size = Files.size(p);
                            if (size > 2_000_000L) {
                                return;
                            }

                            String text = Files.readString(p, StandardCharsets.UTF_8);
                            if (text.contains(needle)) {
                                found[0]++;
                                context.sendMessage(Message.raw("FOUND (content): " + relPath));
                            }
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException e) {
            context.sendMessage(Message.raw("findasset failed: " + e.getClass().getSimpleName()));
        }

        context.sendMessage(Message.raw("Done. Matches: " + found[0]));
    }
}
