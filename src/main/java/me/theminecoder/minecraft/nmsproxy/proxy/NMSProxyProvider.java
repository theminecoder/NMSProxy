package me.theminecoder.minecraft.nmsproxy.proxy;

import com.google.common.base.Joiner;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import me.theminecoder.minecraft.nmsproxy.NMSProxy;
import me.theminecoder.minecraft.nmsproxy.NMSSubclass;
import me.theminecoder.minecraft.nmsproxy.annotations.NMSClass;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author theminecoder
 */
public final class NMSProxyProvider {

    private static final Map<JavaPlugin, NMSProxyProvider> PLUGIN_INSTANCES = Maps.newHashMap();

    private BiMap<Class, Class> proxyToNMSClassMap = HashBiMap.create();
    private final Map<Class, DynamicType.Loaded> proxyToNMSSubclassMap = Maps.newHashMap();
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

        Class nmsClass;
        try {
            nmsClass = Class.forName(nmsClassAnnotation.type().getClassName(nmsClassAnnotation.className()));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Class " + nmsClassAnnotation.className() + " (" + nmsClassAnnotation.type() + ") was not found!");
        }

        proxyToNMSClassMap.put(clazz, nmsClass);
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
     * @param clazz {@link NMSClass} annotated {@link NMSProxy} interface.
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
     * @param clazz {@link NMSClass} annotated {@link NMSProxy} interface class
     * @param params Objects to pass to the constructor (NMSProxy instances will be converted to their actual objects for you)
     * @return The constructed NMS object wrapped in a proxy.
     * @throws ReflectiveOperationException
     */
    public <T extends NMSProxy> T constructNMSObject(Class<T> clazz, Object... params) throws ReflectiveOperationException {
        registerNMSClasses(clazz);

        Object[] fixedArgs = unwrapArguments(params);
        Class[] fixedArgTypes = Arrays.stream(fixedArgs).map(Object::getClass).toArray(Class[]::new);

        Object nmsObject = invocationMapper.findNMSConstructor(clazz, fixedArgTypes).newInstance(fixedArgs);

        return getNMSObject(clazz, nmsObject);
    }

    /**
     * Constructs and returns an object that is a subclass of another NMS class.
     *
     * Something of note here is that the class you use to define the subclass is not the same class you get back.
     * This is due to the use of a dynamic class generator to do the backend work with subclassing.
     *
     * @param clazz Class implementing another {@link NMSProxy} and {@link NMSSubclass}
     * @param params Objects to pass to the constructor of the final subclass
     * @return The constructed object (Note that this is not wrapped in a proxy)
     * @throws ReflectiveOperationException
     */
    public <T extends NMSSubclass> T constructNMSSubclass(Class<T> clazz, Object... params) throws ReflectiveOperationException {
        if (clazz.getInterfaces().length == 0 || NMSProxy.class.isAssignableFrom(clazz.getInterfaces()[0])) {
            throw new IllegalArgumentException("Class does not implement a NMSProxy interface");
        }

        Class<? extends NMSProxy> proxyClass = (Class<? extends NMSProxy>) clazz.getInterfaces()[0];
        registerNMSClasses(proxyClass);

        DynamicType.Loaded<Object> dynamicType = proxyToNMSSubclassMap.get(proxyClass);
        if (dynamicType == null) {
            Class nmsClass = proxyToNMSClassMap.get(proxyClass);
            DynamicType.Builder<Object> builder = new ByteBuddy().subclass(nmsClass).name(clazz.getCanonicalName() + "$RuntimeGenerated").implement(clazz.getInterfaces());

            System.out.println("[SUBCLASSING][" + nmsClass.getSimpleName() + "->" + clazz.getSimpleName() + "] NMS Class Modifiers: " + Modifier.toString(nmsClass.getModifiers()));
            System.out.println("[SUBCLASSING][" + nmsClass.getSimpleName() + "->" + clazz.getSimpleName() + "] Methods needing to be implemented for \"" + nmsClass.getCanonicalName() + "\":");
            for (Method method : nmsClass.getDeclaredMethods()) {
                if (Modifier.isAbstract(method.getModifiers())) {
                    System.out.println("[SUBCLASSING][" + nmsClass.getSimpleName() + "->" + clazz.getSimpleName() + "] \t- " + Modifier.toString(method.getModifiers()) + " " + method.getReturnType().getSimpleName() + " " + method.getName() + "(" + Joiner.on(", ").join(Arrays.stream(method.getParameterTypes()).map(Class::getSimpleName).collect(Collectors.toList())) + ")");
                }
            }

            System.out.println("[SUBCLASSING][" + nmsClass.getSimpleName() + "->" + clazz.getSimpleName() + "] Methods being implemented by \"" + clazz.getCanonicalName() + "\":");
            for (Method method : clazz.getDeclaredMethods()) {
                System.out.println("[SUBCLASSING][" + nmsClass.getSimpleName() + "->" + clazz.getSimpleName() + "] \t- " + Modifier.toString(method.getModifiers()) + " " + method.getReturnType().getSimpleName() + " " + method.getName() + "(" + Joiner.on(", ").join(Arrays.stream(method.getParameterTypes()).map(Class::getSimpleName).collect(Collectors.toList())) + ")");
            }

            for (Method method : clazz.getDeclaredMethods()) {
                builder.defineMethod(method.getName(), method.getReturnType(), method.getModifiers())
                        .withParameters(Arrays.stream(method.getParameterTypes()).map(type -> {
                            if (NMSProxy.class.isAssignableFrom(type)) {
                                registerNMSClasses((Class<? extends NMSProxy>) type);
                                type = proxyToNMSClassMap.get(type);
                            }

                            return type;
                        }).collect(Collectors.toList()))
                        .intercept(Advice.to(NMSProxySubclassAdvice.class).wrap(MethodCall.invoke(method)));
            }

            builder.defineMethod("getProxyHandle", Object.class, Modifier.PUBLIC).intercept(FixedValue.self());

            dynamicType = builder.make().load(clazz.getClassLoader());
            proxyToNMSSubclassMap.put(clazz, dynamicType);
        }

        return (T) dynamicType.getLoaded().getConstructor(Arrays.stream(params).map(Object::getClass).toArray(Class[]::new))
                .newInstance(params);
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

    /**
     * @author theminecoder
     */
    static class NMSProxySubclassAdvice {

        @Advice.OnMethodEnter
        static void enter(@Advice.AllArguments(typing = Assigner.Typing.DYNAMIC) Object[] args) {
            //Wrap back to proxy for original method
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg == null) {
                    continue;
                }
                try {
                    JavaPlugin plugin = JavaPlugin.getProvidingPlugin(args[i].getClass());
                    NMSProxyProvider proxyProvider = get(plugin);
                    Class proxyClass = proxyProvider.proxyToNMSClassMap.inverse().get(args[i].getClass());
                    if (proxyClass != null) {
                        args[i] = proxyProvider.getNMSObject(proxyClass, arg);
                    }
                } catch (Exception e) {
                }
            }
        }

    }

}
