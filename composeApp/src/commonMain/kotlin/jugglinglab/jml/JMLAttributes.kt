//
// JMLAttributes.java
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml

data class JMLAttributes(
    private val attributes: List<Pair<String, String>> = emptyList()
) {
    val numberOfAttributes: Int
        get() = attributes.size

    /**
     * Returns a new `JMLAttributes` instance with the new attribute added.
     */
    fun addAttribute(name: String, value: String): JMLAttributes =
        copy(attributes = this.attributes + (name to value))

    fun getAttributeName(index: Int): String = attributes[index].first

    fun getAttributeValue(index: Int): String = attributes[index].second

    /**
     * Finds the value of the first attribute with the given name (case-insensitive).
     */
    fun getAttribute(name: String): String? {
        return attributes.firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }?.second
    }
}
