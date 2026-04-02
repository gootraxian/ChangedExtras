package com.katt.changedextras.client.discovery;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.ServerStatusPing;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class DiscoveryScreen extends Screen {
    private final JoinMultiplayerScreen parentScreen;
    private final DiscoveryStatusPinger pinger = new DiscoveryStatusPinger();
    private final List<DiscoveryTrackedServer> trackedServers = new ArrayList<>();
    private DiscoveryServerList serverList;
    private Button joinButton;
    private int totalModdedServers;
    private int pendingScans;

    public DiscoveryScreen(JoinMultiplayerScreen parentScreen) {
        super(Component.translatable("changedextras.discovery.title"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        if (!DiscoverySupport.isAuthorizedUser(this.minecraft)) {
            this.onClose();
            return;
        }

        if (this.serverList == null) {
            this.serverList = new DiscoveryServerList(this, this.minecraft, this.width, this.height, 32, this.height - 64, 40);
            this.startScan();
        } else {
            this.serverList.updateSize(this.width, this.height, 32, this.height - 64);
        }

        this.addWidget(this.serverList);
        this.addRenderableWidget(Button.builder(Component.translatable("selectServer.title"), button -> this.minecraft.setScreen(this.parentScreen))
                .bounds(this.width / 2 - 101, 6, 100, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("changedextras.discovery.tab"), button -> {
        }).bounds(this.width / 2 + 1, 6, 100, 20).build()).active = false;

        this.joinButton = this.addRenderableWidget(Button.builder(Component.translatable("selectServer.select"), button -> this.joinSelectedServer())
                .width(100)
                .build());
        Button refreshButton = this.addRenderableWidget(Button.builder(Component.translatable("selectServer.refresh"), button -> this.startScan())
                .width(100)
                .build());
        Button backButton = this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose())
                .width(100)
                .build());

        GridLayout layout = new GridLayout();
        GridLayout.RowHelper rowHelper = layout.createRowHelper(1);
        LinearLayout buttons = rowHelper.addChild(new LinearLayout(308, 20, LinearLayout.Orientation.HORIZONTAL));
        buttons.addChild(this.joinButton);
        buttons.addChild(refreshButton);
        buttons.addChild(backButton);
        rowHelper.addChild(SpacerElement.height(4));
        layout.arrangeElements();
        FrameLayout.centerInRectangle(layout, 0, this.height - 64, this.width, 64);
        this.updateJoinButton();
    }

    @Override
    public void tick() {
        super.tick();
        this.pinger.tick();
    }

    @Override
    public void removed() {
        this.pinger.removeAll();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        this.serverList.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 16777215);

        Component countLine = Component.translatable("changedextras.discovery.total_count", this.totalModdedServers);
        guiGraphics.drawCenteredString(this.font, countLine, this.width / 2, 44, 11184810);

        int visibleServers = this.getVisibleServers().size();
        Component visibleLine = Component.translatable("changedextras.discovery.visible_count", visibleServers);
        guiGraphics.drawCenteredString(this.font, visibleLine, this.width / 2, 56, 11184810);

        if (this.pendingScans > 0) {
            Component scanning = Component.translatable("changedextras.discovery.scanning", this.pendingScans);
            guiGraphics.drawCenteredString(this.font, scanning, this.width / 2, 68, 11184810);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parentScreen);
    }

    public void joinSelectedServer() {
        DiscoveryServerList.Entry selected = this.serverList.getSelected();
        if (selected == null) {
            return;
        }

        ServerData serverData = selected.trackedServer().serverData();
        ConnectScreen.startConnecting(this, this.minecraft, ServerAddress.parseString(serverData.ip), serverData, false);
    }

    public void onSelectedChange() {
        this.updateJoinButton();
    }

    private void startScan() {
        this.pinger.removeAll();
        this.trackedServers.clear();
        this.totalModdedServers = 0;

        ServerList savedServers = new ServerList(this.minecraft);
        savedServers.load();
        for (int i = 0; i < savedServers.size(); i++) {
            ServerData original = savedServers.get(i);
            if (original.isLan()) {
                continue;
            }

            ServerData copy = new ServerData(original.name, original.ip, false);
            copy.copyFrom(original);
            this.trackedServers.add(new DiscoveryTrackedServer(copy));
        }

        this.pendingScans = this.trackedServers.size();
        this.refreshVisibleEntries();

        for (DiscoveryTrackedServer trackedServer : this.trackedServers) {
            try {
                this.pinger.pingServer(trackedServer.serverData(), (serverData, forgeData) -> this.minecraft.execute(() -> this.applyScanResult(trackedServer, forgeData)));
            } catch (UnknownHostException exception) {
                this.applyScanResult(trackedServer, null);
            }
        }
    }

    private void applyScanResult(DiscoveryTrackedServer trackedServer, ServerStatusPing forgeData) {
        if (!trackedServer.scanComplete()) {
            this.pendingScans = Math.max(0, this.pendingScans - 1);
        }

        boolean hasChangedExtras = DiscoverySupport.hasChangedExtras(forgeData);
        boolean discoveryEnabled = DiscoverySupport.isDiscoveryEnabled(forgeData);
        trackedServer.applyScan(hasChangedExtras, discoveryEnabled);
        this.totalModdedServers = (int) this.trackedServers.stream().filter(DiscoveryTrackedServer::hasChangedExtras).count();
        this.refreshVisibleEntries();
    }

    private void refreshVisibleEntries() {
        if (this.serverList == null) {
            return;
        }

        this.serverList.updateEntries(this.getVisibleServers());
        this.updateJoinButton();
    }

    private List<DiscoveryTrackedServer> getVisibleServers() {
        return this.trackedServers.stream()
                .filter(DiscoveryTrackedServer::hasChangedExtras)
                .sorted(Comparator.comparing(server -> server.serverData().name.toLowerCase()))
                .collect(Collectors.toList());
    }

    private void updateJoinButton() {
        if (this.joinButton != null) {
            this.joinButton.active = this.serverList != null && this.serverList.getSelected() != null;
        }
    }
}
