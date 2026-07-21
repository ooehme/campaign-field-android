package de.oliveroehme.campaignfield.network.auth

internal interface CookiePersistence {
    fun read(): ByteArray?
    fun write(value: ByteArray)
    fun clear()
}
