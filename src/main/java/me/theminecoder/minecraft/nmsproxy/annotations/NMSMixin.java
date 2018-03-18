package me.theminecoder.minecraft.nmsproxy.annotations;

/**
 * @author theminecoder
 */
public @interface NMSMixin {

    public enum Action {
        PREPEND,
        REPLACE,
        APPEND
    }

    Action action();

    NMSVersionName[] versionNames() default {};

}
