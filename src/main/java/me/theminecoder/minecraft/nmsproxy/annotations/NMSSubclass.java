package me.theminecoder.minecraft.nmsproxy.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author Hugo Manrique
 * @since 17/02/2018
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface NMSSubclass {
    NMSClass.Type type();

    String parentClass();
}
