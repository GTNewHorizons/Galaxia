package com.gtnewhorizons.galaxia.client.render.rockets;

import static com.gtnewhorizons.galaxia.api.GalaxiaAPI.LocationGalaxia;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.blueprint.RocketPartRegistry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.rocket.modules.RocketPartDef;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantry;
import com.gtnewhorizons.galaxia.registry.rocketmodules.tileentities.gantry.TileEntityGantryTerminal;

/**
 * Renderer to handle gantry rails and the modules travelling along them.
 * <p>
 * A module hangs from two hooks that run along the rail ahead of and behind its middle. The module simply spans the
 * two hooks, so it tilts and turns through corners and shafts by itself instead of following a set angle
 */
public class GantryRenderer extends TileEntitySpecialRenderer {

    private static final ResourceLocation HOOK_MODEL = LocationGalaxia("textures/model/gantry/hook.obj");
    private static final ResourceLocation HOOK_TEXTURE = LocationGalaxia("textures/model/gantry/hook.png");

    @Override
    public void renderTileEntityAt(TileEntity tileEntity, double x, double y, double z, float partialTicks) {
        if (!(tileEntity instanceof TileEntityGantry gantry)) return;

        // Terminals are full blocks with a recess for the rail, the block renderer draws them
        if (!(gantry instanceof TileEntityGantryTerminal)) {
            applyWorldLighting(gantry.getWorldObj(), gantry.xCoord, gantry.yCoord, gantry.zCoord);
            GL11.glPushMatrix();
            GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);
            GantryRailModels.render(
                gantry.getWorldObj(),
                gantry.xCoord,
                gantry.yCoord,
                gantry.zCoord,
                TileEntityGantry.connectionMask(gantry.getWorldObj(), gantry.xCoord, gantry.yCoord, gantry.zCoord));
            GL11.glPopMatrix();
        }

        if (gantry.clientModuleId != -1) renderModule(gantry, x, y, z, partialTicks);
    }

    private static void renderModule(TileEntityGantry gantry, double x, double y, double z, float partialTicks) {
        // TODO: clientModuleId -> clientPartInstance
        RocketPartDef def = RocketPartRegistry.instance()
            .get(gantry.clientModuleId);
        GantryPath path = GantryPath.of(gantry.clientPath);
        if (def == null || path == null) return;

        // Hooks sit a block in from the ends of the module, closer together on short ones
        double progress = gantry.getInterpolatedProgress(partialTicks);
        double half = Math.min(path.reach(), Math.max(1, def.height() - 1) / 2.0);
        GantryPath.Pose front = path.pose(progress + half);
        GantryPath.Pose back = path.pose(progress - half);

        GantryPath.Vec forward = front.position()
            .sub(back.position());
        if (forward.length() < 1e-3) forward = path.pose(progress)
            .tangent();
        forward = forward.normalize();
        GantryPath.Vec hang = front.normal()
            .add(back.normal());
        hang = hang.sub(forward.scale(hang.dot(forward)));
        if (hang.length() < 1e-3) hang = front.normal();
        hang = hang.normalize();

        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(HOOK_TEXTURE);
        renderHook(gantry, front);
        renderHook(gantry, back);

        // Module between the hooks, its top towards the front hook and its side towards the rail
        GantryPath.Vec centre = front.position()
            .add(back.position())
            .scale(0.5)
            .add(hang.scale(GantryPath.HOOK_DROP + def.width() / 2.0));
        GantryPath.Vec side = forward.cross(hang);
        // Lit where the module is, the gantry holding it can be a solid terminal with no light inside
        applyWorldLighting(
            gantry.getWorldObj(),
            gantry.xCoord + (int) Math.floor(centre.x() + 0.5),
            gantry.yCoord + (int) Math.floor(centre.y() + 0.5),
            gantry.zCoord + (int) Math.floor(centre.z() + 0.5));
        GL11.glPushMatrix();
        GL11.glTranslated(centre.x(), centre.y(), centre.z());
        GantryRailModels.multiply(side.x(), side.y(), side.z(), forward.x(), forward.y(), forward.z());
        ModuleModelRenderer.renderModel(def, 1f);
        GL11.glPopMatrix();

        GL11.glPopMatrix();
    }

    /**
     * Draws a hook at a point of the path, running along the rail with its hanger pointing to the module
     */
    private static void renderHook(TileEntityGantry gantry, GantryPath.Pose pose) {
        GantryPath.Vec position = pose.position();
        applyWorldLighting(
            gantry.getWorldObj(),
            gantry.xCoord + (int) Math.floor(position.x() + 0.5),
            gantry.yCoord + (int) Math.floor(position.y() + 0.5),
            gantry.zCoord + (int) Math.floor(position.z() + 0.5));
        GantryPath.Vec up = pose.normal()
            .scale(-1);
        GantryPath.Vec side = up.cross(pose.tangent());
        GL11.glPushMatrix();
        GL11.glTranslated(
            pose.position()
                .x(),
            pose.position()
                .y(),
            pose.position()
                .z());
        GantryRailModels.multiply(side.x(), side.y(), side.z(), up.x(), up.y(), up.z());
        ModelCache.get(HOOK_MODEL)
            .renderAll();
        GL11.glPopMatrix();
    }

    /**
     * Applies the world lightmap at a block so the model receives ambient light and shadow from its surroundings.
     * Inside solid blocks, where the light is zero, the brightest neighbour is used
     */
    private static void applyWorldLighting(World world, int x, int y, int z) {
        int brightness = world.getLightBrightnessForSkyBlocks(x, y, z, 0);
        if (world.getBlock(x, y, z)
            .isOpaqueCube()) {
            for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                int around = world
                    .getLightBrightnessForSkyBlocks(x + side.offsetX, y + side.offsetY, z + side.offsetZ, 0);
                if ((around >> 16) + (around & 0xFFFF) > (brightness >> 16) + (brightness & 0xFFFF))
                    brightness = around;
            }
        }

        int skyLight = (brightness >> 16) & 0xFFFF;
        int blockLight = brightness & 0xFFFF;

        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, blockLight, skyLight);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }
}
