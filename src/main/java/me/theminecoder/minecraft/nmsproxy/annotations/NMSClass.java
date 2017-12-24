package me.theminecoder.minecraft.nmsproxy.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author theminecoder
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NMSClass {

    public enum Type {
        NMS("net.minecraft.server.%version%."),
        CRAFTBUKKIT("org.bukkit.craftbukkit.%version%."),
        OTHER("");

        private String prefix;

        Type(String prefix) {
            this.prefix = prefix;
        }

        public String getPrefix() {
            return prefix;
        }

        public String getClassName(String version, String className) {
            return (prefix + className).replaceFirst("%version%", version);
        }
    }

    Type type();

    String className();

}
