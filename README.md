# Go use [reflection-remapper](https://github.com/jpenilla/reflection-remapper) instead. It has the same featureset, with support for mojang mappings. This wont be updated probably ever.

# NMSProxy

An easy way to work with nms, without the hassle of importing spigot.

Please see the [wiki](https://github.com/theminecoder/NMSProxy/wiki) for documentation on how to use the plugin.
You can also see an [example plugin](https://github.com/theminecoder/NMSProxyTest) for more examples.

## Maven
This library can be found on [JitPack](https://jitpack.io/#theminecoder/NMSProxy) for easy integration into maven 
projects.
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
	
<dependencies>
    <dependency>
        <groupId>com.github.theminecoder</groupId>
        <artifactId>NMSProxy</artifactId>
        <version>0.3</version>
        <scope>provided</scope>
    </dependency>
</dependencies>	
```

