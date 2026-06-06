package com.gtnewhorizons.galaxia.registry.items.tether;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;

import com.gtnewhorizons.galaxia.core.config.ConfigPlayer;

public class KineticTether {

    public static double getBreakDistance() {
        return ConfigPlayer.ConfigTether.maxTetherLength * ConfigPlayer.ConfigTether.maxStretchMultiplier;
    }

    public static void applyPhysics(EntityPlayer player, TetherData data) {

        if (!data.tetherActive || player.worldObj.provider.dimensionId != data.anchorDim) {

            data.tetherActive = false;
            data.propulsionActive = false;
            return;
        }

        double maxLength = ConfigPlayer.ConfigTether.maxTetherLength;
        double breakDist = getBreakDistance();

        Vec3 playerPos = Vec3.createVectorHelper(player.posX, player.posY, player.posZ);

        Vec3 anchorPos = Vec3.createVectorHelper(data.anchorX, data.anchorY, data.anchorZ);

        Vec3 delta = Vec3.createVectorHelper(
            playerPos.xCoord - anchorPos.xCoord,
            playerPos.yCoord - anchorPos.yCoord,
            playerPos.zCoord - anchorPos.zCoord);

        double dist = delta.lengthVector();

        if (dist > breakDist) {
            data.tetherActive = false;
            data.propulsionActive = false;
            return;
        }

        Vec3 velocity = Vec3.createVectorHelper(player.motionX, player.motionY, player.motionZ);

        Vec3 radialDir = delta.normalize();

        double radialSpeed = velocity.xCoord * radialDir.xCoord + velocity.yCoord * radialDir.yCoord
            + velocity.zCoord * radialDir.zCoord;

        if (radialSpeed > 0) {

            double damping = ConfigPlayer.ConfigTether.outwardDamping;

            Vec3 outwardComponent = Vec3.createVectorHelper(
                radialDir.xCoord * radialSpeed,
                radialDir.yCoord * radialSpeed,
                radialDir.zCoord * radialSpeed);

            Vec3 dampedOutward = Vec3.createVectorHelper(
                outwardComponent.xCoord * damping,
                outwardComponent.yCoord * damping,
                outwardComponent.zCoord * damping);

            velocity = Vec3.createVectorHelper(
                velocity.xCoord - outwardComponent.xCoord + dampedOutward.xCoord,
                velocity.yCoord - outwardComponent.yCoord + dampedOutward.yCoord,
                velocity.zCoord - outwardComponent.zCoord + dampedOutward.zCoord);
        }

        if (dist > maxLength) {

            double excess = dist - maxLength;

            double inwardAssist = ConfigPlayer.ConfigTether.inwardAssist;

            velocity = velocity.addVector(
                -radialDir.xCoord * inwardAssist,
                -radialDir.yCoord * inwardAssist,
                -radialDir.zCoord * inwardAssist);

            double forceMagnitude = excess * ConfigPlayer.ConfigTether.elasticityConst;

            velocity = velocity.addVector(
                -radialDir.xCoord * forceMagnitude,
                -radialDir.yCoord * forceMagnitude,
                -radialDir.zCoord * forceMagnitude);
        }

        if (data.propulsionActive) {

            double force = ConfigPlayer.ConfigTether.propulsionForce;

            Vec3 look = player.getLook(1.0F);

            velocity = velocity.addVector(look.xCoord * force, look.yCoord * force, look.zCoord * force);
        }

        player.motionX = velocity.xCoord;
        player.motionY = velocity.yCoord;
        player.motionZ = velocity.zCoord;
    }
}
