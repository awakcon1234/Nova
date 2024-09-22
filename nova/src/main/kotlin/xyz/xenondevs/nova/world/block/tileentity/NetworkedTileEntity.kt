@file:Suppress("LeakingThis")

package xyz.xenondevs.nova.world.block.tileentity

import org.bukkit.block.BlockFace
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.collections.enumMap
import xyz.xenondevs.commons.collections.enumSet
import xyz.xenondevs.commons.provider.Provider
import xyz.xenondevs.invui.inventory.VirtualInventory
import xyz.xenondevs.nova.context.Context
import xyz.xenondevs.nova.context.intention.DefaultContextIntentions.BlockBreak
import xyz.xenondevs.nova.context.intention.DefaultContextIntentions.BlockPlace
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.CUBE_FACES
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.state.property.DefaultBlockStateProperties
import xyz.xenondevs.nova.world.block.tileentity.network.NetworkManager
import xyz.xenondevs.nova.world.block.tileentity.network.node.EndPointDataHolder
import xyz.xenondevs.nova.world.block.tileentity.network.node.NetworkEndPoint
import xyz.xenondevs.nova.world.block.tileentity.network.node.NetworkNode
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType
import xyz.xenondevs.nova.world.block.tileentity.network.type.energy.holder.DefaultEnergyHolder
import xyz.xenondevs.nova.world.block.tileentity.network.type.energy.holder.EnergyHolder
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.container.NetworkedFluidContainer
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.holder.DefaultFluidHolder
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.holder.FluidHolder
import xyz.xenondevs.nova.world.block.tileentity.network.type.item.holder.DefaultItemHolder
import xyz.xenondevs.nova.world.block.tileentity.network.type.item.holder.ItemHolder
import xyz.xenondevs.nova.world.block.tileentity.network.type.item.inventory.NetworkedInventory
import xyz.xenondevs.nova.world.block.tileentity.network.type.item.inventory.NetworkedMultiVirtualInventory
import xyz.xenondevs.nova.world.block.tileentity.network.type.item.inventory.NetworkedVirtualInventory
import java.util.*

abstract class NetworkedTileEntity(
    pos: BlockPos,
    blockState: NovaBlockState,
    data: Compound
) : TileEntity(pos, blockState, data), NetworkEndPoint {
    
    @Volatile
    final override var isValid = false
    
    final override val holders: MutableSet<EndPointDataHolder> = HashSet()
    override val linkedNodes: Set<NetworkNode> = emptySet()
    
    init {
        // legacy conversion
        DefaultEnergyHolder.tryConvertLegacy(this)?.let { storeData("energyHolder", it) }
        DefaultItemHolder.tryConvertLegacy(this)?.let { storeData("itemHolder", it) }
        DefaultFluidHolder.tryConvertLegacy(this)?.let { storeData("fluidHolder", it) }
    }
    
    /**
     * Retrieves the [EnergyHolder] previously stored or creates a new one and registers it in the [holders] map.
     *
     * The energy capacity is limited by the [maxEnergy] provider and the [allowedConnectionType] determines
     * whether energy can be inserted, extracted, or both.
     *
     * The [blockedSides] defines which sides of this tile-entity can never be used for energy transfer.
     *
     * If the [EnergyHolder] is created for the first time, [defaultConnectionConfig] is used to determine the
     * correct [NetworkConnectionType] for each side.
     */
    @JvmName("storedEnergyHolderBlockSide")
    fun storedEnergyHolder(
        maxEnergy: Provider<Long>,
        allowedConnectionType: NetworkConnectionType,
        blockedSides: Set<BlockSide>,
        defaultConnectionConfig: () -> Map<BlockFace, NetworkConnectionType> = { CUBE_FACES.associateWithTo(enumMap()) { allowedConnectionType } }
    ) = storedEnergyHolder(
        maxEnergy,
        allowedConnectionType,
        translateSidesToFaces(blockedSides),
        defaultConnectionConfig
    )
    
    /**
     * Retrieves the [EnergyHolder] previously stored or creates a new one and registers it in the [holders] map.
     *
     * The energy capacity is limited by the [maxEnergy] provider and the [allowedConnectionType] determines
     * whether energy can be inserted, extracted, or both.
     *
     * The [blockedFaces] define which faces of this tile-entity can never be used for energy transfer.
     *
     * If the [EnergyHolder] is created for the first time, [defaultConnectionConfig] is used to determine the
     * correct [NetworkConnectionType] for each side.
     */
    @JvmName("storedEnergyHolderBlockFace")
    fun storedEnergyHolder(
        maxEnergy: Provider<Long>,
        allowedConnectionType: NetworkConnectionType,
        blockedFaces: Set<BlockFace> = emptySet(),
        defaultConnectionConfig: () -> Map<BlockFace, NetworkConnectionType> = { CUBE_FACES.associateWithTo(enumMap()) { allowedConnectionType } }
    ): DefaultEnergyHolder {
        if (hasData("energyHolder")) {
            val holderCompound = retrieveDataOrNull<Compound>("energyHolder")!!
            val energy = holderCompound.get<Long>("energy")
            if (energy != null) {
                holderCompound.remove("energy")
                storeData("energy", energy, true)
            }
        }
        
        val holder = DefaultEnergyHolder(
            storedValue("energyHolder", ::Compound),
            storedValue("energy", true) { 0L },
            maxEnergy,
            allowedConnectionType,
            blockedFaces,
            defaultConnectionConfig
        )
        holders += holder
        return holder
    }
    
    /**
     * Retrieves the [ItemHolder] previously stored or creates a new one, registers it in the [holders] map,
     * and adds drop providers for [ItemHolder.insertFilters] and [ItemHolder.extractFilters].
     *
     * The item holder uses the inventories and connection types provided ([inventory], [inventories]).
     *
     * The [blockedSides] define which sides of the tile-entity can never be used for item transfer.
     *
     * If the [ItemHolder] is created for the first time, [defaultInventoryConfig] and [defaultConnectionConfig]
     * are used to determine the correct [VirtualInventory] and [NetworkConnectionType] for each side.
     * If [defaultInventoryConfig] is `null`, the merged inventory will be used for all sides.
     *
     * If [defaultConnectionConfig] is `null`, each side will be assigned the highest possible connection type.
     */
    @JvmName("storedItemHolderBlockSide")
    fun storedItemHolder(
        inventory: Pair<VirtualInventory, NetworkConnectionType>,
        vararg inventories: Pair<VirtualInventory, NetworkConnectionType>,
        blockedSides: Set<BlockSide>,
        defaultInventoryConfig: (() -> Map<BlockFace, VirtualInventory>)? = null,
        defaultConnectionConfig: (() -> Map<BlockFace, NetworkConnectionType>)? = null,
    ) = storedItemHolder(
        inventory,
        inventories = inventories,
        translateSidesToFaces(blockedSides),
        defaultInventoryConfig,
        defaultConnectionConfig
    )
    
    /**
     * Retrieves the [ItemHolder] previously stored or creates a new one, registers it in the [holders] map,
     * and adds drop providers for [ItemHolder.insertFilters] and [ItemHolder.extractFilters].
     *
     * The item holder uses the inventories and connection types provided ([inventory], [inventories]).
     *
     * The [blockedFaces] define which faces of the tile-entity can never be used for item transfer.
     *
     * If the [ItemHolder] is created for the first time, [defaultInventoryConfig] and [defaultConnectionConfig]
     * are used to determine the correct [VirtualInventory] and [NetworkConnectionType] for each side.
     * If [defaultInventoryConfig] is `null`, the merged inventory will be used for all sides.
     *
     * If [defaultConnectionConfig] is `null`, each side will be assigned the highest possible connection type.
     */
    @JvmName("storedItemHolderBlockFace")
    fun storedItemHolder(
        inventory: Pair<VirtualInventory, NetworkConnectionType>,
        vararg inventories: Pair<VirtualInventory, NetworkConnectionType>,
        blockedFaces: Set<BlockFace> = emptySet(),
        defaultInventoryConfig: (() -> Map<BlockFace, VirtualInventory>)? = null,
        defaultConnectionConfig: (() -> Map<BlockFace, NetworkConnectionType>)? = null,
    ): DefaultItemHolder {
        val allInventories: Map<VirtualInventory, NetworkConnectionType> =
            buildMap { this += inventory; this += inventories }
        val availableInventories: MutableMap<UUID, NetworkedInventory> =
            allInventories.keys.associateTo(HashMap()) { it.uuid to NetworkedVirtualInventory(it) }
        val allowedConnectionTypes: MutableMap<NetworkedInventory, NetworkConnectionType> =
            allInventories.mapKeysTo(HashMap()) { (vi, _) -> availableInventories[vi.uuid]!! }
        
        val mergedInventory = NetworkedMultiVirtualInventory(DefaultItemHolder.ALL_INVENTORY_UUID, allInventories)
        availableInventories[DefaultItemHolder.ALL_INVENTORY_UUID] = mergedInventory
        allowedConnectionTypes[mergedInventory] = NetworkConnectionType.of(allowedConnectionTypes.values)
        
        val holder = DefaultItemHolder(
            storedValue("itemHolder", ::Compound),
            allowedConnectionTypes,
            mergedInventory,
            blockedFaces,
            // map from VirtualInventory to NetworkedInventory or use mergedInventory for all sides
            defaultInventoryConfig
                ?.let { { it.invoke().mapValues { (_, vi) -> availableInventories[vi.uuid]!! } } }
                ?: { CUBE_FACES.associateWithTo(enumMap()) { mergedInventory } },
            defaultConnectionConfig
        )
        registerItemHolder(holder)
        return holder
    }
    
    /**
     * Retrieves the [ItemHolder] previously stored or creates a new one, registers it in the [holders] map,
     * and adds drop providers for [ItemHolder.insertFilters] and [ItemHolder.extractFilters].
     *
     * The item holder uses the inventories and connection types provided ([inventory], [inventories]).
     *
     * The [blockedSides] define which sides of the tile-entity can never be used for item transfer.
     *
     * If the [ItemHolder] is created for the first time, [defaultInventoryConfig] and [defaultConnectionConfig]
     * are used to determine the correct [NetworkedInventory] and [NetworkConnectionType] for each side.
     *
     * If [defaultConnectionConfig] is `null`, each side will be assigned the highest possible connection type.
     */
    @JvmName("storedItemHolderBlockSide")
    fun storedItemHolder(
        inventory: Pair<NetworkedInventory, NetworkConnectionType>,
        vararg inventories: Pair<NetworkedInventory, NetworkConnectionType>,
        mergedInventory: NetworkedInventory? = null,
        blockedSides: Set<BlockSide>,
        defaultInventoryConfig: () -> Map<BlockFace, NetworkedInventory> = { CUBE_FACES.associateWithTo(enumMap()) { inventory.first } },
        defaultConnectionConfig: (() -> Map<BlockFace, NetworkConnectionType>)? = null
    ) = storedItemHolder(
        inventory,
        inventories = inventories,
        mergedInventory,
        translateSidesToFaces(blockedSides),
        defaultInventoryConfig,
        defaultConnectionConfig
    )
    
    /**
     * Retrieves the [ItemHolder] previously stored or creates a new one, registers it in the [holders] map,
     * and adds drop providers for [ItemHolder.insertFilters] and [ItemHolder.extractFilters].
     *
     * The item holder uses the inventories and connection types provided ([inventory], [inventories]).
     *
     * The [blockedFaces] define which faces of the tile-entity can never be used for item transfer.
     *
     * If the [ItemHolder] is created for the first time, [defaultInventoryConfig] and [defaultConnectionConfig]
     * are used to determine the correct [NetworkedInventory] and [NetworkConnectionType] for each side.
     *
     * If [defaultConnectionConfig] is `null`, each side will be assigned the highest possible connection type.
     */
    @JvmName("storedItemHolderBlockFace")
    fun storedItemHolder(
        inventory: Pair<NetworkedInventory, NetworkConnectionType>,
        vararg inventories: Pair<NetworkedInventory, NetworkConnectionType>,
        mergedInventory: NetworkedInventory? = null,
        blockedFaces: Set<BlockFace> = emptySet(),
        defaultInventoryConfig: () -> Map<BlockFace, NetworkedInventory> = { CUBE_FACES.associateWithTo(enumMap()) { inventory.first } },
        defaultConnectionConfig: (() -> Map<BlockFace, NetworkConnectionType>)? = null
    ): DefaultItemHolder {
        val allInventories = buildMap { this += inventory; this += inventories }
        
        val holder = DefaultItemHolder(
            storedValue("itemHolder", ::Compound),
            allInventories,
            mergedInventory,
            blockedFaces,
            defaultInventoryConfig,
            defaultConnectionConfig
        )
        registerItemHolder(holder)
        return holder
    }
    
    /**
     * Registers the given [holder] to [holders] and adds drop providers for [ItemHolder.insertFilters]
     * and [ItemHolder.extractFilters].
     */
    private fun registerItemHolder(holder: ItemHolder) {
        holders += holder
        dropProvider {
            val itemFilters = ArrayList<ItemStack>()
            for (filter in holder.insertFilters.values)
                itemFilters += filter.toItemStack()
            for (filter in holder.extractFilters.values)
                itemFilters += filter.toItemStack()
            itemFilters
        }
    }
    
    /**
     * Retrieves the [FluidHolder] previously stored or creates a new one and registers it in the [holders] map.
     *
     * The fluid holder uses the containers and connection types provided ([container], [containers]).
     *
     * The [blockedSides] define which sides of the tile-entity can never be used for fluid transfer.
     *
     * If the [FluidHolder] is created for the first time, [defaultContainerConfig] and [defaultConnectionConfig]
     * are used to determine the correct [NetworkedFluidContainer] and [NetworkConnectionType] for each side.
     *
     * If [defaultConnectionConfig] is `null`, each side will be assigned the highest possible connection type.
     */
    @JvmName("storedFluidHolderBlockSide")
    fun storedFluidHolder(
        container: Pair<NetworkedFluidContainer, NetworkConnectionType>,
        vararg containers: Pair<NetworkedFluidContainer, NetworkConnectionType>,
        blockedSides: Set<BlockSide>,
        defaultContainerConfig: () -> MutableMap<BlockFace, NetworkedFluidContainer> = { CUBE_FACES.associateWithTo(enumMap()) { container.first } },
        defaultConnectionConfig: (() -> EnumMap<BlockFace, NetworkConnectionType>)? = null
    ) = storedFluidHolder(
        container,
        containers = containers,
        translateSidesToFaces(blockedSides),
        defaultContainerConfig,
        defaultConnectionConfig
    )
    
    
    /**
     * Retrieves the [FluidHolder] previously stored or creates a new one and registers it in the [holders] map.
     *
     * The fluid holder uses the containers and connection types provided ([container], [containers]).
     *
     * The [blockedFaces] define which faces of the tile-entity can never be used for fluid transfer.
     *
     * If the [FluidHolder] is created for the first time, [defaultContainerConfig] and [defaultConnectionConfig]
     * are used to determine the correct [NetworkedFluidContainer] and [NetworkConnectionType] for each side.
     *
     * If [defaultConnectionConfig] is `null`, each side will be assigned the highest possible connection type.
     */
    @JvmName("storedFluidHolderBlockFace")
    fun storedFluidHolder(
        container: Pair<NetworkedFluidContainer, NetworkConnectionType>,
        vararg containers: Pair<NetworkedFluidContainer, NetworkConnectionType>,
        blockedFaces: Set<BlockFace> = emptySet(),
        defaultContainerConfig: () -> MutableMap<BlockFace, NetworkedFluidContainer> = { CUBE_FACES.associateWithTo(enumMap()) { container.first } },
        defaultConnectionConfig: (() -> EnumMap<BlockFace, NetworkConnectionType>)? = null
    ): DefaultFluidHolder {
        val fluidHolder = DefaultFluidHolder(
            storedValue("fluidHolder", ::Compound),
            buildMap { this += container; this += containers },
            blockedFaces,
            defaultContainerConfig,
            defaultConnectionConfig
        )
        holders += fluidHolder
        return fluidHolder
    }
    
    private fun translateSidesToFaces(sides: Set<BlockSide>): Set<BlockFace> {
        val facing = blockState[DefaultBlockStateProperties.FACING] ?: BlockFace.NORTH
        return sides.mapTo(enumSet()) { it.getBlockFace(facing) }
    }
    
    override fun handleEnable() {
        super.handleEnable()
        
        // legacy conversion
        if (hasData("connectedNodes") || hasData("networks")) {
            removeData("connectedNodes")
            removeData("networks")
            
            NetworkManager.queueAddEndPoint(this)
        }
        
        isValid = true
    }
    
    override fun handlePlace(ctx: Context<BlockPlace>) {
        super.handlePlace(ctx)
        NetworkManager.queueAddEndPoint(this)
        isValid = true
    }
    
    override fun handleDisable() {
        super.handleDisable()
        isValid = false
    }
    
    override fun handleBreak(ctx: Context<BlockBreak>) {
        super.handleBreak(ctx)
        NetworkManager.queueRemoveEndPoint(this)
        isValid = false
    }
    
}