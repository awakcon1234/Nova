package xyz.xenondevs.nova.patch

import org.bukkit.Bukkit
import xyz.xenondevs.nova.NovaBootstrapper

/**
 * The class loader that is responsible for loading all Bukkit and Minecraft classes.
 */
private val PAPER_CLASS_LOADER = Bukkit::class.java.classLoader

/**
 * The class loader responsible for loading Nova and its libraries.
 */
private val NOVA_CLASS_LOADER = NovaBootstrapper::class.java.classLoader

/**
 * The [PatchedClassLoader] is a class loader that is injected in the class loading hierarchy.
 * It is the parent of the SpigotClassLoader and is used as a bridge to load classes referenced in patches.
 *
 * Hierarchy:
 * PluginClassLoader -> Paper Bundler (URLClassLoader - loads paper, minecraft and libraries) -> PatchedClassLoader -> PlatformClassLoader -> BootClassLoader
 *
 * If the PlatformClassLoader (and parents) cannot find the requested class and the class load was triggered by
 * the Paper Bundler (so from a class that was potentially patched), the [PatchedClassLoader] will basically
 * restart the class loading process at the Nova class loader.
 */
internal class PatchedClassLoader : ClassLoader(PAPER_CLASS_LOADER.parent) {
    
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        var c: Class<*>? = null
        
        try {
            c = parent.loadClass(name)
        } catch (ignored: ClassNotFoundException) {
        }
        
        if (c == null && checkNonRecursive() && checkPaperLoader()) {
            try {
                c = NOVA_CLASS_LOADER.loadClass(name)
            } catch (ignored: ClassNotFoundException) {
            }
        }
        
        if (c == null)
            throw ClassNotFoundException(name)
        
        return c
    }
    
    /**
     * Checks the stacktrace for the PatchedClassLoader to prevent recursion.
     *
     * It's independent from [checkPaperLoader] as that requires loading a class, which would then cause recursion.
     */
    private fun checkNonRecursive(): Boolean {
        val stackTrace = Thread.currentThread().stackTrace
        for (i in 3..stackTrace.lastIndex) { // skip the first three elements: Thread.getStackTrace(), checkNonRecursive(), loadClass()
            val className = stackTrace[i].className
            
            // check whether the stack trace element is PatchedClassLoader, i.e. this is a recursive call
            if (className == "xyz.xenondevs.nova.patch.PatchedClassLoader")
                return false
            
            // does not indicate a recursive call, but is the most common class loading deadlock cause,
            // so it is included here until the root cause is resolved (removal of PatchedClassLoader)
            if (className == "org.apache.logging.log4j.core.impl.ThrowableProxyHelper")
                return false
        }
        
        return true
    }
    
    /**
     * Checks that the class initiating the class loading process is loaded by the Paper Bundler (i.e a class that has been potentially patched).
     */
    private fun checkPaperLoader(): Boolean =
        findLoadingClass().classLoader == PAPER_CLASS_LOADER
    
    /**
     * Steps through the stack frames to find the first class that triggered a class loading process.
     */
    private fun findLoadingClass(): Class<*> {
        var takeNext = false
        var loadingClass: Class<*>? = null
        
        StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).forEach {
            var clazz = it.declaringClass
            
            if (takeNext) {
                loadingClass = clazz
                takeNext = false
            }
            
            while (clazz != null) {
                if (clazz == ClassLoader::class.java) {
                    takeNext = true
                    break
                }
                clazz = clazz.superclass
            }
        }
        
        return loadingClass!!
    }
    
}