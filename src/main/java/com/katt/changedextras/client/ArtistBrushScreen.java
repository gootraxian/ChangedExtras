package com.katt.changedextras.client;

import com.katt.changedextras.item.ArtistBrushItem;
import com.katt.changedextras.network.ChangedExtrasNetwork;
import com.katt.changedextras.network.SaveArtistBrushPacket;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.ChatFormatting;
import net.ltxprogrammer.changed.client.renderer.CustomLatexRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ArtistBrushScreen extends Screen {
    private static final int PREVIEW_MAX_WIDTH = 220;
    private static final int PREVIEW_MAX_HEIGHT = 220;
    private static final String CUSTOM_LATEX_TEXTURE_URL = "https://github.com/LtxProgrammer/Changed-Minecraft-Mod/blob/1.20.1-dev/src/main/resources/assets/changed/textures/custom_latex.png";

    private final InteractionHand hand;
    private final ItemStack stack;
    private EditBox texturePath;
    private EditBox hexColor;
    private int leftPaneX;
    private int topPaneY;
    private int leftPaneWidth;
    private int previewX;
    private int previewY;
    private PreviewTextureData cachedPreviewData;
    private String cachedPreviewPath = "";

    public ArtistBrushScreen(InteractionHand hand, ItemStack stack) {
        super(Component.translatable("screen.changedextras.artist_brush"));
        this.hand = hand;
        this.stack = stack.copy();
    }

    @Override
    protected void init() {
        super.init();
        recalculateLayout();
        var brushData = ArtistBrushItem.getOrCreateBrushData(stack);

        int fieldWidth = Math.max(140, leftPaneWidth - 82);
        texturePath = new EditBox(this.font, leftPaneX, topPaneY + 20, fieldWidth, 20, Component.translatable("screen.changedextras.artist_brush.texture"));
        texturePath.setMaxLength(Integer.MAX_VALUE);
        texturePath.setValue(brushData.getString(ArtistBrushItem.TEXTURE_PATH_TAG));
        addRenderableWidget(texturePath);

        addRenderableWidget(Button.builder(Component.translatable("screen.changedextras.artist_brush.browse"), button -> chooseTextureFile())
                .bounds(leftPaneX + fieldWidth + 8, topPaneY + 20, Math.max(70, leftPaneWidth - fieldWidth - 8), 20)
                .build());

        hexColor = new EditBox(this.font, leftPaneX, topPaneY + 78, Math.min(160, leftPaneWidth), 20, Component.translatable("screen.changedextras.artist_brush.hex"));
        hexColor.setValue(brushData.getString(ArtistBrushItem.HEX_COLOR_TAG));
        addRenderableWidget(hexColor);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> saveAndClose())
                .bounds(leftPaneX, topPaneY + 152, Math.max(90, (leftPaneWidth - 8) / 2), 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(leftPaneX + Math.max(90, (leftPaneWidth - 8) / 2) + 8, topPaneY + 152, Math.max(90, leftPaneWidth - Math.max(90, (leftPaneWidth - 8) / 2) - 8), 20)
                .build());
        setInitialFocus(texturePath);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        PreviewTextureData preview = resolvePreviewTexture();
        String selectedName = trimToWidth(ArtistBrushItem.getOrCreateBrushData(stack).getString(ArtistBrushItem.SELECTED_TARGET_NAME_TAG), leftPaneWidth);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, topPaneY, 0xFFFFFF);
        graphics.drawString(this.font, Component.translatable("screen.changedextras.artist_brush.texture"), leftPaneX, topPaneY + 8, 0xCFCFCF, false);
        graphics.drawString(this.font, Component.translatable("screen.changedextras.artist_brush.hex"), leftPaneX, topPaneY + 66, 0xCFCFCF, false);
        graphics.drawString(this.font, Component.translatable("screen.changedextras.artist_brush.target", ArtistBrushItem.TARGET_FORM_ID), leftPaneX, topPaneY + 106, 0xA6E3A1, false);
        graphics.drawString(this.font, Component.translatable("screen.changedextras.artist_brush.selected", selectedName), leftPaneX, topPaneY + 120, 0x8DB9FF, false);
        graphics.drawString(this.font, Component.translatable("screen.changedextras.artist_brush.preview"), previewX, topPaneY + 8, 0xCFCFCF, false);

        renderTexturePreview(graphics, previewX, previewY, preview);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && isInsideTextureLink(mouseX, mouseY)) {
            Util.getPlatform().openUri(CUSTOM_LATEX_TEXTURE_URL);
            return true;
        }
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        disposeCachedPreview();
    }

    private void saveAndClose() {
        ChangedExtrasNetwork.INSTANCE.sendToServer(new SaveArtistBrushPacket(
                hand,
                texturePath.getValue().trim(),
                normalizeHex(hexColor.getValue().trim())
        ));
        onClose();
    }

    private void renderTexturePreview(GuiGraphics graphics, int previewX, int previewY, PreviewTextureData preview) {
        ImageBounds bounds = getImageBounds(preview);
        int drawWidth = bounds.drawWidth();
        int drawHeight = bounds.drawHeight();
        int imageX = bounds.x();
        int imageY = bounds.y();

        graphics.fill(previewX - 3, previewY - 3, previewX + PREVIEW_MAX_WIDTH + 3, previewY + PREVIEW_MAX_HEIGHT + 3, 0xFF232323);
        graphics.fill(previewX, previewY, previewX + PREVIEW_MAX_WIDTH, previewY + PREVIEW_MAX_HEIGHT, 0xFF121212);

        for (int y = 0; y < drawHeight; y += 16) {
            for (int x = 0; x < drawWidth; x += 16) {
                int color = ((x + y) / 16) % 2 == 0 ? 0xFF3A3A3A : 0xFF505050;
                graphics.fill(imageX + x, imageY + y, imageX + Math.min(drawWidth, x + 16), imageY + Math.min(drawHeight, y + 16), color);
            }
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.pose().pushPose();
        graphics.pose().translate(imageX, imageY, 0.0F);
        graphics.pose().scale(drawWidth / (float)Math.max(1, preview.imageWidth()), drawHeight / (float)Math.max(1, preview.imageHeight()), 1.0F);
        graphics.blit(preview.texture(), 0, 0, 0, 0, preview.imageWidth(), preview.imageHeight(), preview.imageWidth(), preview.imageHeight());
        graphics.pose().popPose();

        drawRegionOutline(graphics, imageX, imageY, imageX + drawWidth, imageY + drawHeight, 0xFF5A5A5A);

        graphics.drawString(this.font, Component.translatable("screen.changedextras.artist_brush.preview_hint"), previewX, previewY + PREVIEW_MAX_HEIGHT + 8, 0x8DD3FF, false);
        graphics.drawString(this.font, Component.translatable("screen.changedextras.artist_brush.preview_target_hint"), previewX, previewY + PREVIEW_MAX_HEIGHT + 20, 0x9FD7FF, false);
        graphics.drawString(this.font, Component.translatable("screen.changedextras.artist_brush.preview_size", preview.imageWidth(), preview.imageHeight()), previewX, previewY + PREVIEW_MAX_HEIGHT + 32, 0xD7D7D7, false);
        graphics.drawString(this.font,
                Component.literal("Click here to get the custom latex texture, so you can make your own texture").withStyle(ChatFormatting.UNDERLINE),
                previewX,
                previewY + PREVIEW_MAX_HEIGHT + 44,
                0x7CC7FF,
                false);
    }

    private PreviewTextureData resolvePreviewTexture() {
        String path = texturePath.getValue().trim();
        if (path.isEmpty()) {
            return resolveResourceTexture(CustomLatexRenderer.DEFAULT_SKIN_LOCATION);
        }
        if (path.equals(cachedPreviewPath) && cachedPreviewData != null) {
            return cachedPreviewData;
        }

        disposeCachedPreview();

        Path filePath = Path.of(path);
        cachedPreviewData = Files.exists(filePath) ? resolveExternalTexture(filePath) : resolveResourceTexture(ResourceLocation.tryParse(path));
        cachedPreviewPath = path;
        return cachedPreviewData;
    }

    private PreviewTextureData resolveExternalTexture(Path filePath) {
        try (var stream = Files.newInputStream(filePath)) {
            NativeImage image = NativeImage.read(stream);
            DynamicTexture dynamicTexture = new DynamicTexture(image);
            String normalized = filePath.toAbsolutePath().normalize().toString();
            ResourceLocation texture = this.minecraft.getTextureManager().register("artist_brush_preview/" + Math.abs(normalized.hashCode()), dynamicTexture);
            return PreviewTextureData.of(texture, image.getWidth(), image.getHeight(), dynamicTexture);
        } catch (IOException ignored) {
            return PreviewTextureData.missing();
        }
    }

    private PreviewTextureData resolveResourceTexture(ResourceLocation location) {
        if (location == null) {
            return PreviewTextureData.missing();
        }
        try (var stream = this.minecraft.getResourceManager().open(location)) {
            NativeImage image = NativeImage.read(stream);
            return PreviewTextureData.of(location, image.getWidth(), image.getHeight(), null);
        } catch (IOException ignored) {
            return PreviewTextureData.missing();
        }
    }

    private boolean isInsideTextureLink(double mouseX, double mouseY) {
        String text = "Click here to get the custom latex texture, so you can make your own texture";
        int x = previewX;
        int y = previewY + PREVIEW_MAX_HEIGHT + 44;
        int width = this.font.width(text);
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + this.font.lineHeight;
    }

    private void chooseTextureFile() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.png"));
            filters.flip();
            String chosen = TinyFileDialogs.tinyfd_openFileDialog(
                    Component.translatable("screen.changedextras.artist_brush.browse").getString(),
                    texturePath.getValue().isBlank() ? null : texturePath.getValue(),
                    filters,
                    "PNG Images",
                    false
            );
            if (chosen != null && !chosen.isBlank()) {
                texturePath.setValue(chosen);
                cachedPreviewPath = "";
            }
        } catch (Throwable ignored) {
            showBrowseUnavailableMessage();
        }
    }

    private void showBrowseUnavailableMessage() {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(Component.translatable("message.changedextras.artist_brush.browse_unavailable"), true);
        }
    }

    private static String normalizeHex(String value) {
        if (value.isEmpty()) {
            return "#FFFFFF";
        }
        return value.startsWith("#") ? value.toUpperCase() : ("#" + value.toUpperCase());
    }

    private void disposeCachedPreview() {
        if (cachedPreviewData != null && cachedPreviewData.dynamicTexture() != null) {
            cachedPreviewData.dynamicTexture().close();
        }
        cachedPreviewData = null;
        cachedPreviewPath = "";
    }

    private void recalculateLayout() {
        leftPaneWidth = Math.min(250, Math.max(190, this.width / 2 - 24));
        leftPaneX = Math.max(12, this.width / 2 - leftPaneWidth - (PREVIEW_MAX_WIDTH / 2) - 16);
        previewX = Math.min(this.width - PREVIEW_MAX_WIDTH - 12, leftPaneX + leftPaneWidth + 16);
        topPaneY = Math.max(12, (this.height - 280) / 2);
        previewY = topPaneY + 20;
    }

    private void drawRegionOutline(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        graphics.fill(x1, y1, x2, y1 + 1, color);
        graphics.fill(x1, y2 - 1, x2, y2, color);
        graphics.fill(x1, y1, x1 + 1, y2, color);
        graphics.fill(x2 - 1, y1, x2, y2, color);
    }

    private String trimToWidth(String value, int maxWidth) {
        return this.font.plainSubstrByWidth(value, Math.max(40, maxWidth - 10));
    }

    private record ImageBounds(int x, int y, int drawWidth, int drawHeight) {
    }

    private record PreviewTextureData(ResourceLocation texture, int imageWidth, int imageHeight, int drawWidth, int drawHeight, DynamicTexture dynamicTexture) {
        static PreviewTextureData of(ResourceLocation texture, int imageWidth, int imageHeight, DynamicTexture dynamicTexture) {
            float scale = Math.min(PREVIEW_MAX_WIDTH / (float)Math.max(1, imageWidth), PREVIEW_MAX_HEIGHT / (float)Math.max(1, imageHeight));
            int drawWidth = Math.max(1, Math.round(imageWidth * scale));
            int drawHeight = Math.max(1, Math.round(imageHeight * scale));
            return new PreviewTextureData(texture, imageWidth, imageHeight, drawWidth, drawHeight, dynamicTexture);
        }

        static PreviewTextureData missing() {
            return of(MissingTextureAtlasSprite.getLocation(), 16, 16, null);
        }
    }

    private ImageBounds getImageBounds(PreviewTextureData preview) {
        int drawWidth = preview.drawWidth();
        int drawHeight = preview.drawHeight();
        int imageX = previewX + (PREVIEW_MAX_WIDTH - drawWidth) / 2;
        int imageY = previewY + (PREVIEW_MAX_HEIGHT - drawHeight) / 2;
        return new ImageBounds(imageX, imageY, drawWidth, drawHeight);
    }
}
