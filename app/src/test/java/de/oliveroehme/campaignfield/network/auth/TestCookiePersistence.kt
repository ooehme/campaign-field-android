package de.oliveroehme.campaignfield.network.auth

internal class TestCookiePersistence : CookiePersistence {
    var value: ByteArray? = null

    override fun read(): ByteArray? = value?.copyOf()

    override fun write(value: ByteArray) {
        this.value = value.copyOf()
    }

    override fun clear() {
        value = null
    }
}
