package xyz.xenondevs.nova.resources.builder.task

import xyz.xenondevs.nova.resources.ResourcePath
import xyz.xenondevs.nova.resources.ResourceType
import xyz.xenondevs.nova.resources.builder.ResourcePackBuilder
import xyz.xenondevs.nova.resources.builder.task.font.MovedFontContent
import xyz.xenondevs.nova.ui.overlay.bossbar.BossBarOverlayManager

class BarOverlayTask(private val builder: ResourcePackBuilder) : PackTaskHolder {
    
    @PackTask(runBefore = ["MovedFontContent#write"])
    private fun requestMovedFonts() {
        if (BossBarOverlayManager.ENABLED) {
            val movedFontContent = builder.getHolder<MovedFontContent>()
            movedFontContent.requestMovedFonts(ResourcePath(ResourceType.Font, "minecraft", "default"), 1..19)
            movedFontContent.requestMovedFonts(ResourcePath(ResourceType.Font, "minecraft", "uniform"), 1..19)
            movedFontContent.requestMovedFonts(ResourcePath(ResourceType.Font, "nova", "bossbar"), 1..19)
        }
    }
    
}