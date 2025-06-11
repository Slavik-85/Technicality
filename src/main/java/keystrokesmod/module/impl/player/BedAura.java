package keystrokesmod.module.impl.player;

import keystrokesmod.Technicality;
import keystrokesmod.event.*;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.minigames.BedWars;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.*;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

public class BedAura extends Module {
    public SliderSetting mode;
    private SliderSetting breakSpeed;
    private SliderSetting fov;
    public SliderSetting range;
    private SliderSetting rate;
    public ButtonSetting allowAura;
    private ButtonSetting breakNearBlock;
    private ButtonSetting cancelKnockback;
    private ButtonSetting disableBreakEffects;
    private ButtonSetting showBlockInfo; // 新增：显示方块信息开关
    private Map<BlockPos, Block> cachedSurrounding = new HashMap<>(); // 缓存周围方块
    public ButtonSetting groundSpoof;
    private ButtonSetting onlyWhileVisible;
    private ButtonSetting renderOutline;
    private ButtonSetting sendAnimations;
    private ButtonSetting silentSwing;
    private String[] modes = new String[]{"Legit", "Instant", "Swap"};

    private BlockPos[] bedPos;
    private BlockPos packetPos;
    public float breakProgress;
    private int lastSlot = -1;
    public BlockPos currentBlock, lastBlock;
    private long lastCheck = 0;
    public boolean stopAutoblock, breakTick;
    private int outlineColor = new Color(226, 65, 65).getRGB();
    private BlockPos nearestBlock;
    private Map<BlockPos, Float> breakProgressMap = new HashMap<>();
    public double lastProgress;
    public float vanillaProgress;
    private int defaultOutlineColor = new Color(226, 65, 65).getRGB();
    private BlockPos previousBlockBroken;
    private BlockPos rotateLastBlock;
    private boolean spoofGround, firstStop;
    private boolean isBreaking, startPacket, stopPacket, delayStop;
    // ========== 新增的进度条相关变量 ==========
    private float barWidth = 80;
    private float barHeight = 5;
    private float filledWidth;
    private float barX, barY;
    private int progressColor;
    private int dotAnimationCounter = 0;
    private long lastDotUpdate;

    public BedAura() {
        super("BedAura", category.player, 0);
        this.registerSetting(mode = new SliderSetting("Break mode", 0, modes));
        this.registerSetting(breakSpeed = new SliderSetting("Break speed", "x", 1, 1, 2, 0.01));
        this.registerSetting(fov = new SliderSetting("FOV", 360.0, 30.0, 360.0, 4.0));
        this.registerSetting(range = new SliderSetting("Range", 4.5, 1.0, 8.0, 0.5));
        this.registerSetting(rate = new SliderSetting("Rate", " second", 0.2, 0.05, 3.0, 0.05));
        this.registerSetting(allowAura = new ButtonSetting("Allow aura", true));
        this.registerSetting(breakNearBlock = new ButtonSetting("Break near block", false));
        this.registerSetting(cancelKnockback = new ButtonSetting("Cancel knockback", false));
        this.registerSetting(disableBreakEffects = new ButtonSetting("Disable break effects", false));
        this.registerSetting(groundSpoof = new ButtonSetting("Ground spoof", false));
        this.registerSetting(onlyWhileVisible = new ButtonSetting("Only while visible", false));
        this.registerSetting(renderOutline = new ButtonSetting("Render block outline", true));
        this.registerSetting(sendAnimations = new ButtonSetting("Send animations", false));
        this.registerSetting(silentSwing = new ButtonSetting("Silent swing", false));
        this.registerSetting(showBlockInfo = new ButtonSetting("Show Block Info", false)); // 新建设置项
    }

    @Override
    public String getInfo() {
        return modes[(int) mode.getInput()];
    }

    @Override
    public void onDisable() {
        reset(true, true);
        bedPos = null;
    }

    @Override
    public void onEnable() {
        // 新增：初始化进度条位置
        updateProgressBarPosition();
    }

    private void updateProgressBarPosition() {
        ScaledResolution sr = new ScaledResolution(mc);
        barX = sr.getScaledWidth() / 2 - barWidth / 2;
        barY = sr.getScaledHeight() / 2 + 28;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) // takes priority over ka & antifireball
    public void onPreUpdate(PreUpdateEvent e) {
        // 新增：更新进度条宽度（使用类成员变量 this.breakProgress）
        if (this.currentBlock != null && this.breakProgress > 0) {
            this.filledWidth = (float) (barWidth * this.breakProgress);
            updateProgressBarPosition();
        }
    }

    @SubscribeEvent
    public void onWorldJoin(EntityJoinWorldEvent e) {
        if (e.entity == mc.thePlayer) {
            reset(true, true);
            bedPos = null;
        }
    }

    @SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent e) {
        if (!Utils.nullCheck() || !cancelKnockback.isToggled() || currentBlock == null) {
            return;
        }
        if (e.getPacket() instanceof S12PacketEntityVelocity) {
            if (((S12PacketEntityVelocity) e.getPacket()).getEntityID() == mc.thePlayer.getEntityId()) {
                e.setCanceled(true);
            }
        } else if (e.getPacket() instanceof S27PacketExplosion) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onClientRotation(ClientRotationEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }
        if (delayStop) {
            delayStop = false;
        } else {
            stopAutoblock = false;
        }
        breakTick = false;
        if (currentBlock == null || !RotationUtils.inRange(currentBlock, range.getInput())) {
            reset(true, true);
            bedPos = null;
        }
        if (Utils.isBedwarsPracticeOrReplay()) {
            return;
        }
        if (ModuleManager.bedwars != null && ModuleManager.bedwars.isEnabled() && BedWars.whitelistOwnBed.isToggled() && !BedWars.outsideSpawn) {
            reset(true, true);
            return;
        }
        if (!mc.thePlayer.capabilities.allowEdit || mc.thePlayer.isSpectator()) {
            reset(true, true);
            return;
        }
        if (bedPos == null) {
            if (!isBreaking && System.currentTimeMillis() - lastCheck >= (rate.getInput() * 1000)) {
                lastCheck = System.currentTimeMillis();
                bedPos = getBedPos();
            }
            if (bedPos == null) {
                reset(true, true);
                return;
            }
        } else {
            if (!(BlockUtils.getBlock(bedPos[0]) instanceof BlockBed) || (currentBlock != null && BlockUtils.replaceable(currentBlock))) {
                reset(true, true);
                return;
            }
        }
        if (breakNearBlock.isToggled() && isCovered(bedPos[0]) && isCovered(bedPos[1])) {
            if (nearestBlock == null) {
                nearestBlock = getBestBlock(bedPos, true);
            }
            breakBlock(e, nearestBlock);
        } else {
            nearestBlock = null;
            breakBlock(e, bedPos[0]);
        }
    }

    @SubscribeEvent
    public void onPreMotion(PreMotionEvent e) {

        if (stopAutoblock) {
            if (Technicality.debug) {
                Utils.sendModuleMessage(this, "&7stopping autoblock (&3" + mc.thePlayer.ticksExisted + "&7).");
            }
        }

        if (groundSpoof.isToggled() && !mc.thePlayer.isInWater() && spoofGround) {
            e.setOnGround(true);
            if (Technicality.debug) {
                Utils.sendModuleMessage(this, "&7ground spoof (&3" + mc.thePlayer.ticksExisted + "&7).");
            }
        }

        if (startPacket) {
            mc.thePlayer.sendQueue.addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.START_DESTROY_BLOCK, packetPos, EnumFacing.UP));
            swing();
            if (Technicality.debug) {
                Utils.sendModuleMessage(this, "sending c07 &astart &7break &7(&b" + mc.thePlayer.ticksExisted + "&7)");
            }
        }
        if (stopPacket) {
            mc.thePlayer.sendQueue.addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK, packetPos, EnumFacing.UP));
            swing();
            if (Technicality.debug) {
                Utils.sendModuleMessage(this, "sending c07 &cstop &7break &7(&b" + mc.thePlayer.ticksExisted + "&7)");
            }
        }
        if (isBreaking && !startPacket && !stopPacket) {
            swing();
        }
        startPacket = stopPacket = spoofGround = false;
    }

    // ========== 修改的渲染事件处理器 ==========
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderOverlay(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.TEXT) return;
        if (this.currentBlock == null || this.breakProgress <= 0) return;

        // 更新动画计时
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDotUpdate > 333) {
            dotAnimationCounter = (dotAnimationCounter + 1) % 4;
            lastDotUpdate = currentTime;
        }

        // 获取颜色
        if (ModuleManager.hud != null && ModuleManager.hud.isEnabled()) {
            progressColor = Theme.getGradient((int) ModuleManager.hud.theme.getInput(), 0);
        } else {
            progressColor = new Color(0, 187, 255, 200).getRGB();
        }

        // 动态计算位置
        ScaledResolution sr = new ScaledResolution(mc);
        barX = sr.getScaledWidth() / 2f - barWidth / 2;
        barY = sr.getScaledHeight() / 2f + 28;

        // 绘制文本
        String baseText = "Breaking";
        String[] dots = {"", ".", "..", "..."};
        String fullText = baseText + dots[dotAnimationCounter];
        int textWidth = mc.fontRendererObj.getStringWidth(fullText);
        int xPos = (sr.getScaledWidth() - textWidth) / 2;
        int yPos = sr.getScaledHeight() / 2 + 8;
        mc.fontRendererObj.drawStringWithShadow(fullText, xPos, yPos, progressColor);

        // 计算填充宽度
        filledWidth = Math.min(barWidth, Math.max(0, barWidth * this.breakProgress));

        // 绘制进度条
        glEnable(GL_BLEND);
        glDisable(GL_TEXTURE_2D);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // 背景阴影
        drawRoundedRect(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 2, 0xAA000000);
        // 进度条背景
        drawRoundedRect(barX, barY, barX + barWidth, barY + barHeight, 2, 0xAA222222);
        // 渐变填充
        if (filledWidth > 0) {
            drawGradientRect(
                    barX + 0.5f,
                    barY + 0.5f,
                    barX + filledWidth - 0.5f,
                    barY + barHeight - 0.5f,
                    progressColor,
                    adjustColor(progressColor, -30)
            );
        }
        // 高光边框
        drawRoundedBorder(barX, barY, barX + barWidth, barY + barHeight, 2, 0x44FFFFFF, 1.5f);

        glEnable(GL_TEXTURE_2D);
        glDisable(GL_BLEND);
    }

    // ========== 新增的绘图方法 ==========
    private void drawRoundedRect(float x, float y, float x2, float y2, float radius, int color) {
        float alpha = (color >> 24 & 0xFF) / 255.0F;
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        glColor4f(red, green, blue, alpha);
        glBegin(GL_POLYGON);
        for (int i = 0; i <= 360; i += 30) {
            float rad = (float) Math.toRadians(i);
            float cx = x + radius + (float) Math.cos(rad) * radius;
            float cy = y + radius + (float) Math.sin(rad) * radius;
            glVertex2f(cx, cy);
        }
        glEnd();
    }

    private void drawGradientRect(float left, float top, float right, float bottom, int startColor, int endColor) {
        glShadeModel(GL_SMOOTH);
        glBegin(GL_QUADS);
        glColor4f(
                (startColor >> 16 & 0xFF) / 255.0F,
                (startColor >> 8 & 0xFF) / 255.0F,
                (startColor & 0xFF) / 255.0F,
                (startColor >> 24 & 0xFF) / 255.0F
        );
        glVertex2f(left, top);
        glVertex2f(left, bottom);
        glColor4f(
                (endColor >> 16 & 0xFF) / 255.0F,
                (endColor >> 8 & 0xFF) / 255.0F,
                (endColor & 0xFF) / 255.0F,
                (endColor >> 24 & 0xFF) / 255.0F
        );
        glVertex2f(right, bottom);
        glVertex2f(right, top);
        glEnd();
        glShadeModel(GL_FLAT);
    }

    private void drawRoundedBorder(float x, float y, float x2, float y2, float radius, int color, float lineWidth) {
        glLineWidth(lineWidth);
        float alpha = (color >> 24 & 0xFF) / 255.0F;
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        glColor4f(red, green, blue, alpha);
        glBegin(GL_LINE_LOOP);
        for (int i = 0; i < 360; i += 30) {
            float rad = (float) Math.toRadians(i);
            float cx = x + radius + (float) Math.cos(rad) * radius;
            float cy = y + radius + (float) Math.sin(rad) * radius;
            glVertex2f(cx, cy);
        }
        glEnd();
    }

    private int adjustColor(int original, int amount) {
        int r = Math.min(255, Math.max(0, (original >> 16 & 0xFF) + amount));
        int g = Math.min(255, Math.max(0, (original >> 8 & 0xFF) + amount));
        int b = Math.min(255, Math.max(0, (original & 0xFF) + amount));
        return (original & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderWorld(RenderWorldLastEvent e) {
        // 新增方块信息显示
        if (showBlockInfo.isToggled() && bedPos != null) {
            updateSurroundingCache();
            renderBlockInfo(e.partialTicks);
        }
        if (!renderOutline.isToggled() || currentBlock == null || !Utils.nullCheck()) {
            return;
        }
        if (ModuleManager.bedESP != null && ModuleManager.bedESP.isEnabled()) {
            outlineColor = Theme.getGradient((int) ModuleManager.bedESP.theme.getInput(), 0);
        } else if (ModuleManager.hud != null && ModuleManager.hud.isEnabled()) {
            outlineColor = Theme.getGradient((int) ModuleManager.hud.theme.getInput(), 0);
        }
        // 新增进度条渲染（使用 this.currentBlock 和 this.breakProgress）
        if (this.currentBlock != null && this.breakProgress > 0) {
            onRenderOverlay();
        } else {
            outlineColor = defaultOutlineColor;
        }
        RenderUtils.renderBlock(currentBlock, outlineColor, (Arrays.asList(bedPos).contains(currentBlock) ? 0.5625 : 1), true, false);
    }

    private void onRenderOverlay() {
    }

    // 更新周围方块缓存
    private void updateSurroundingCache() {
        cachedSurrounding.clear();
        for (BlockPos bedPart : bedPos) {
            for (EnumFacing facing : EnumFacing.values()) {
                BlockPos neighbor = bedPart.offset(facing);
                Block block = BlockUtils.getBlock(neighbor);
                if (block != Blocks.air && !Arrays.asList(bedPos).contains(neighbor)) {
                    cachedSurrounding.put(neighbor, block);
                }
            }
        }
    }

    // 渲染方块信息和材质
    private void renderBlockInfo(float partialTicks) {
        for (Map.Entry<BlockPos, Block> entry : cachedSurrounding.entrySet()) {
            BlockPos pos = entry.getKey();
            Block block = entry.getValue();

            // 渲染方块材质
            renderBlockTexture(pos, block);

            // 显示方块名称
            renderBlockName(pos, block);
        }
    }

    // 渲染方块材质
    private void renderBlockTexture(BlockPos pos, Block block) {
        try {
            RenderUtils.enable3DRender();

            // 计算相对相机位置
            double x = pos.getX() - mc.getRenderManager().viewerPosX;
            double y = pos.getY() - mc.getRenderManager().viewerPosY;
            double z = pos.getZ() - mc.getRenderManager().viewerPosZ;

            // 设置模型变换
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + 0.5, y + 1.2, z + 0.5);
            GlStateManager.scale(0.8, 0.8, 0.8);
            GlStateManager.rotate(-mc.getRenderManager().playerViewY + 180, 0, 1, 0);
            GlStateManager.rotate(mc.getRenderManager().playerViewX, 1, 0, 0);

            // 渲染物品模型
            ItemStack stack = new ItemStack(block);
            IBakedModel model = mc.getRenderItem().getItemModelMesher().getItemModel(stack);
            mc.getRenderItem().renderItem(stack, model);

            GlStateManager.popMatrix();
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            RenderUtils.disable3DRender();
        }
    }

    // 渲染方块名称
    private void renderBlockName(BlockPos pos, Block block) {
        String name = block.getLocalizedName();
        Vec3 vec = new Vec3(
                pos.getX() + 0.5,
                pos.getY() + 1.5,
                pos.getZ() + 0.5
        );

        // 使用新的投影方法
        Vec3 screenPos = RenderUtils.to2D(vec.xCoord, vec.yCoord, vec.zCoord);

        if (screenPos != null) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(screenPos.xCoord, screenPos.yCoord, 0);
            GlStateManager.scale(0.5, 0.5, 0.5);

            // 绘制背景和文字（保持不变）
            int textWidth = mc.fontRendererObj.getStringWidth(name);
            RenderUtils.drawRect(-2, -2, textWidth + 2,
                    mc.fontRendererObj.FONT_HEIGHT + 2, 0x80000000);

            mc.fontRendererObj.drawStringWithShadow(name, 0, 0, 0xFFFFFF);
            GlStateManager.popMatrix();
        }
    }

    private void resetSlot() {
        if (Technicality.packetsHandler != null && Technicality.packetsHandler.playerSlot != null && Utils.nullCheck() && Technicality.packetsHandler.playerSlot.get() != mc.thePlayer.inventory.currentItem && mode.getInput() == 2) {
            setPacketSlot(mc.thePlayer.inventory.currentItem);
        }
        else if (lastSlot != -1) {
            lastSlot = mc.thePlayer.inventory.currentItem = lastSlot;
        }
    }

    public boolean cancelKnockback() {
        return cancelKnockback.isToggled() && currentBlock != null && RotationUtils.inRange(currentBlock, range.getInput());
    }

    private BlockPos[] getBedPos() {
        int range;
        priority:
        for (int n = range = (int) this.range.getInput(); range >= -n; --range) {
            for (int j = -n; j <= n; ++j) {
                for (int k = -n; k <= n; ++k) {
                    final BlockPos blockPos = new BlockPos(mc.thePlayer.posX + j, mc.thePlayer.posY + range, mc.thePlayer.posZ + k);
                    final IBlockState getBlockState = mc.theWorld.getBlockState(blockPos);
                    if (getBlockState.getBlock() == Blocks.bed && getBlockState.getValue((IProperty) BlockBed.PART) == BlockBed.EnumPartType.FOOT) {
                        float fov = (float) this.fov.getInput();
                        if (fov != 360 && !Utils.inFov(fov, blockPos)) {
                            continue priority;
                        }
                        return new BlockPos[]{blockPos, blockPos.offset((EnumFacing) getBlockState.getValue((IProperty) BlockBed.FACING))};
                    }
                }
            }
        }
        return null;
    }

    private void setRots(ClientRotationEvent e) {
        float[] rotations = RotationUtils.getRotations(currentBlock == null ? rotateLastBlock : currentBlock, e.getYaw(), e.getPitch());
        e.setYaw(RotationUtils.applyVanilla(rotations[0]));
        e.setPitch(rotations[1]);
        if (Technicality.debug) {
            Utils.sendModuleMessage(this, "&7rotating (&3" + mc.thePlayer.ticksExisted + "&7).");
        }
    }

    public BlockPos getBestBlock(BlockPos[] positions, boolean getSurrounding) {
        if (positions == null || positions.length == 0) {
            return null;
        }
        HashMap<BlockPos, double[]> blockMap = new HashMap<>();
        for (BlockPos pos : positions) {
            if (pos == null) {
                continue;
            }
            if (getSurrounding) {
                for (EnumFacing enumFacing : EnumFacing.values()) {
                    if (enumFacing == EnumFacing.DOWN) {
                        continue;
                    }
                    BlockPos offset = pos.offset(enumFacing);
                    if (Arrays.asList(positions).contains(offset)) {
                        continue;
                    }
                    if (!RotationUtils.inRange(offset, range.getInput())) {
                        continue;
                    }
                    double efficiency = getEfficiency(offset);
                    double distance = mc.thePlayer.getDistanceSqToCenter(offset);
                    blockMap.put(offset, new double[]{distance, efficiency});
                }
            }
            else {
                if (!RotationUtils.inRange(pos, range.getInput())) {
                    continue;
                }
                double efficiency = getEfficiency(pos);
                double distance = mc.thePlayer.getDistanceSqToCenter(pos);
                blockMap.put(pos, new double[]{distance, efficiency});
            }
        }
        List<Map.Entry<BlockPos, double[]>> sortedByDistance = sortByDistance(blockMap);
        List<Map.Entry<BlockPos, double[]>> sortedByEfficiency = sortByEfficiency(sortedByDistance);
        List<Map.Entry<BlockPos, double[]>> sortedByPreviousBlocks = sortByPreviousBlocks(sortedByEfficiency);
        return sortedByPreviousBlocks.isEmpty() ? null : sortedByPreviousBlocks.get(0).getKey();
    }

    private List<Map.Entry<BlockPos, double[]>> sortByDistance(HashMap<BlockPos, double[]> blockMap) {
        List<Map.Entry<BlockPos, double[]>> list = new ArrayList<>(blockMap.entrySet());
        list.sort(Comparator.comparingDouble(entry -> entry.getValue()[0]));
        return list;
    }

    private List<Map.Entry<BlockPos, double[]>> sortByEfficiency(List<Map.Entry<BlockPos, double[]>> blockList) {
        blockList.sort((entry1, entry2) -> Double.compare(entry2.getValue()[1], entry1.getValue()[1]));
        return blockList;
    }

    private List<Map.Entry<BlockPos, double[]>> sortByPreviousBlocks(List<Map.Entry<BlockPos, double[]>> blockList) {
        blockList.sort((entry1, entry2) -> {
            boolean isEntry1Previous = entry1.getKey().equals(previousBlockBroken);
            boolean isEntry2Previous = entry2.getKey().equals(previousBlockBroken);
            if (isEntry1Previous && !isEntry2Previous) {
                return -1;
            }
            if (!isEntry1Previous && isEntry2Previous) {
                return 1;
            }
            return 0;
        });
        return blockList;
    }

    private double getEfficiency(BlockPos pos) {
        Block block = BlockUtils.getBlock(pos);
        ItemStack tool = (mode.getInput() == 2 && Utils.getTool(block) != -1) ? mc.thePlayer.inventory.getStackInSlot(Utils.getTool(block)) : mc.thePlayer.getHeldItem();
        boolean ignoreSlow = false;
        double efficiency = BlockUtils.getBlockHardness(block, tool, false, ignoreSlow);

        if (breakProgressMap.get(pos) != null) {
            efficiency = breakProgressMap.get(pos);
        }

        return efficiency;
    }

    private void reset(boolean resetSlot, boolean stopAutoblock) {
        if (resetSlot) {
            resetSlot();
        }
        breakProgress = 0;
        breakProgressMap.clear();
        lastSlot = -1;
        vanillaProgress = 0;
        lastProgress = 0;
        if (stopAutoblock) {
            this.stopAutoblock = false;
        }
        rotateLastBlock = null;
        firstStop = false;
        if (isBreaking) {
            ModuleUtils.isBreaking = false;
            isBreaking = false;
        }
        breakTick = false;
        currentBlock = null;
        nearestBlock = null;
        delayStop = false;
    }

    public void setPacketSlot(int slot) {
        if (slot == -1) {
            return;
        }
        Technicality.packetsHandler.updateSlot(slot);
        stopAutoblock = true;
    }

    private void startBreak(ClientRotationEvent e ,BlockPos blockPos) {
        setRots(e);
        packetPos = blockPos;
        startPacket = true;
        isBreaking = true;
        breakTick = true;

    }

    private void stopBreak(ClientRotationEvent e, BlockPos blockPos) {
        setRots(e);
        packetPos = blockPos;
        stopPacket = true;
        isBreaking = false;
        breakTick = true;
    }

    private void swing() {
        if (!silentSwing.isToggled()) {
            mc.thePlayer.swingItem();
        }
        else {
            mc.thePlayer.sendQueue.addToSendQueue(new C0APacketAnimation());
        }
    }

    private void breakBlock(ClientRotationEvent e, BlockPos blockPos) {
        if (blockPos == null) {
            reset(true, true);
            return;
        }
        lastBlock = blockPos;
        float fov = (float) this.fov.getInput();
        if (fov != 360 && !Utils.inFov(fov, blockPos)) {
            return;
        }
        if (onlyWhileVisible.isToggled() && (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || !mc.objectMouseOver.getBlockPos().equals(blockPos))) {
            return;
        }
        if (BlockUtils.replaceable(currentBlock == null ? blockPos : currentBlock)) {
            reset(true, true);
            return;
        }
        Block block = BlockUtils.getBlock(blockPos);
        currentBlock = blockPos;
        if ((breakProgress <= 0 || breakProgress >= 1) && mode.getInput() == 2 && !firstStop) {
            firstStop = true;
            stopAutoblock = delayStop = true;
            setRots(e);
            return;
        }
        if (mode.getInput() == 2 || mode.getInput() == 0) {
            if (breakProgress == 0) {
                resetSlot();
                if (mode.getInput() == 0) {
                    setSlot(Utils.getTool(block));
                }
                startBreak(e, blockPos);
            }
            else if (breakProgress >= 1) {
                if (mode.getInput() == 2) {
                    setPacketSlot(Utils.getTool(block));
                }
                stopBreak(e, blockPos);
                previousBlockBroken = currentBlock;
                reset(false, false);
                Iterator<Map.Entry<BlockPos, Float>> iterator = breakProgressMap.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<BlockPos, Float> entry = iterator.next();
                    if (entry.getKey().equals(blockPos)) {
                        iterator.remove();
                    }
                }
                if (!disableBreakEffects.isToggled()) {
                    mc.playerController.onPlayerDestroyBlock(blockPos, EnumFacing.UP);
                }
                rotateLastBlock = previousBlockBroken;
                return;
            }
            else {
                if (mode.getInput() == 0) {

                }
            }
            boolean ignoreSlow = false;
            double progress = vanillaProgress = (float) (BlockUtils.getBlockHardness(block, (mode.getInput() == 2 && Utils.getTool(block) != -1) ? mc.thePlayer.inventory.getStackInSlot(Utils.getTool(block)) : mc.thePlayer.getHeldItem(), false, ignoreSlow) * breakSpeed.getInput());
            if (lastProgress != 0 && breakProgress >= lastProgress - vanillaProgress) {
                if (breakProgress >= lastProgress) {
                    if (mode.getInput() == 2) {
                        if (Technicality.debug) {
                            Utils.sendModuleMessage(this, "&7setting slot &7(&b" + mc.thePlayer.ticksExisted + "&7)");
                        }
                        setPacketSlot(Utils.getTool(block));
                    }
                }
            }
            breakProgress += progress;
            breakProgressMap.put(blockPos, breakProgress);
            if (breakProgress > 0) firstStop = false;
            if (sendAnimations.isToggled()) {
                mc.theWorld.sendBlockBreakProgress(mc.thePlayer.getEntityId(), blockPos, (int) ((breakProgress * 10) - 1));
            }
            lastProgress = 0;
            while (lastProgress + progress < 1) {
                lastProgress += progress;
            }
        }
        else if (mode.getInput() == 1) {
            swing();
            startBreak(e, blockPos);
            setSlot(Utils.getTool(block));
            stopBreak(e, blockPos);
        }
    }

    private void setSlot(int slot) {
        if (slot == -1 || slot == mc.thePlayer.inventory.currentItem) {
            return;
        }
        if (lastSlot == -1) {
            lastSlot = mc.thePlayer.inventory.currentItem;
        }
        mc.thePlayer.inventory.currentItem = slot;
    }

    private boolean isCovered(BlockPos blockPos) {
        for (EnumFacing enumFacing : EnumFacing.values()) {
            BlockPos offset = blockPos.offset(enumFacing);
            if (BlockUtils.replaceable(offset) || BlockUtils.notFull(BlockUtils.getBlock(offset)) ) {
                return false;
            }
        }
        return true;
    }
}