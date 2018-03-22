package me.theminecoder.minecraft.nmsproxy;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * This is an empty shell because bukkit.
 *
 * @author theminecoder
 */
public class NMSProxyPlugin extends JavaPlugin {

    public static final String NMS_VERSION;

    static {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        NMS_VERSION = packageName.substring(packageName.lastIndexOf(".") + 1);
    }

}
