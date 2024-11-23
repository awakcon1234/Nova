package xyz.xenondevs.nova.world.block.tileentity.network

import xyz.xenondevs.nova.LOGGER
import xyz.xenondevs.nova.world.block.tileentity.network.node.NetworkNode
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkType
import java.util.*
import java.util.logging.Level
import kotlin.random.Random

/**
 * A cluster of [Networks][Network] that share at least one [NetworkNode].
 * Different clusters are capable of being ticked in parallel, as they do not share state.
 */
class NetworkCluster(val uuid: UUID, val networks: List<Network<*>>) {
    
    private val tickOffset: Int // load balancing
    private val groups = networks.groupBy { it.type }
        .map { (type, networks) -> createGroup(type, networks) }
    
    @Volatile
    private var isValid: Boolean = false
    
    init {
        val maxTickDelay = networks.maxOfOrNull { it.type.tickDelay } ?: 1
        tickOffset = Random.nextInt(maxTickDelay)
    }
    
    fun preTickSync(tick: Int) {
        updateIsValid()
        tickNetworks(tick, NetworkGroup<*>::preTickSync)
    }
    fun preTick(tick: Int) = tickNetworks(tick, NetworkGroup<*>::preTick)
    fun tick(tick: Int) = tickNetworks(tick, NetworkGroup<*>::tick)
    fun postTick(tick: Int) = tickNetworks(tick, NetworkGroup<*>::postTick)
    fun postTickSync(tick: Int) = tickNetworks(tick, NetworkGroup<*>::postTickSync)
    
    @Suppress("UNCHECKED_CAST", "USELESS_CAST")
    private fun <T : Network<T>> createGroup(type: NetworkType<*>, networks: List<*>): NetworkGroup<T> {
        val data = ImmutableNetworkGroupData(type as NetworkType<T>, networks as List<T>)
        return (type as NetworkType<T>).createGroup(data)
    }
    
    private fun updateIsValid() {
        for (network in networks) {
            if (!network.isValid()) {
                isValid = false
                return
            }           
        }
        
        isValid = true
    }
    
    private inline fun tickNetworks(tick: Int, tickFun: NetworkGroup<*>.() -> Unit) {
        // skip ticks for clusters that include invalid nodes
        if (!isValid)
            return
        
        for (group in groups) {
            val tickDelay = group.type.tickDelay
            if ((tick + tickOffset) % tickDelay == 0) {
                try {
                    tickFun.invoke(group)
                } catch (e: Exception) {
                    LOGGER.log(Level.SEVERE, "An exception occurred trying to tick $group in cluster $this", e)
                }
            }
        }
    }
    
    override fun toString(): String {
        return "NetworkCluster(uuid=$uuid, networks=$networks)"
    }
    
}

/**
 * A group of [ProtoNetworks][ProtoNetwork] that share at least one [NetworkNode].
 *
 * [ProtoNetworkClusters][ProtoNetworkCluster] are used to build [NetworkClusters][NetworkCluster],
 * which are groups of [Networks][Network] that are ticked together.
 */
class ProtoNetworkCluster : Iterable<ProtoNetwork<*>> {
    
    val uuid: UUID = UUID.randomUUID()
    private val networks = HashSet<ProtoNetwork<*>>()
    
    lateinit var cluster: NetworkCluster
    var dirty = true
    
    operator fun plusAssign(network: ProtoNetwork<*>) {
        if (networks.add(network))
            dirty = true
    }
    
    operator fun plusAssign(networks: Iterable<ProtoNetwork<*>>) {
        if (this.networks.addAll(networks))
            dirty = true
    }
    
    operator fun plusAssign(other: ProtoNetworkCluster?) {
        if (other == null)
            return
        
        if (networks.addAll(other.networks))
            dirty = true
    }
    
    operator fun minusAssign(network: ProtoNetwork<*>) {
        if (networks.remove(network))
            dirty = true
    }
    
    operator fun contains(network: ProtoNetwork<*>) =
        network in networks
    
    override operator fun iterator() =
        networks.iterator()
    
}