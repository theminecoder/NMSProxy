package me.theminecoder.minecraft.nmsproxy.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static me.theminecoder.minecraft.nmsproxy.proxy.NMSProxyProvider.NMS_VERSION;

/**
 * @author theminecoder
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NMSClass {

    public static final String USE_OTHER_VALUE = " ** __USE_OTHER_VALUE__ ** ";

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

        public String getClassName(String className) {
            return (prefix + className).replaceFirst("%version%", NMS_VERSION);
        }
    }

    /**
     * @deprecated Going to remove in 2.0 in favour os just prepending it to the value manually
     */
    @Deprecated
    Type type() default Type.OTHER;

    /**
     * @deprecated use value instead
     */
    @Deprecated
    String className() default USE_OTHER_VALUE;

    String value() default USE_OTHER_VALUE;

}
