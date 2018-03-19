package me.theminecoder.minecraft.nmsproxy.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used to denote the use of a static method/field vs a object method/field on a proxy
 * @author theminecoder
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NMSStatic {
}
