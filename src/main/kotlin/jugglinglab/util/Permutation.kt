//
// Permutation.kt
//
// This describes a mathematical permutation of objects such as balls or jugglers.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.util

import java.util.*

class Permutation {
  var size: Int
    private set
  private var mapping: IntArray
  private var reverses: Boolean

  // Construct an identity permutation with `n` elements.
  //
  // Parameter `reverses` allows one to map an element `i` onto a reverse of
  // another element `j`. This is used when for example describing permutations
  // of jugglers, and the symmetry is such that the right hand of juggler `i`
  // is equivalent to the left hand of juggler `j`.

  constructor(n: Int, reverses: Boolean) {
    size = n
    this.reverses = reverses

    if (reverses) {
      mapping = IntArray(size * 2 + 1)
      for (i in 0..<(size * 2 + 1)) {
        mapping[i] = i - size
      }
    } else {
      mapping = IntArray(size)
      for (i in 0..<n) {
        mapping[i] = i + 1
      }
    }
  }

  // Construct a permutation with `n` elements, and mapping `mapping`.

  constructor(n: Int, mapping: IntArray, reverses: Boolean) {
    size = n
    this.reverses = reverses
    this.mapping = mapping
  }

  // Construct a permutation from a string representation.
  //
  // There are two ways the string can be formatted: As an explicit mapping
  // (comma-separated integers), or in cycle notation (parentheses).

  constructor(n: Int, perm: String, reverses: Boolean) {
    size = n
    this.mapping = if (reverses) IntArray(size * 2 + 1) else IntArray(size)
    this.reverses = reverses

    val used: BooleanArray = if (reverses) BooleanArray(size * 2 + 1) else
        BooleanArray(size)

    if (!perm.contains('(')) {
      // explicit mapping
      var num: Int
      val st = StringTokenizer(perm, ",")

      if (st.countTokens() != size) {
        throw JuggleException(
          "Permutation init error: must have $n elements in mapping"
        )
      }
      for (i in 0..<size) {
        val s = st.nextToken().trim { it <= ' ' }
        try {
          num = s.toInt()
        } catch (_: NumberFormatException) {
          throw JuggleException("Permutation init error: number format")
        }
        if (num !in 1..size) {
          throw JuggleException("Permutation init error: out of range")
        }
        if (used[num - 1]) {
          throw JuggleException("Permutation init error: not one-to-one")
        }

        used[num - 1] = true
        mapping[i] = num
      }
    } else {
      // cycle notation
      val st1 = StringTokenizer(perm, ")")

      while (st1.hasMoreTokens()) {
        var s1 = st1.nextToken().trim { it <= ' ' }
        if (s1[0] != '(') {
          throw JuggleException("Permutation init error: parenthesis not grouped")
        }
        s1 = s1.substring(1)
        var num: Int
        var lastnum = -(size + 1)
        val st2 = StringTokenizer(s1, ",")
        while (st2.hasMoreTokens()) {
          var s2 = st2.nextToken().trim { it <= ' ' }
          try {
            if (reverses) {
              var negate = false
              if (s2.endsWith("*")) {
                negate = true
                s2 = s2.replace('*', ' ').trim { it <= ' ' }
              }
              num = s2.toInt()
              if (negate) {
                num = -num
              }
            } else {
              num = s2.toInt()
            }
          } catch (_: NumberFormatException) {
            throw JuggleException("Permutation init error: number format")
          }

          if (reverses) {
            if (num < -size || num > size || num == 0) {
              throw JuggleException("Permutation init error: out of range")
            }
            if (used[num + size]) {
              throw JuggleException("Permutation init error: not one-to-one")
            }
            used[num + size] = true

            if (lastnum == -(size + 1)) {
              mapping[num + size] = num
            } else {
              mapping[num + size] = mapping[lastnum + size]
              mapping[lastnum + size] = num
              if (used[-lastnum + size] && (mapping[-lastnum + size] != -num)) {
                throw JuggleException("Permutation init error: input not reversible")
              }
            }
          } else {
            if (num !in 1..size) {
              throw JuggleException("Permutation init error: out of range")
            }
            if (used[num - 1]) {
              throw JuggleException("Permutation init error: not one-to-one")
            }
            used[num - 1] = true

            if (lastnum == -(size + 1)) {
              mapping[num - 1] = num
            } else {
              mapping[num - 1] = mapping[lastnum - 1]
              mapping[lastnum - 1] = num
            }
          }
          lastnum = num
        }
      }
    }

    if (reverses) {
      for (i in 1..size) {
        if (used[i + size] && !used[-i + size]) {
          mapping[-i + size] = -mapping[i + size]
        } else if (!used[i + size] && used[-i + size]) {
          mapping[i + size] = -mapping[-i + size]
        } else if (!used[i + size] && !used[-i + size]) {
          mapping[-i + size] = 0
          mapping[i + size] = 0
        }
      }
    } else {
      for (i in 0..<size) if (!used[i]) {
        mapping[i] = i + 1
      }
    }
  }

  override fun toString(): String {
    return toString(true)
  }

  fun toString(cyclenotation: Boolean): String {
    val sb = StringBuilder()

    if (cyclenotation) {
      if (reverses) {
        var start: Int
        var current: Int
        val printed = BooleanArray(size)

        for (i in 0..<size) {
          if (printed[i]) continue

          start = i + 1
          printed[i] = true
          current = mapping[start + size]
          if (current != 0) {
            sb.append("(").append(convertReverse(start))
            while (current != start) {
              if (current > 0) {
                printed[current - 1] = true
              } else if (current < 0) {
                printed[-current - 1] = true
              }
              sb.append(",").append(convertReverse(current))
              current = mapping[current + size]
            }
            sb.append(")")
          }
        }
      } else {
        var start: Int
        var current: Int
        var left = size
        val printed = BooleanArray(size)
        for (i in 0..<size) {
          printed[i] = false
        }

        while (left > 0) {
          var i = 0
          while (i < size) {
            if (!printed[i]) {
              break
            }
            ++i
          }
          start = i + 1
          printed[i] = true
          sb.append("(").append(start)
          --left
          current = mapping[i]
          while (current != start) {
            sb.append(",").append(current)
            printed[current - 1] = true
            --left
            current = mapping[current - 1]
          }
          sb.append(")")
        }
      }
    } else {
      if (reverses) {
        sb.append(convertReverse(mapping[size + 1]))
        for (i in 1..<size) {
          sb.append(",").append(convertReverse(mapping[size + 1 + i]))
        }
      } else {
        sb.append(mapping[0])
        for (i in 1..<size) {
          sb.append(",").append(mapping[i])
        }
      }
    }
    return sb.toString()
  }

  private fun convertReverse(num: Int): String {
    return if (num >= 0) {
      num.toString()
    } else {
      (-num).toString() + "*"
    }
  }

  fun hasReverses(): Boolean {
    return reverses
  }

  fun equals(p: Permutation?): Boolean {
    if (p == null) {
      return false
    }
    if (reverses != p.hasReverses()) {
      return false
    }
    if (this.size != p.size) {
      return false
    }
    for (i in 1..this.size) {
      if (getMapping(i) != p.getMapping(i)) {
        return false
      }
    }
    return true
  }

  // Return `perm(elem)`, or the result of applying the permutation to `elem`.

  fun getMapping(elem: Int): Int {
    return if (reverses) {
      mapping[elem + size]
    } else {
      mapping[elem - 1]
    }
  }

  // Return the result of applying the permutation to `elem`, `power` times
  // in succession.
  //
  // If `power` < 0 then return the inverse permutation applied `abs(power)`
  // times in succession.

  fun getMapping(elem: Int, power: Int): Int {
    var el = elem
    if (power > 0) {
      repeat(power) {
        el = getMapping(el)
      }
    } else if (power < 0) {
      repeat(-power) {
        el = getInverseMapping(el)
      }
    }
    return el
  }

  // Return the permutation that is this one, plus permutation `secondp` applied
  // afterward.

  fun apply(secondp: Permutation?): Permutation {
    if (secondp == null || this.size != secondp.size) {
      return this
    }
    if (this.hasReverses() || secondp.hasReverses()) {
      return this
    }

    val res = IntArray(this.size)
    for (i in 0..<this.size) {
      res[i] = secondp.getMapping(this.getMapping(i + 1))
    }

    return Permutation(this.size, res, false)
  }

  // Return `perm.inverse(elem)`, i.e. the element that the permutation maps
  // onto `elem`.

  fun getInverseMapping(elem: Int): Int {
    if (reverses) {
      for (i in 0..<(2 * size + 1)) {
        if (mapping[i] == elem) {
          return (i - size)
        }
      }
    } else {
      for (i in 0..<size) {
        if (mapping[i] == elem) {
          return (i + 1)
        }
      }
    }
    return 0
  }

  // Find the inverse of the permutation.

  val inverse: Permutation
    get() {
      val invmapping: IntArray?

      if (reverses) {
        invmapping = IntArray(size * 2 + 1)

        for (i in 0..<(size * 2 + 1)) {
          invmapping[mapping[i] + size] = i - size
        }
      } else {
        invmapping = IntArray(size)

        for (i in 0..<size) {
          invmapping[mapping[i] - 1] = i + 1
        }
      }

      return Permutation(size, invmapping, reverses)
    }

  // Find the order of the permutation, or how many times the permutation must
  // be applied in succession to arrive at the identity permutation.

  val order: Int
    get() {
      var ord = 1

      for (elem in 1..size) {
        if (getMapping(elem) != 0) {
          ord = lcm(ord, getOrder(elem))
        }
      }

      return ord
    }

  // Find the order of a particular element in the permutation.

  fun getOrder(elem: Int): Int {
    var ord = 1
    var index = (if (reverses) elem + size else elem - 1)

    while (mapping[index] != elem) {
      ord++
      index = mapping[index] + (if (reverses) size else -1)
    }

    return ord
  }

  fun getCycle(elem: Int): IntArray {
    val ord = getOrder(elem)
    val result = IntArray(ord)
    var term = elem

    for (i in 0..<ord) {
      result[i] = term
      term = mapping[if (reverses) term + size else term - 1]
    }
    return result
  }

  companion object {
    // Find the least common multiple of two integers using Euclid's GCD
    // algorithm (x > 0, y > 0).

    @JvmStatic
    fun lcm(x: Int, y: Int): Int {
      var x = x
      var y = y
      val x0 = x
      val y0 = y
      var g = y

      while (x > 0) {
        g = x
        x = y % x
        y = g
      }
      return (x0 * y0) / g
    }
  }
}
