package xyz.xenondevs.nova.resources.builder.basepack

import org.bukkit.Material
import xyz.xenondevs.commons.provider.immutable.map
import xyz.xenondevs.nova.LOGGER
import xyz.xenondevs.nova.config.MAIN_CONFIG
import xyz.xenondevs.nova.resources.ResourcePath
import xyz.xenondevs.nova.resources.builder.ResourcePackBuilder
import xyz.xenondevs.nova.resources.builder.basepack.merger.FileMerger
import xyz.xenondevs.nova.resources.builder.task.ArmorData
import xyz.xenondevs.nova.resources.builder.task.font.MovedFontContent
import xyz.xenondevs.nova.util.StringUtils
import xyz.xenondevs.nova.util.data.openZip
import xyz.xenondevs.nova.world.block.state.model.BackingStateConfigType
import java.io.File
import java.nio.file.Path
import java.util.logging.Level
import kotlin.io.path.copyTo
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

private val DEFAULT_WHITELISTED_FILE_TYPES: Set<String> = hashSetOf(
    "json", "png", "mcmeta", "ogg", "txt", "bin", "fsh", "vsh", "glsl", // vanilla
    "properties" // optifine
)

private val WHITELISTED_FILE_TYPES by MAIN_CONFIG.entry<Set<String>>("resource_pack", "generation", "whitelisted_file_types")
    .map { it.mapTo(HashSet(), String::lowercase) + DEFAULT_WHITELISTED_FILE_TYPES }
private val BASE_PACKS by MAIN_CONFIG.entry<List<File>>("resource_pack", "generation", "base_packs")

class BasePacks internal constructor(private val builder: ResourcePackBuilder) {
    
    private val mergers = FileMerger.createMergers(this)
    private val packs = BASE_PACKS + (ResourcePackBuilder.BASE_PACKS_DIR.toFile().listFiles() ?: emptyArray())
    
    val packAmount = packs.size
    val occupiedModelData = HashMap<Material, HashSet<Int>>()
    internal val occupiedSolidIds = HashMap<BackingStateConfigType<*>, HashSet<Int>>()
    val customArmor = HashMap<Int, ArmorData>()
    
    internal fun include() {
        packs.map {
            if (it.isFile && it.extension.equals("zip", true)) {
                val dir = ResourcePackBuilder.TEMP_BASE_PACKS_DIR.resolve("${it.nameWithoutExtension}-${StringUtils.randomString(5)}")
                dir.createDirectories()
                it.openZip().copyToRecursively(dir, followLinks = false, overwrite = true)
                
                return@map dir
            }
            
            return@map it.toPath()
        }.forEach {
            mergeBasePack(it)
            requestMovedFonts(it)
        }
    }
    
    private fun mergeBasePack(packDir: Path) {
        LOGGER.info("Adding base pack $packDir")
        packDir.walk()
            .filter(Path::isRegularFile)
            .forEach { file ->
                // Validate file extension
                if (file.extension.lowercase() !in WHITELISTED_FILE_TYPES) {
                    LOGGER.warning("Skipping file $file as it is not a resource pack file")
                    return@forEach
                }
                
                // Validate file name
                if (!ResourcePath.NON_NAMESPACED_ENTRY.matches(file.name)) {
                    LOGGER.warning("Skipping file $file as its name does not match regex ${ResourcePath.NON_NAMESPACED_ENTRY}")
                    return@forEach
                }
                
                val relPath = file.relativeTo(packDir)
                val packFile = ResourcePackBuilder.PACK_DIR.resolve(relPath)
                
                packFile.parent.createDirectories()
                val fileMerger = mergers.firstOrNull { it.acceptsFile(relPath) }
                if (fileMerger != null) {
                    try {
                        fileMerger.merge(file, packFile, packDir, relPath)
                    } catch (t: Throwable) {
                        LOGGER.log(Level.SEVERE, "An exception occurred trying to merge base pack file \"$file\" with \"$packFile\"", t)
                    }
                } else if (!packFile.exists()) {
                    file.copyTo(packFile)
                } else {
                    LOGGER.warning("Skipping file $file: File type cannot be merged")
                }
            }
    }
    
    private fun requestMovedFonts(packDir: Path) {
        val assetsDir = packDir.resolve("assets").takeIf(Path::exists) ?: return
        assetsDir.listDirectoryEntries()
            .mapNotNull { it.resolve("font").takeIf(Path::isDirectory) }
            .forEach { fontDir ->
                fontDir.walk()
                    .filter { it.isRegularFile() && it.extension.equals("json", true) }
                    .forEach { fontFile ->
                        val fontNameParts = fontFile.relativeTo(assetsDir).invariantSeparatorsPathString
                            .substringBeforeLast('.')
                            .split('/')
                        
                        builder.getHolder<MovedFontContent>().requestMovedFonts(
                            ResourcePath(fontNameParts[0], fontNameParts.drop(2).joinToString("/")),
                            1..19
                        )
                    }
            }
    }
    
}