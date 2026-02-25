package com.janiel.hytale.travel.commands;

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
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class FindAssetCommand extends AbstractPlayerCommand {

    private final RequiredArg<String> needleArg;
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

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

        // IMPORTANT:
        // - chatLimit: keep player chat readable
        // - logLimit: allow many results in server console for copy/paste
        final int chatLimit = 30;
        final int logLimit = 2000;

        Path root = AssetModule.get().getBaseAssetPack().getRoot();

        context.sendMessage(Message.raw("Searching base assets for: " + needle + " (logging to server console)"));
        LOGGER.atInfo().log("[findasset] Searching base assets for: " + needle);
        LOGGER.atInfo().log("[findasset] Root: " + root);

        int[] totalMatches = new int[]{0};
        int[] chatShown = new int[]{0};

        try (Stream<Path> paths = Files.walk(root)) {
            paths
                    .filter(p -> {
                        String s = p.toString().toLowerCase();
                        // Added .ui because we need to locate UI layouts
                        return s.endsWith(".json") || s.endsWith(".xml") || s.endsWith(".ui");
                    })
                    .limit(400000)
                    .forEach(p -> {
                        try {
                            String relPath = root.relativize(p).toString();
                            String relLower = relPath.toLowerCase();

                            boolean matched = false;
                            String matchedKind = null;

                            // 1) Match by file path/name
                            if (relLower.contains(needleLower)) {
                                matched = true;
                                matchedKind = "path";
                            } else {
                                // 2) Match by file contents (skip huge files)
                                long size = Files.size(p);
                                if (size <= 2_000_000L) {
                                    String text = Files.readString(p, StandardCharsets.UTF_8);
                                    if (text.contains(needle)) {
                                        matched = true;
                                        matchedKind = "content";
                                    }
                                }
                            }

                            if (!matched) {
                                return;
                            }

                            totalMatches[0]++;

                            // Log to server console for copy/paste
                            if (totalMatches[0] <= logLimit) {
                                LOGGER.atInfo().log("[findasset] FOUND (" + matchedKind + "): " + relPath);
                            } else if (totalMatches[0] == logLimit + 1) {
                                LOGGER.atInfo().log("[findasset] Log limit reached (" + logLimit + "). Suppressing additional log lines.");
                            }

                            // Show a smaller subset to player chat
                            if (chatShown[0] < chatLimit) {
                                chatShown[0]++;
                                context.sendMessage(Message.raw("FOUND (" + matchedKind + "): " + relPath));
                            } else if (chatShown[0] == chatLimit) {
                                chatShown[0]++;
                                context.sendMessage(Message.raw("Too many matches. See server console for full output."));
                            }

                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException e) {
            context.sendMessage(Message.raw("findasset failed: " + e.getClass().getSimpleName()));
            LOGGER.atWarning().log("[findasset] Failed: " + e.getClass().getSimpleName(), e);
            return;
        }

        context.sendMessage(Message.raw("Done. Matches: " + totalMatches[0]));
        LOGGER.atWarning().log("[findasset] Done. Matches: " + totalMatches[0]);
    }
}
