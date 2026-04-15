package dev.lulu.plugin

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.property
import javax.inject.Inject

abstract class McSourceExtension @Inject constructor(objects: ObjectFactory) {
    val mcVersion: Property<String> = objects.property()

    val type: Property<JarType> = objects.property()

    val resolveResources: Property<Boolean> = objects.property<Boolean>().convention(false)

    val decompilerArguments: MapProperty<String, Any> = objects.mapProperty<String, Any>().convention(mapOf())

    val manifestUrlOverride: Property<String> = objects.property<String>().convention(null)

    val jarOverride: JarOverride = objects.newInstance()

    @Suppress("unused")
    fun jarOverride(op: Action<in JarOverride>) {
        op.execute(jarOverride)
    }

    fun validate() {
        val hasJarOverride = jarOverride.isSet()
        if (manifestUrlOverride.isPresent || hasJarOverride) {
            require((manifestUrlOverride.isPresent && hasJarOverride).not()) {
                "Cannot define both manifest/jar override"
            }
        }
        if (hasJarOverride) {
            jarOverride.validate()
        }
    }

    abstract class JarOverride @Inject constructor(objects: ObjectFactory)  {
        val url: Property<String> = objects.property<String>().convention(null)

        val mappingUrl: Property<String> = objects.property<String>().convention(null)

        fun isSet(): Boolean {
            return url.isPresent || mappingUrl.isPresent
        }

        fun validate() {
            require(url.isPresent) {
                "Cannot define mapping url override without a jar url"
            }
        }
    }
}
