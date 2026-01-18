//
// JmlAttributes.java
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml

data class JmlAttributes(
    val entries: List<Pair<String, String>> = emptyList()
) {
    /**
     * Returns a new `JmlAttributes` instance with the new attribute added.
     */
    fun addAttribute(name: String, value: String): JmlAttributes =
        copy(entries = this.entries + (name to value))

    /**
     * Finds the value of the first attribute with the given name (case-insensitive).
     */
    fun getValueOf(name: String): String? {
        return entries.firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }?.second
    }
}
