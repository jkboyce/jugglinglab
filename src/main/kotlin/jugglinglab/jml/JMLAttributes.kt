//
// JMLAttributes.java
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.jml

class JMLAttributes(private var parentTag: JMLNode?) {
    var numberOfAttributes: Int = 0
        private set
    private var names: MutableList<String> = ArrayList()
    private var values: MutableList<String> = ArrayList()

    fun addAttribute(name: String, value: String) {
        names.add(name)
        values.add(value)
        numberOfAttributes++
    }

    fun getAttributeName(index: Int) = names[index]

    fun getAttributeValue(index: Int) = values[index]

    fun getAttribute(name: String): String? {
        for (i in 0..<this.numberOfAttributes) {
            if (name.equals(names[i], ignoreCase = true)) {
                return values[i]
            }
        }
        return null
    }
}
