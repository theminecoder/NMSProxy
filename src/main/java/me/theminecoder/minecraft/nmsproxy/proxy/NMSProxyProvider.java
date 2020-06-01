package me.theminecoder.minecraft.nmsproxy.proxy;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import me.theminecoder.minecraft.nmsproxy.NMSProxy;
import me.theminecoder.minecraft.nmsproxy.annotations.NMSClass;
import me.theminecoder.minecraft.nmsproxy.util.NullReference;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Map;

/**
 * @author theminecoder
 */
@SuppressWarnings({"rawtypes", "unchecked"})
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

    private static final Map<JavaPlugin, NMSProxyProvider> PLUGIN_INSTANCES = Maps.newHashMap();

    private BiMap<Class, Class> proxyToNMSClassMap = HashBiMap.create();
    private NMSProxyInvocationMapper invocationMapper = new NMSProxyInvocationMapper(proxyToNMSClassMap);

    private NMSProxyProvider() {
    }

    public static NMSProxyProvider get(JavaPlugin plugin) {
        NMSProxyProvider instance = PLUGIN_INSTANCES.get(plugin);
        if (instance == null) {
            instance = new NMSProxyProvider();
            PLUGIN_INSTANCES.put(plugin, instance);
        }
        return instance;
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
     * @param params Objects to pass to the constructor (NMSProxy instances will be converted to their actual objects for you)
     * @return The constructed NMS object wrapped in a proxy.
     * @throws ReflectiveOperationException
     */
    public <T extends NMSProxy> T constructNMSObject(Class<T> clazz, Object... params) throws ReflectiveOperationException {
        registerNMSClasses(clazz);

        NullReference[] nullReferences = new NullReference[params.length];
        Object[] fixedArgs = unwrapArguments(params);

        //pull out null references and swap them to null
        for (int i = 0; i < fixedArgs.length; i++) {
            if(fixedArgs[i] instanceof NullReference) {
                nullReferences[i] = (NullReference) fixedArgs[i];
                fixedArgs[i] = null;
            }
        }

        Class[] fixedArgTypes = Arrays.stream(fixedArgs).map(Object::getClass).toArray(Class[]::new);

        //swap type search with null reference types
        for (int i = 0; i < nullReferences.length; i++) {
            if(nullReferences[i] != null) {
                fixedArgTypes[i] = nullReferences[i].getType();
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
