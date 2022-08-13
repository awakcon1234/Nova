package xyz.xenondevs.nmsutils.network

import io.netty.buffer.Unpooled
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.protocol.game.ClientboundBossEventPacket
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.resources.ResourceLocation
import xyz.xenondevs.nmsutils.internal.util.ReflectionRegistry
import xyz.xenondevs.nmsutils.internal.util.toPackedByte
import xyz.xenondevs.nmsutils.bossbar.operation.BossBarOperation
import java.util.*

fun ClientboundPlaceGhostRecipePacket(containerId: Int, resourceLocation: String): ClientboundPlaceGhostRecipePacket {
    val buffer = FriendlyByteBuf(Unpooled.buffer())
    buffer.writeByte(containerId)
    buffer.writeResourceLocation(ResourceLocation(resourceLocation))
    return ClientboundPlaceGhostRecipePacket(buffer)
}

fun ClientboundSetPassengersPacket(vehicle: Int, passengers: IntArray): ClientboundSetPassengersPacket {
    val buffer = FriendlyByteBuf(Unpooled.buffer())
    buffer.writeVarInt(vehicle)
    buffer.writeVarIntArray(passengers)
    return ClientboundSetPassengersPacket(buffer)
}

fun ClientboundRotateHeadPacket(entity: Int, yaw: Float): ClientboundRotateHeadPacket {
    val buffer = FriendlyByteBuf(Unpooled.buffer())
    buffer.writeVarInt(entity)
    buffer.writeByte(yaw.toPackedByte().toInt())
    return ClientboundRotateHeadPacket(buffer)
}

fun ClientboundSetEntityDataPacket(id: Int, packedData: List<SynchedEntityData.DataItem<*>>?): ClientboundSetEntityDataPacket {
    val buffer = FriendlyByteBuf(Unpooled.buffer())
    buffer.writeVarInt(id)
    SynchedEntityData.pack(packedData, buffer)
    return ClientboundSetEntityDataPacket(buffer)
}

fun ClientboundBossEventPacket(id: UUID, operation: BossBarOperation): ClientboundBossEventPacket {
    return ReflectionRegistry.CLIENTBOUND_BOSS_EVENT_PACKET_CONSTRUCTOR.newInstance(id, operation.toNMS())
}

fun ServerboundPlaceRecipePacket(containerId: Int, recipe: ResourceLocation, shiftDown: Boolean): ServerboundPlaceRecipePacket {
    val buffer = FriendlyByteBuf(Unpooled.buffer())
    buffer.writeByte(containerId)
    buffer.writeResourceLocation(recipe)
    buffer.writeBoolean(shiftDown)
    return ServerboundPlaceRecipePacket(buffer)
}