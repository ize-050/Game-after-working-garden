package com.littlefarm.platform

interface SaveStore {
    fun read(): String?
    fun write(value: String)
}

expect fun currentTimeMillis(): Long

