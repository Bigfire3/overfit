package app.overfit.data

/** Plain key-value storage (SharedPreferences / NSUserDefaults). Not for secrets. */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

/** Storage for the API key: Android Keystore / iOS Keychain. */
interface SecretStore {
    fun load(): String?
    fun save(value: String)
    fun delete()
}

class InMemoryKeyValueStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
}

class InMemorySecretStore : SecretStore {
    private var value: String? = null
    override fun load() = value
    override fun save(value: String) { this.value = value }
    override fun delete() { value = null }
}
