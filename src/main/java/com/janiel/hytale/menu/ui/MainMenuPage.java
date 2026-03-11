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
import com.janiel.hytale.mutations.ui.MutationCatalogTileElement;
import com.janiel.hytale.mutations.ui.MutationDetailElement;
import com.janiel.hytale.mutations.ui.MutationsPage;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

public final class MainMenuPage extends ChoiceBasePage {

    private static final int TAB_INDEX_BASE = 1000;
    private static final int MUTATION_TILE_INDEX_BASE = 2000;

    private final PlayerRef playerRef;
    private final Section activeSection;
    private final ChoiceElement[] tabElements;
    private final ChoiceElement[] overviewElements;
    private final ChoiceElement[] detailElements;
    private final String bodyUiPath;
    private final String overviewListSelector;
    private final String detailListSelector;

    private final MutationsPage.MutationViewData[] mutationViews;
    private String selectedMutationKey;

    private enum Section {
        MUTATIONS,
        EQUIPMENT
    }

    private MainMenuPage(
            @Nonnull PlayerRef playerRef,
            @Nonnull Section activeSection,
            @Nonnull ChoiceElement[] tabElements,
            @Nonnull ChoiceElement[] overviewElements,
            @Nonnull ChoiceElement[] detailElements,
            @Nonnull String bodyUiPath,
            @Nonnull String overviewListSelector,
            @Nonnull String detailListSelector,
            @Nonnull MutationsPage.MutationViewData[] mutationViews,
            @Nonnull String selectedMutationKey
    ) {
        super(playerRef, new ChoiceElement[0], "Pages/JanielMenu/MainMenuHome.ui");
        this.playerRef = playerRef;
        this.activeSection = activeSection;
        this.tabElements = tabElements;
        this.overviewElements = overviewElements;
        this.detailElements = detailElements;
        this.bodyUiPath = bodyUiPath;
        this.overviewListSelector = overviewListSelector;
        this.detailListSelector = detailListSelector;
        this.mutationViews = mutationViews;
        this.selectedMutationKey = selectedMutationKey;
    }

    public static MainMenuPage create(@Nonnull PlayerRef playerRef) {
        return create(playerRef, Section.MUTATIONS, null);
    }

    public static MainMenuPage createEquipment(@Nonnull PlayerRef playerRef) {
        return create(playerRef, Section.EQUIPMENT, null);
    }

    private static MainMenuPage create(
            @Nonnull PlayerRef playerRef,
            @Nonnull Section activeSection,
            String selectedMutationKey
    ) {
        ChoiceElement[] tabElements = new ChoiceElement[] {
                new MenuTabElement("Mutations", activeSection == Section.MUTATIONS),
                new MenuTabElement("Equipment", activeSection == Section.EQUIPMENT)
        };

        if (activeSection == Section.MUTATIONS) {
            MutationsPage.MutationViewData[] mutationViews = MutationsPage.createViewData(playerRef);
            String resolvedSelectedKey = resolveSelectedMutationKey(mutationViews, selectedMutationKey);
            MutationsPage.MutationViewData selectedView = findMutationView(mutationViews, resolvedSelectedKey);

            ChoiceElement[] detailElements = new ChoiceElement[] {
                    new MutationDetailElement(
                            selectedView.getName(),
                            selectedView.getDescription(),
                            "Level " + selectedView.getLevel(),
                            selectedView.getProgressLabel(),
                            selectedView.getTierLine1(),
                            selectedView.getTierLine2(),
                            selectedView.getTierLine3(),
                            selectedView.getSummaryLine1(),
                            selectedView.getSummaryLine2(),
                            selectedView.getSummaryLine3(),
                            "#MutationDetailsList"
                    )
            };

            return new MainMenuPage(
                    playerRef,
                    activeSection,
                    tabElements,
                    new ChoiceElement[0],
                    detailElements,
                    "Pages/JanielMenu/MainMenuMutationsBody.ui",
                    "",
                    "#MutationDetailsList",
                    mutationViews,
                    resolvedSelectedKey
            );
        }

        ChoiceElement[] overviewElements = new ChoiceElement[] {
                new MenuEntryElement(
                        "Player Preview Area",
                        "Reserved for the character preview, equipped armor silhouette, vanity display, and quick visual identity.",
                        "PREVIEW",
                        "#EquipmentOverviewList"
                ),
                new MenuEntryElement(
                        "Stats Area",
                        "Reserved for derived combat stats, mutation bonuses, armor totals, utility modifiers, and resistances.",
                        "STATS",
                        "#EquipmentOverviewList"
                )
        };

        ChoiceElement[] detailElements = new ChoiceElement[] {
                new MenuEntryElement(
                        "Equipment Slots Area",
                        "Reserved for helmet, chest, legs, boots, utility slots, and other equipped items tied to the real player inventory.",
                        "EQUIPMENT",
                        "#EquipmentDetailsList"
                ),
                new MenuEntryElement(
                        "Inventory Grid Area",
                        "Reserved for hotbar, backpack, and storage summary panels that will mirror the real inventory containers.",
                        "INVENTORY",
                        "#EquipmentDetailsList"
                )
        };

        return new MainMenuPage(
                playerRef,
                activeSection,
                tabElements,
                overviewElements,
                detailElements,
                "Pages/JanielMenu/MainMenuEquipmentBody.ui",
                "#EquipmentOverviewList",
                "#EquipmentDetailsList",
                new MutationsPage.MutationViewData[0],
                ""
        );
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commands,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store
    ) {
        super.build(ref, commands, events, store);

        commands.append("#BodyHost", bodyUiPath);

        for (int i = 0; i < tabElements.length; i++) {
            String selector = "#TabsList[" + i + "]";
            tabElements[i].addButton(commands, events, selector, playerRef);
            bindClickIndex(events, selector, TAB_INDEX_BASE + i);
        }

        if (activeSection == Section.MUTATIONS) {
            buildMutationCatalog(commands, events);

            if (detailElements.length > 0) {
                detailElements[0].addButton(commands, events, "#MutationDetailsList[0]", playerRef);
            }
            return;
        }

        for (int i = 0; i < overviewElements.length; i++) {
            String selector = overviewListSelector + "[" + i + "]";
            overviewElements[i].addButton(commands, events, selector, playerRef);
        }

        for (int i = 0; i < detailElements.length; i++) {
            String selector = detailListSelector + "[" + i + "]";
            detailElements[i].addButton(commands, events, selector, playerRef);
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

        if (activeSection == Section.MUTATIONS
                && index >= MUTATION_TILE_INDEX_BASE
                && index < MUTATION_TILE_INDEX_BASE + mutationViews.length) {

            MutationsPage.MutationViewData selectedView = mutationViews[index - MUTATION_TILE_INDEX_BASE];

            if (selectedView.getKey().equals(selectedMutationKey)) {
                return;
            }

            updateMutationDetails(selectedView);
            selectedMutationKey = selectedView.getKey();
            return;
        }
    }

    private void buildMutationCatalog(
            @Nonnull UICommandBuilder commands,
            @Nonnull UIEventBuilder events
    ) {
        Map<String, Integer> rowIndexBySelector = new HashMap<>();

        for (int i = 0; i < mutationViews.length; i++) {
            MutationsPage.MutationViewData view = mutationViews[i];

            int rowIndex = rowIndexBySelector.getOrDefault(view.getCatalogRowSelector(), 0);
            rowIndexBySelector.put(view.getCatalogRowSelector(), rowIndex + 1);

            String selector = view.getCatalogRowSelector() + "[" + rowIndex + "]";

            MutationCatalogTileElement tile = new MutationCatalogTileElement(
                    view.getCatalogLabel(),
                    view.getIconUiPath(),
                    view.getLevel(),
                    view.getCatalogRowSelector()
            );

            tile.addButton(commands, events, selector, playerRef);
            bindClickIndex(events, selector, MUTATION_TILE_INDEX_BASE + i);
        }
    }

    private void updateMutationDetails(@Nonnull MutationsPage.MutationViewData selectedView) {
        UICommandBuilder commandBuilder = new UICommandBuilder();

        commandBuilder.clear("#MutationDetailsList");

        MutationDetailElement detailElement = buildMutationDetailElement(selectedView);
        detailElement.addButton(commandBuilder, new UIEventBuilder(), "#MutationDetailsList[0]", playerRef);

        sendUpdate(commandBuilder);
    }

    @Nonnull
    private MutationDetailElement buildMutationDetailElement(
            @Nonnull MutationsPage.MutationViewData selectedView
    ) {
        return new MutationDetailElement(
                selectedView.getName(),
                selectedView.getDescription(),
                "Level " + selectedView.getLevel(),
                selectedView.getProgressLabel(),
                selectedView.getTierLine1(),
                selectedView.getTierLine2(),
                selectedView.getTierLine3(),
                selectedView.getSummaryLine1(),
                selectedView.getSummaryLine2(),
                selectedView.getSummaryLine3(),
                "#MutationDetailsList"
        );
    }

    private void bindClickIndex(
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

    @Nonnull
    private static String resolveSelectedMutationKey(
            @Nonnull MutationsPage.MutationViewData[] mutationViews,
            String selectedMutationKey
    ) {
        if (mutationViews.length == 0) {
            return "";
        }

        if (selectedMutationKey == null || selectedMutationKey.isBlank()) {
            return mutationViews[0].getKey();
        }

        for (MutationsPage.MutationViewData view : mutationViews) {
            if (view.getKey().equals(selectedMutationKey)) {
                return selectedMutationKey;
            }
        }

        return mutationViews[0].getKey();
    }

    @Nonnull
    private static MutationsPage.MutationViewData findMutationView(
            @Nonnull MutationsPage.MutationViewData[] mutationViews,
            @Nonnull String selectedMutationKey
    ) {
        for (MutationsPage.MutationViewData view : mutationViews) {
            if (view.getKey().equals(selectedMutationKey)) {
                return view;
            }
        }

        return mutationViews[0];
    }
}