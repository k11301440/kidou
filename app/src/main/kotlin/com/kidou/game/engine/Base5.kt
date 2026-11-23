package com.kidou.game.engine

object Base5 {
  fun toDigitWordIds(value: Int): List<String> {
    if (value <= 0) return listOf("d_0")
    val digits = mutableListOf<String>()
    var n = value
    while (n > 0) {
      val rem = n % 5
      digits.add("d_$rem")
      n /= 5
    }
    return digits.reversed()
  }
}
