package dev.cyclone.cloak.root

/**
 * The apps every space installs besides the owner's picks. Cloak is always one, so a space keeps its own navigation.
 * Cyclone joins only when this phone has it, because a managed user can install only apps Main already has.
 *
 * Cyclone's full essentials list lives on its own branch and is not in this checkout, so only these two are known here.
 */
object CornerstoneApps {
    const val CLOAK = "dev.cyclone.cloak"
    const val CYCLONE = "com.cyclone.mobile"

    /** The essentials on a phone where [installed] says which packages exist. Cloak comes first. */
    fun essentials(installed: (String) -> Boolean): List<String> = listOf(CLOAK) + listOf(CYCLONE).filter(installed)

    /** The packages a new space installs: essentials first, then the owner's picks, without repeats. */
    fun plan(picked: List<String>, installed: (String) -> Boolean): List<String> = (essentials(installed) + picked).distinct()
}
