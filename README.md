mc-source
=========

mc-source is a minimalist Gradle plugin that aims to download, extract and decompile the client or
the server of Minecraft Java Edition.

Usage
-----

Note: the following example assume the maven repositories are configured properly to fetch
[Vineflower](https://github.com/Vineflower/vineflower),
[unpick](https://github.com/FabricMC/unpick) with its [definitions](https://github.com/PaperMC/unpick-definitions) and possibly
[AutoRenamingTool](https://github.com/neoforged/AutoRenamingTool):

```kotlin
mcSource {
    mcVersion = "26.1"
    type = JarType.CLIENT // or SERVER

    // By default, only the java class files are decompiled along the stripped manifest without other resources such as
    // data-pack files or assets, this option extract those as well.
    // resolveResources = true

    // Optionally the manifest can be set manually for a few special versions
    // manifestUrlOverride = "https://piston-meta.mojang.com/v1/packages/700837ec523b1c7ff921abab74896102c94047c5/25w14craftmine.json"

    // Optionally the downloaded jar can be set manually for special version such as combat update or unobfuscated
    // versions provided since 25w45a until 1.21.11 (included). The libraries will still be fetched from the regular manifest
    // from the version set in the mcVersion property to give proper context during transformation and decompilation.
    // jarOverride.url = "https://piston-data.mojang.com/v1/objects/3ca78d5068bf9b422f694d3f0820e289581c0f0d/server.jar"

    // Only applicable if the jar url is changed with the above option, will require the remapper to be defined as well
    // jarOverride.mappingUrl = "https://piston-data.mojang.com/v1/objects/0f78860aa616f0ac1d044be0db3b7e1ea4eda16f/client.txt"

    // By default, the following arguments are passed to the decompiler (Vineflower):
    decompilerArguments.putAll(mapOf(
        IFernflowerPreferences.INDENT_STRING to " ".repeat(4),
        IFernflowerPreferences.LOG_LEVEL to IFernflowerLogger.Severity.TRACE.name,
        IFernflowerPreferences.BYTECODE_SOURCE_MAPPING to true,
        IFernflowerPreferences.THREADS to Math.ceilDiv(Runtime.getRuntime().availableProcessors(), 2).toString()
    ))
    // more can be passed on and the above options can be overwritten as well
    decompilerArguments.put(IFernflowerPreferences.VERIFY_PRE_POST_VARIABLE_MERGES, true)
}

dependencies {
    unpickDefinitions("io.papermc.unpick-definitions:unpick-definitions:26.1+build.1@unpick") // accept a raw .unpick file in v4 or a zip containing this file

    // Optionally for versions with mojang mapping available the remapper must be set to apply the transformation.
    // It's also recommended to define it for very old versions (before 19w36a) to fix lvt name using snowman identifier.
    // remapper("net.neoforged:AutoRenamingTool:<version>:all") # the fat jar is needed because of https://github.com/neoforged/AutoRenamingTool/issues/15
}
```

Context
-------

I used to look for changes between versions using [mojankinator](https://github.com/octylFractal/mojankinator) from the Dr. Doofenshmirtz.
But internally it uses loom which is limited and doesn't allow to define its own set of unpick definitions
without applying legacy yarn mappings on top. This plugin aims to replace loom in mojankinator and provide great flexibility while
still being simple.

License
-------

The code in this repository is licensed under [MIT](LICENSE). The decompiled
code is Mojang's proprietary code and not part of the licensed work.
