package me.theminecoder.minecraft.nmsproxy.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author theminecoder
 */
//@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NMSVersionName {

    String name();

    String version();

}
