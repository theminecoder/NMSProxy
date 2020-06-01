package me.theminecoder.minecraft.nmsproxy.proxy;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import me.theminecoder.minecraft.nmsproxy.NMSProxy;
import me.theminecoder.minecraft.nmsproxy.annotations.NMSClass;
import me.theminecoder.minecraft.nmsproxy.util.NullReference;
import net.md_5.bungee.api.plugin.Plugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Map;

/**
 * @author theminecoder
 */
@SuppressWarnings({"rawtypes", "unchecked", "JavaDoc"})
public final class NMSProxyProvider {

    public static final String NMS_VERSION;

    static {
        String LOADED_VERSION;
        try {
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            LOADED_VERSION = packageName.substring(packageName.lastIndexOf(".") + 1);
        } catch (LinkageError e) {
            //Not loaded in bukkit context
            LOADED_VERSION = "";
        }
        NMS_VERSION = LOADED_VERSION;
    }

    @Deprecated
    private static final Map<Object, NMSProxyProvider> PLUGIN_INSTANCES = Maps.newHashMap();

    private final BiMap<Class, Class> proxyToNMSClassMap = HashBiMap.create();
    private final NMSProxyInvocationMapper invocationMapper = new NMSProxyInvocationMapper(proxyToNMSClassMap);

    public NMSProxyProvider() {
    }

    /**
     * @deprecated Just make your own instance now.
     */
    @Deprecated
    public static NMSProxyProvider get(JavaPlugin plugin) {
        return PLUGIN_INSTANCES.computeIfAbsent(plugin, __ -> new NMSProxyProvider());
    }

    /**
     * @deprecated Just make your own instance now.
     */
    @Deprecated // Only making this to allow for copy/paste into bungee plugins
    public static NMSProxyProvider get(Plugin plugin) {
        return PLUGIN_INSTANCES.computeIfAbsent(plugin, __ -> new NMSProxyProvider());
    }

    private void registerNMSClasses(Class<? extends NMSProxy> clazz) {
        if (proxyToNMSClassMap.containsKey(clazz)) {
            return;
        }

        NMSClass nmsClassAnnotation = clazz.getAnnotation(NMSClass.class);
        if (nmsClassAnnotation == null) {
            throw new IllegalStateException("NMSProxy interfaces must have a valid @NMSClass annotation");
        }

        // this is by design
        //noinspection StringEquality
        if(nmsClassAnnotation.value() == NMSClass.USE_OTHER_VALUE && nmsClassAnnotation.className() == NMSClass.USE_OTHER_VALUE) {
            throw new IllegalStateException("Please set the value property in the @NMSClass annotation of "+clazz);
        }

        String className;
        //noinspection StringEquality
        if(nmsClassAnnotation.value() != NMSClass.USE_OTHER_VALUE) {
            className = nmsClassAnnotation.value();
        } else {
            className = nmsClassAnnotation.className();
        }

        Class nmsClass;
        try {
            nmsClass = Class.forName(nmsClassAnnotation.type().getClassName(className)); //TODO Move %version% replacement here
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Class " + nmsClassAnnotation.className() + " (" + nmsClassAnnotation.type() + ") was not found!");
        }

        proxyToNMSClassMap.put(clazz, nmsClass);
    }

    /**
     * Checks if the passed NMS object is an instance of the passed class
     *
     * @param object Object to check
     * @param clazz  Class to check
     */
    public boolean isInstanceOf(Object object, Class<? extends NMSProxy> clazz) {
        registerNMSClasses(clazz);

        if (object instanceof NMSProxy) {
            object = ((NMSProxy) object).getProxyHandle();
        }

        return proxyToNMSClassMap.get(clazz).isAssignableFrom(object.getClass());
    }

    /**
     * Generates a static only proxy to an NMS class
     *
     * @param clazz {@link NMSClass} annotated {@link NMSProxy} interface.
     * @return Generated Proxy
     */
    public <T extends NMSProxy> T getStaticNMSObject(Class<T> clazz) {
        registerNMSClasses(clazz);

        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, new NMSProxyInvocationHandler(null, invocationMapper, this));
    }

    /**
     * Generates a proxy to an NMS class instance
     *
     * @param clazz  {@link NMSClass} annotated {@link NMSProxy} interface.
     * @param object Object to proxy
     * @return Generated Proxy
     */
    public <T extends NMSProxy> T getNMSObject(Class<T> clazz, Object object) {
        registerNMSClasses(clazz);

        if (!proxyToNMSClassMap.get(clazz).isAssignableFrom(object.getClass())) {
            throw new IllegalStateException("Object is not of type " + proxyToNMSClassMap.get(clazz).getCanonicalName() + "!");
        }

        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, new NMSProxyInvocationHandler(object, invocationMapper, this));
    }

    /**
     * Constructs and returns a NMS object wrapped in a proxy.
     *
     * @param clazz  {@link NMSClass} annotated {@link NMSProxy} interface class
     * @param params Objects to pass to the constructor (NMSProxy instances will be converted to their actual objects for you).
     *               Use of null must be modified to use a {@link NullReference} object instead, so the type of null is known.
     * @return The constructed NMS object wrapped in a proxy.
     * @throws ReflectiveOperationException
     */
    public <T extends NMSProxy> T constructNMSObject(Class<T> clazz, Object... params) throws ReflectiveOperationException {
        registerNMSClasses(clazz);

        NullReference[] nullReferences = new NullReference[params.length];

        //pull out null references and swap them to null
        for (int i = 0; i < params.length; i++) {
            if(params[i] == null) throw new IllegalArgumentException("null argument is not supported directly. Use a NullReference instead.");
            if(params[i] instanceof NullReference) {
                nullReferences[i] = (NullReference) params[i];
                params[i] = null;
            }
        }

        Object[] fixedArgs = unwrapArguments(params);
        Class[] fixedArgTypes = Arrays.stream(fixedArgs).map(arg -> arg != null ? arg.getClass() : null).toArray(Class[]::new);

        for (int i = 0; i < nullReferences.length; i++) {
            if(nullReferences[i] != null) {
                Class type = nullReferences[i].getType();
                if (NMSProxy.class.isAssignableFrom(type)) {
                    this.registerNMSClasses(type);
                    type = proxyToNMSClassMap.get(type);
                }
                fixedArgTypes[i] = type;
            }
        }

        Object nmsObject = invocationMapper.findNMSConstructor(clazz, fixedArgTypes).newInstance(fixedArgs);

        return getNMSObject(clazz, nmsObject);
    }

    Object[] unwrapArguments(Object[] args) {
        if (args == null) {
            return new Object[]{};
        }

        return Arrays.stream(args).map(this::unwrapArgument).toArray(Object[]::new);
    }

    Object unwrapArgument(Object arg) {
        if (arg == null) {
            return null;
        }

        if (arg instanceof NMSProxy) {
            this.registerNMSClasses((Class<? extends NMSProxy>) arg.getClass().getInterfaces()[0]);
            return ((NMSProxy) arg).getProxyHandle();
        }

        return arg;
    }
}
