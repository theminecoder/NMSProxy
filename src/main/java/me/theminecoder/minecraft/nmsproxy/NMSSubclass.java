package me.theminecoder.minecraft.nmsproxy;

/**
 * @author theminecoder
 */
public abstract class NMSSubclass implements NMSProxy {

    protected NMSSubclass(Object... args) {

    }

    @Override
    public final Object getProxyHandle() {
        return null;
    }
}
