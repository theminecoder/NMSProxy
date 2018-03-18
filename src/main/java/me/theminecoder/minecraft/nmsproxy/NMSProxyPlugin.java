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

//    @Override
//    public void onEnable() {
//        SimpleMixinInjectionProvider mixinInjectionProvider = new SimpleMixinInjectionProvider();
//        Arrays.stream(getServer().getPluginManager().getPlugins())
//                .filter(plugin -> plugin instanceof PluginMixinProvider)
//                .forEach(plugin -> ((PluginMixinProvider) plugin).provideMixins(mixinInjectionProvider));
//
//        ByteBuddy byteBuddy = new ByteBuddy();
//        TypePool typePool = TypePool.Default.of(Bukkit.class.getClassLoader());
//        Map<String, DynamicType.Builder> builders = new HashMap<>();
//        mixinInjectionProvider.getMixinClasses().forEach(mixinClass -> {
//            NMSClass classAnnotation = mixinClass.getAnnotation(NMSClass.class);
//            if (classAnnotation == null) {
//                System.out.println("[MIXIN] Skipping " + mixinClass.getCanonicalName() + " as it has no @NMSClass annotation!");
//                return;
//            }
//
//            String nmsClassName = classAnnotation.type().getClassName(NMS_VERSION, classAnnotation.className());
//
//            DynamicType.Builder builder = builders.get(nmsClassName);
//            if (builder == null) {
//                TypeDescription result = typePool.describe(nmsClassName).resolve();
//                builder = byteBuddy.rebase(
//                        result,
//                        ClassFileLocator.ForClassLoader.of(Bukkit.class.getClassLoader())
//                );
//                builders.put(nmsClassName, builder);
//            }
//
//            DynamicType.Builder classBuilder = builder;
//            Arrays.stream(mixinClass.getDeclaredMethods()).forEach(mixinMethod -> {
//                NMSMixin mixinAnnotation = mixinMethod.getAnnotation(NMSMixin.class);
//                if (mixinAnnotation == null) {
//                    return;
//                }
//
//                classBuilder.method()
//            });
//        });
//
//        builders.forEach((clazz, builder) -> builder.make().load(Bukkit.class.getClassLoader()));
//    }
}
