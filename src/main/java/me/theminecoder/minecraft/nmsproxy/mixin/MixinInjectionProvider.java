package me.theminecoder.minecraft.nmsproxy.mixin;

/**
 * @author theminecoder
 */
public interface MixinInjectionProvider {

    public void inject(Class<? extends NMSMixinClass> mixinClass);

    public void injectBefore(Class<? extends NMSMixinClass> mixinClass, Class<? extends NMSMixinClass> mixinBeforeTarget);

    public void injectAfter(Class<? extends NMSMixinClass> mixinClass, Class<? extends NMSMixinClass> mixinAfterTarget);

}
