package com.janiel.hytale.menu.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceBasePage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.janiel.hytale.mutations.ui.MutationsPage;

import javax.annotation.Nonnull;

public final class MainMenuPage extends ChoiceBasePage {

    private static final int TAB_INDEX_BASE = 1000;

    private final PlayerRef playerRef;
    private final Section activeSection;
    private final ChoiceElement[] tabElements;
    private final ChoiceElement[] sidebarElements;

    private enum Section {
        MUTATIONS,
        EQUIPMENT
    }

    private MainMenuPage(
            @Nonnull PlayerRef playerRef,
            @Nonnull Section activeSection,
            @Nonnull ChoiceElement[] contentElements,
            @Nonnull ChoiceElement[] tabElements,
            @Nonnull ChoiceElement[] sidebarElements
    ) {
        super(playerRef, contentElements, "Pages/JanielMenu/MainMenuHome.ui");
        this.playerRef = playerRef;
        this.activeSection = activeSection;
        this.tabElements = tabElements;
        this.sidebarElements = sidebarElements;
    }

    public static MainMenuPage create(@Nonnull PlayerRef playerRef) {
        return create(playerRef, Section.MUTATIONS);
    }

    public static MainMenuPage createEquipment(@Nonnull PlayerRef playerRef) {
        return create(playerRef, Section.EQUIPMENT);
    }

    private static MainMenuPage create(
            @Nonnull PlayerRef playerRef,
            @Nonnull Section activeSection
    ) {
        ChoiceElement[] tabElements = new ChoiceElement[] {
                new MenuTabElement("Mutations", activeSection == Section.MUTATIONS),
                new MenuTabElement("Equipment", activeSection == Section.EQUIPMENT)
        };

        ChoiceElement[] sidebarElements;
        ChoiceElement[] contentElements;

        if (activeSection == Section.MUTATIONS) {
            sidebarElements = new ChoiceElement[] {
                    new MenuEntryElement(
                            "Progression Overview",
                            "Review mining, stamina, and weapon mastery progress in a single persistent menu.",
                            "MUTATIONS",
                            "#SidebarList"
                    ),
                    new MenuEntryElement(
                            "Next Expansion",
                            "This menu will later connect progression with equipment bonuses, stat summaries, and inventory context.",
                            "ROADMAP",
                            "#SidebarList"
                    )
            };

            contentElements = MutationsPage.createElements(playerRef, "#ElementList");
        } else {
            sidebarElements = new ChoiceElement[] {
                    new MenuEntryElement(
                            "Player Preview Area",
                            "Reserved for the character preview, equipped armor silhouette, vanity display, and quick visual identity.",
                            "PREVIEW",
                            "#SidebarList"
                    ),
                    new MenuEntryElement(
                            "Stats Area",
                            "Reserved for derived combat stats, mutation bonuses, armor totals, utility modifiers, and resistances.",
                            "STATS",
                            "#SidebarList"
                    )
            };

            contentElements = new ChoiceElement[] {
                    new MenuEntryElement(
                            "Equipment Slots Area",
                            "Reserved for helmet, chest, legs, boots, utility slots, and other equipped items tied to the real player inventory.",
                            "EQUIPMENT",
                            "#ElementList"
                    ),
                    new MenuEntryElement(
                            "Inventory Grid Area",
                            "Reserved for hotbar, backpack, and storage summary panels that will mirror the real inventory containers.",
                            "INVENTORY",
                            "#ElementList"
                    )
            };
        }

        return new MainMenuPage(
                playerRef,
                activeSection,
                contentElements,
                tabElements,
                sidebarElements
        );
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commands,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store
    ) {
        // Main content lives in #ElementList and is handled by ChoiceBasePage.
        super.build(ref, commands, events, store);

        // Tabs are built manually into #TabsList.
        for (int i = 0; i < tabElements.length; i++) {
            String selector = "#TabsList[" + i + "]";
            tabElements[i].addButton(commands, events, selector, playerRef);
            bindTabClick(events, selector, TAB_INDEX_BASE + i);
        }

        // Sidebar is visual-only for now.
        for (int i = 0; i < sidebarElements.length; i++) {
            String selector = "#SidebarList[" + i + "]";
            sidebarElements[i].addButton(commands, events, selector, playerRef);
        }
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull ChoicePageEventData data
    ) {
        int index = data.getIndex();

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        PageManager pages = player.getPageManager();
        if (pages == null) {
            return;
        }

        if (index == TAB_INDEX_BASE) {
            if (activeSection != Section.MUTATIONS) {
                pages.openCustomPage(ref, store, MainMenuPage.create(playerRef));
            }
            return;
        }

        if (index == TAB_INDEX_BASE + 1) {
            if (activeSection != Section.EQUIPMENT) {
                pages.openCustomPage(ref, store, MainMenuPage.createEquipment(playerRef));
            }
            return;
        }

        // Main content entries are informational for now.
    }

    private void bindTabClick(
            @Nonnull UIEventBuilder events,
            @Nonnull String selector,
            int index
    ) {
        EventData data = EventData.of("Index", Integer.toString(index));

        String[] selectorCandidates = new String[] {
                selector,
                selector + " #Button"
        };

        for (String candidate : selectorCandidates) {
            try {
                events.addEventBinding(CustomUIEventBindingType.Activating, candidate, data, false);
                return;
            } catch (Exception ignored) {
            }
        }
    }
}