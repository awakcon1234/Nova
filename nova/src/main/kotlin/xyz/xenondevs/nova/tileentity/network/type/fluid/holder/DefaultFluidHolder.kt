package xyz.xenondevs.nova.tileentity.network.type.fluid.holder

import org.bukkit.block.BlockFace
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.cbf.provider.entry
import xyz.xenondevs.commons.collections.enumMap
import xyz.xenondevs.commons.collections.toEnumMap
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.commons.provider.mutable.mapNonNull
import xyz.xenondevs.commons.provider.mutable.orElseLazily
import xyz.xenondevs.nova.tileentity.network.type.NetworkConnectionType
import xyz.xenondevs.nova.tileentity.network.type.fluid.container.NetworkedFluidContainer
import xyz.xenondevs.nova.util.CUBE_FACES
import java.util.*

/**
 * The default [FluidHolder] implementation.
 *
 * @param compound the [Compound] for data storage and retrieval
 * @param containers all available [NetworkedFluidContainers][NetworkedFluidContainer] and their allowed [NetworkConnectionType]
 * @param defaultContainerConfig the default ([BlockFace], [NetworkedFluidContainer]) to be used if no configuration is stored
 * @param defaultConnectionConfig the default ([BlockFace], [NetworkConnectionType]) to be used if no configuration is stored
 */
class DefaultFluidHolder(
    compound: Provider<Compound>,
    override val containers: Map<NetworkedFluidContainer, NetworkConnectionType>,
    defaultContainerConfig: () -> Map<BlockFace, NetworkedFluidContainer>,
    defaultConnectionConfig: () -> Map<BlockFace, NetworkConnectionType>
) : FluidHolder {
    
    init {
        if (containers.isEmpty())
            throw IllegalArgumentException("availableContainers must not be empty")
    }
    
    private val uuidToContainer: Map<UUID, NetworkedFluidContainer> =
        containers.keys.associateByTo(HashMap()) { it.uuid }
    
    override val containerConfig: MutableMap<BlockFace, NetworkedFluidContainer>
        by compound.entry<Map<BlockFace, UUID>>("containerConfig")
            .mapNonNull(
                { it.mapValuesTo(enumMap()) { (_, uuid) -> uuidToContainer[uuid]!! } },
                { it.mapValuesTo(enumMap()) { (_, container) -> container.uuid } }
            ).orElseLazily { defaultContainerConfig().toEnumMap() }
    
    override val connectionConfig: MutableMap<BlockFace, NetworkConnectionType> by
        compound.entry<MutableMap<BlockFace, NetworkConnectionType>>("connectionConfig")
            .orElseLazily { defaultConnectionConfig().toEnumMap() }
    
    override val channels: MutableMap<BlockFace, Int>
        by compound.entry<MutableMap<BlockFace, Int>>("channels")
            .orElseLazily(DEFAULT_CHANNEL_CONFIG)
    
    override val insertPriorities: MutableMap<BlockFace, Int>
        by compound.entry<MutableMap<BlockFace, Int>>("insertPriorities")
            .orElseLazily(DEFAULT_PRIORITIES)
    
    override val extractPriorities: MutableMap<BlockFace, Int>
        by compound.entry<MutableMap<BlockFace, Int>>("extractPriorities")
            .orElseLazily(DEFAULT_PRIORITIES)
    
    internal companion object {
        val DEFAULT_CONNECTION_CONFIG = { CUBE_FACES.associateWithTo(enumMap()) { NetworkConnectionType.NONE } }
        val DEFAULT_CHANNEL_CONFIG = { CUBE_FACES.associateWithTo(enumMap()) { 0 } }
        val DEFAULT_PRIORITIES = { CUBE_FACES.associateWithTo(enumMap()) { 50 } }
    }
    
}