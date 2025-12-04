//
// JMLAttributes.java
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml

data class JMLAttributes(
    val entries: List<Pair<String, String>> = emptyList()
) {
    val numberOfAttributes: Int
        get() = entries.size

    /**
     * Returns a new `JMLAttributes` instance with the new attribute added.
     */
    fun addAttribute(name: String, value: String): JMLAttributes =
        copy(entries = this.entries + (name to value))

    fun getAttributeName(index: Int): String = entries[index].first

    fun getAttributeValue(index: Int): String = entries[index].second

    /**
     * Finds the value of the first attribute with the given name (case-insensitive).
     */
    fun getValueOf(name: String): String? {
        return entries.firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }?.second
    }
}
