package xyz.xenondevs.nova.ui.waila.overlay

import net.kyori.adventure.key.Key
import org.bukkit.entity.Player
import xyz.xenondevs.commons.provider.combinedProvider
import xyz.xenondevs.commons.provider.map
import xyz.xenondevs.nova.config.MAIN_CONFIG
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.resources.CharSizes
import xyz.xenondevs.nova.resources.lookup.ResourceLookups
import xyz.xenondevs.nova.ui.overlay.bossbar.BossBarOverlay
import xyz.xenondevs.nova.ui.overlay.bossbar.BossBarOverlayCompound
import xyz.xenondevs.nova.ui.overlay.bossbar.positioning.BarMatchInfo
import xyz.xenondevs.nova.ui.overlay.bossbar.positioning.BarMatcher
import xyz.xenondevs.nova.ui.overlay.bossbar.positioning.BarPositioning
import xyz.xenondevs.nova.ui.waila.info.WailaLine

private val BAR_MATCH_INFO = BarMatchInfo.fromAddon(Key.key("nova", "waila"))

private val MARGIN_TOP = MAIN_CONFIG.entry<Int>("waila", "positioning", "margin_top")
private val MARGIN_BOTTOM = MAIN_CONFIG.entry<Int>("waila", "positioning", "margin_bottom")
private val MATCH_BELOW = MAIN_CONFIG.entry<BarMatcher.CombinedAny>("waila", "positioning", "above") // bars WAILA should be above: matchers for bar below
private val MATCH_ABOVE = MAIN_CONFIG.entry<BarMatcher.CombinedAny>("waila", "positioning", "below")  // bars WAILA should be below: matchers for bar above

internal class WailaOverlayCompound(private val player: Player) : BossBarOverlayCompound {
    
    override var hasChanged = false
    
    override val positioning by combinedProvider(MARGIN_TOP, MARGIN_BOTTOM, MATCH_ABOVE, MATCH_BELOW)
        .map { (marginTop, marginBottom, matchAbove, matchBelow) ->
            BarPositioning.Dynamic(marginTop, marginBottom, BAR_MATCH_INFO, matchAbove, matchBelow)
        }
    
    override val overlays = ArrayList<BossBarOverlay>()
    
    private val imageOverlay = WailaImageOverlay()
    private val lineOverlays = Array(10, ::WailaLineOverlay)
    
    @Suppress("DEPRECATION")
    fun update(icon: Key, lines: List<WailaLine>) {
        require(lines.size <= 10) { "Waila text can't be longer than 10 lines" }
        
        // reset line overlays
        overlays.clear()
        overlays += imageOverlay
        
        val iconChar = ResourceLookups.WAILA_DATA_LOOKUP[icon]
        val (beginX, centerX) = imageOverlay.update(iconChar, lines.size, lines.maxOf { CharSizes.calculateComponentWidth(it.text, player.locale) })
        
        // re-add line overlays
        lineOverlays.forEachIndexed { idx, overlay ->
            if (lines.size <= idx) {
                overlay.clear()
                return@forEachIndexed
            }
            
            val (text, alignment) = lines[idx]
            overlay.text = text
            overlay.centered = alignment == WailaLine.Alignment.CENTERED
            overlay.x = when (alignment) {
                WailaLine.Alignment.LEFT -> beginX
                WailaLine.Alignment.CENTERED -> centerX
                WailaLine.Alignment.FIRST_LINE -> getBeginX(lines, 0, beginX, centerX)
                WailaLine.Alignment.PREVIOUS_LINE -> getBeginX(lines, idx - 1, beginX, centerX)
            }
            
            overlays += overlay
        }
        
        // mark as changed
        hasChanged = true
    }
    
    @Suppress("DEPRECATION")
    private fun getBeginX(lines: List<WailaLine>, lineNumber: Int, beginX: Float, centerX: Float): Float {
        var currentLineNumber = lineNumber
        while (true) {
            val line = lines[currentLineNumber]
            
            when (line.alignment) {
                WailaLine.Alignment.LEFT -> return beginX
                WailaLine.Alignment.CENTERED -> return centerX - CharSizes.calculateComponentWidth(line.text, player.locale) / 2
                
                WailaLine.Alignment.FIRST_LINE -> currentLineNumber = 0
                WailaLine.Alignment.PREVIOUS_LINE -> currentLineNumber--
            }
        }
    }
    
}