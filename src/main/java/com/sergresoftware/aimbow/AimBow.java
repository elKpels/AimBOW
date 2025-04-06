package com.sergresoftware.aimbow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBow;
import net.minecraft.util.Vec3;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.List;

@Mod(modid = AimBow.MODID, name = AimBow.NAME, version = AimBow.VERSION)
public class AimBow {
    public static final String MODID = "aimbow";
    public static final String NAME = "AimBow";
    public static final String VERSION = "2.0";

    @Mod.Instance
    public static AimBow instance;

    private static final String KEY_CATEGORY = "key.categories.aimbow";
    private static final String KEY_NAME = "key.aimbow.toggle";
    public static KeyBinding keyToggle = new KeyBinding(KEY_NAME, Keyboard.KEY_F, KEY_CATEGORY);

    private boolean isEnabled = false;
    private EntityPlayer targetPlayer = null;
    private boolean keyPressedLastTick = false;
    private double lastMouseX, lastMouseY;
    private final double SMOOTH_THRESHOLD = 5.0; // Umbral para detectar movimiento suave
    private final double ROUGH_THRESHOLD = 20.0; // Umbral para detectar movimiento brusco

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        ClientRegistry.registerKeyBinding(keyToggle);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Inicialización si es necesaria
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;

        if (player == null || !isEnabled) return;

        if (player.getHeldItem() != null && player.getHeldItem().getItem() instanceof ItemBow && player.isUsingItem()) {
            if (targetPlayer != null) {
                smoothAdjustCameraToTarget(player, targetPlayer, 0.1);
            }
            drawTrajectory(player, event.partialTicks);
        }
    }

    private void drawTrajectory(EntityPlayer player, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();

        // Posición de la cámara (crosshair)
        double cameraX = mc.getRenderViewEntity().posX;
        double cameraY = mc.getRenderViewEntity().posY + mc.getRenderViewEntity().getEyeHeight();
        double cameraZ = mc.getRenderViewEntity().posZ;

        Vec3 startPosition = new Vec3(cameraX, cameraY, cameraZ);
        Vec3 lookVec = player.getLookVec();
        double initialVelocity = 1.5;
        double gravity = 0.05;
        int numSteps = 100;
        double timeStep = 0.1;

        // Predicción del movimiento del jugador objetivo
        Vec3 predictedPosition = targetPlayer != null ? predictTargetPosition(targetPlayer, 2.0) : null;

        if (predictedPosition == null) {
            return; // Salir si no hay una posición predicha válida
        }

        // Vector de dirección ajustado con la predicción
        Vec3 adjustedLookVec = predictedPosition.subtract(startPosition).normalize();

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GL11.glLineWidth(2.0F);
        GL11.glBegin(GL11.GL_LINE_STRIP);

        Vec3 previousPoint = startPosition;

        for (int i = 1; i <= numSteps; i++) {
            double time = i * timeStep;

            // Ajuste en altura para trayectorias parabólicas reales
            double x = initialVelocity * time * adjustedLookVec.xCoord;
            double y = initialVelocity * time * adjustedLookVec.yCoord - 0.5 * gravity * time * time;
            double z = initialVelocity * time * adjustedLookVec.zCoord;

            Vec3 currentPoint = startPosition.addVector(x, y, z);

            GL11.glVertex3d(previousPoint.xCoord, previousPoint.yCoord, previousPoint.zCoord);
            GL11.glVertex3d(currentPoint.xCoord, currentPoint.yCoord, currentPoint.zCoord);

            previousPoint = currentPoint;
        }

        GL11.glEnd();
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private Vec3 predictTargetPosition(EntityPlayer target, double predictionFactor) {
        if (target == null) {
            return null; // Verificar si el objetivo es nulo para evitar NullPointerException
        }

        // Velocidad del jugador objetivo
        Vec3 targetVelocity = new Vec3(
            target.posX - target.prevPosX,
            target.posY - target.prevPosY,
            target.posZ - target.prevPosZ
        );

        // Predicción de la posición futura, ajustando para la altura
        Vec3 predictedPosition = new Vec3(
            target.posX + targetVelocity.xCoord * predictionFactor,
            target.posY + targetVelocity.yCoord * predictionFactor - (0.5 * 0.05 * Math.pow(predictionFactor, 2)),
            target.posZ + targetVelocity.zCoord * predictionFactor
        );

        return predictedPosition;
    }

    private void smoothAdjustCameraToTarget(EntityPlayer player, EntityPlayer target, double smoothFactor) {
        if (player == null || target == null) {
            return; // Verificar si player o target es nulo para evitar NullPointerException
        }

        Vec3 playerPos = new Vec3(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 targetPos = predictTargetPosition(target, 2.0); // Usar la posición predicha

        if (targetPos == null) {
            return; // Salir si no hay una posición predicha válida
        }

        Vec3 direction = targetPos.subtract(playerPos).normalize();

        double desiredYaw = Math.atan2(direction.zCoord, direction.xCoord) * (180.0 / Math.PI) - 90.0;
        double desiredPitch = Math.toDegrees(Math.atan2(direction.yCoord, Math.sqrt(direction.xCoord * direction.xCoord + direction.zCoord * direction.zCoord)));

        player.rotationYaw = (float) (player.rotationYaw + (desiredYaw - player.rotationYaw) * smoothFactor);
        player.rotationPitch = (float) (player.rotationPitch + (desiredPitch - player.rotationPitch) * smoothFactor);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;

        if (player == null) return;

        // Comprobar si se presionó la tecla para activar/desactivar el mod
        if (Keyboard.isKeyDown(keyToggle.getKeyCode())) {
            if (!keyPressedLastTick) {
                isEnabled = !isEnabled;
                displayStatusMessage(isEnabled ? "Activado" : "Desactivado");
                keyPressedLastTick = true;
            }
        } else {
            keyPressedLastTick = false;
        }

        double currentMouseX = Mouse.getDX();
        double currentMouseY = Mouse.getDY();

        double deltaX = currentMouseX - lastMouseX;
        double deltaY = currentMouseY - lastMouseY;

        lastMouseX = currentMouseX;
        lastMouseY = currentMouseY;

        double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);

        if (isEnabled && distance > ROUGH_THRESHOLD) {
            isEnabled = false;
            displayStatusMessage("Desactivado por movimiento brusco");
        } else if (isEnabled && distance > SMOOTH_THRESHOLD) {
            updateClosestPlayerTarget(mc, player);
        }
    }

    private void updateClosestPlayerTarget(Minecraft mc, EntityPlayer player) {
        List<EntityPlayer> players = mc.theWorld.playerEntities;

        if (players == null || players.isEmpty()) {
            targetPlayer = null; // Verificar si la lista de jugadores es nula o está vacía
            return;
        }

        targetPlayer = null;
        double minDistance = Double.MAX_VALUE;

        for (EntityPlayer potentialTarget : players) {
            if (potentialTarget != player) {
                double distance = player.getDistanceToEntity(potentialTarget);
                if (distance < minDistance) {
                    minDistance = distance;
                    targetPlayer = potentialTarget;
                }
            }
        }
    }

    private void displayStatusMessage(String message) {
        Minecraft mc = Minecraft.getMinecraft();

        if (mc.thePlayer != null) { 
            mc.thePlayer.addChatMessage(new ChatComponentText("AimBow " + message));
        }
    }
}
