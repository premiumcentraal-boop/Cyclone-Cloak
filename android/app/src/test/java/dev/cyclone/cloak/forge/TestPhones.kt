package dev.cyclone.cloak.forge

/** The built-in catalog (`catalog/phones.json`, on the test classpath), as the app loads it. */
object TestPhones {
    val all: List<PhoneTemplate> by lazy {
        PhoneCatalog.parse(javaClass.classLoader!!.getResource("phones.json")!!.readText())
    }

    operator fun get(id: String): PhoneTemplate = all.first { it.id == id }
}
