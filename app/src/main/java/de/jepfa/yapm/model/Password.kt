package de.jepfa.yapm.model

import java.nio.CharBuffer

data class Password(var data: CharArray) : Clearable {
    constructor(passwd: String) : this(passwd.toCharArray()) {
    }

    constructor(passwd: ByteArray) : this(passwd.map { it.toChar() }.toCharArray()) {
    }

    fun add(other: Password) {
        val buffer = data + other.data
        clear()
        other.clear()
        data = buffer
    }

    fun add(other: Char) {
        val buffer = data + other
        clear()
        data = buffer
    }

    fun toByteArray(): ByteArray {
        return data.map { it.toByte() }.toByteArray();
    }

    fun debugToString(): String {
        var presentation = "";
        for (i in 0 until data.size) {
            if (i != 0 && i % 4 == 0) {
                presentation += "-"
            }
            presentation += data[i]
        }
        return presentation
    }

    override fun clear() {
        data.fill('0', 0, data.size)
    }
}