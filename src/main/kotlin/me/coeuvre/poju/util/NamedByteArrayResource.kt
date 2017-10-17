package me.coeuvre.poju.util

import org.springframework.core.io.ByteArrayResource

class NamedByteArrayResource(private val filename: String, byteArray: ByteArray) : ByteArrayResource(byteArray) {
    override fun getFilename(): String = filename
}