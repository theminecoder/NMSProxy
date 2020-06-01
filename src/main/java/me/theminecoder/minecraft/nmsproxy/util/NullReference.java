package me.theminecoder.minecraft.nmsproxy.util;

import me.theminecoder.minecraft.nmsproxy.NMSProxy;

/**
 * Utility class to define a type when calling a nms constructor with a null argument
 */
public final class NullReference {

    public static NullReference of(Class<? extends NMSProxy> type) {
        return new NullReference(type);
    }

    private final Class<? extends NMSProxy> type;

    private NullReference(Class<? extends NMSProxy> type) {
        this.type = type;
    }

    public Class<? extends NMSProxy> getType() {
        return type;
    }
}
