package me.theminecoder.minecraft.nmsproxy.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @deprecated No longer required
 * @author theminecoder
 */
@SuppressWarnings("DeprecatedIsStillUsed") //Gonna be here for a bit
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Deprecated
public @interface NMSMethod {

    //TODO move this into repeatable annotation
    NMSVersionName[] versionNames() default {};

}
