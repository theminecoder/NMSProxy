package me.theminecoder.minecraft.nmsproxy;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import me.theminecoder.minecraft.nmsproxy.annotations.*;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import org.bukkit.Bukkit;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class NMSProxyProviderOld {

    private static final String NMS_VERSION;

    static {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        NMS_VERSION = packageName.substring(packageName.lastIndexOf(".") + 1);
    }

    private static final Map<Class, Class> proxyToNMSClassMap = Maps.newHashMap();
    private static final Map<Class, Class> nmsToProxyClassMap = Maps.newHashMap();

    private static final Map<Class, DynamicType.Loaded> proxyToNMSSubclassMap = Maps.newHashMap();

    private static final Map<Class, Map<Method, Method>> proxyToNmsMethodMap = Maps.newConcurrentMap();
    private static final Map<Class, Map<Method, Field>> proxyToNMSFieldMap = Maps.newConcurrentMap();

    public static <T extends NMSSubclass> T constructSubClassedNMSObject(Class<T> clazz, Object... args) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        if (clazz.getInterfaces().length == 0 || NMSProxy.class.isAssignableFrom(clazz.getInterfaces()[0])) {
            throw new IllegalArgumentException("Class does not implement a NMSProxy interface");
        }

        Class<? extends NMSProxy> proxyClass = (Class<? extends NMSProxy>) clazz.getInterfaces()[0];
        registerNMSClasses(proxyClass, false);

        DynamicType.Loaded<Object> dynamicType = proxyToNMSSubclassMap.get(proxyClass);

        if (dynamicType == null) {
            Class nmsClass = proxyToNMSClassMap.get(proxyClass);
            DynamicType.Builder<Object> builder = new ByteBuddy().subclass(nmsClass).implement(clazz.getInterfaces());

            // Subclasses only implement interfaces
            // TODO Implement default interface bodies
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

            dynamicType = builder.make().load(NMSProxyPlugin.class.getClassLoader());
            proxyToNMSSubclassMap.put(clazz, dynamicType);
        }

        return newSubclassInstance(dynamicType, args);
    }

    private static <T extends NMSSubclass> T newSubclassInstance(DynamicType.Loaded dynamicType, Object... args) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        return ((DynamicType.Loaded<T>) dynamicType)
                .getLoaded()
                .getConstructor(
                        Arrays.stream(args)
                                .map(Object::getClass)
                                .toArray(Class[]::new))
                .newInstance(args);
    }

    public static <T extends NMSProxy> T constructNMSObject(Class<T> clazz, Object... args) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        registerNMSClasses(clazz);

        Object[] fixedArgs = unwrapArguments(args);
        Class[] fixedArgTypes = Arrays.stream(fixedArgs).map(Object::getClass).toArray(Class[]::new);


        final Class nmsClass = proxyToNMSClassMap.get(clazz);

        AtomicReference<Constructor> constructerSearchRef = new AtomicReference<>();
        eachType(fixedArgTypes, (searchTypes) -> {
            Class searchClass = nmsClass;
            do {
                try {
                    constructerSearchRef.set(searchClass.getDeclaredConstructor(searchTypes));
                    return true;
                } catch (NoSuchMethodException ignored) {
                    searchClass = searchClass.getSuperclass();
                }
            } while (constructerSearchRef.get() == null && searchClass != null);
            return false;
        });

        if (constructerSearchRef.get() == null) {
            throw new NoSuchMethodException(nmsClass.getCanonicalName() + ".<init>");
        }

        Constructor constructor = constructerSearchRef.get();
        if (!constructor.isAccessible()) {
            constructor.setAccessible(true);
        }

        Object nmsObject = constructor.newInstance(fixedArgs);

        return getNMSObject(clazz, nmsObject);
    }

//    public static <T extends NMSProxy> T getStaticNMSObject(Class<T> clazz) {
//        registerNMSClasses(clazz);
//        return (T) Proxy.
//    }

    public static <T extends NMSProxy> T getNMSObject(Class<T> clazz, Object object) {
        registerNMSClasses(clazz);
        if (!proxyToNMSClassMap.get(clazz).isAssignableFrom(object.getClass())) {
            throw new IllegalStateException("Object is not of type " + proxyToNMSClassMap.get(clazz).getCanonicalName() + "!");
        }
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, new NMSInvokationHandler(object));
    }

    private static void registerNMSClasses(Class<? extends NMSProxy> clazz) {
        NMSProxyProviderOld.registerNMSClasses(clazz, true);
    }

    private static void registerNMSClasses(Class<? extends NMSProxy> clazz, boolean registerNMSToProxy) {
        if (proxyToNMSClassMap.containsKey(clazz)) {
            return;
        }

        NMSClass nmsClassAnnotation = clazz.getAnnotation(NMSClass.class);
        if (nmsClassAnnotation == null) {
            throw new IllegalStateException("NMSProxy interfaces must have a valid @NMSClass annotation");
        }

        Class nmsClass;
        try {
            nmsClass = Class.forName(nmsClassAnnotation.type().getClassName(NMS_VERSION, nmsClassAnnotation.className()));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Class " + nmsClassAnnotation.className() + " (" + nmsClassAnnotation.type() + ") was not found!");
        }

        proxyToNMSClassMap.put(clazz, nmsClass);
        if (registerNMSToProxy)
            nmsToProxyClassMap.put(nmsClass, clazz);
    }

    private static Object[] unwrapArguments(Object[] args) {
        if (args == null) {
            return new Object[]{};
        }

        return Arrays.stream(args).map(NMSProxyProviderOld::unwrapArgument).toArray(Object[]::new);
    }

    private static Object unwrapArgument(Object arg) {
        if (arg == null) {
            return null;
        }

        if (arg instanceof NMSProxy) {
            registerNMSClasses((Class<? extends NMSProxy>) arg.getClass().getInterfaces()[0]);
            return ((NMSProxy) arg).getProxyHandle();
        }

        return arg;
    }

    private static void eachType(Class[] types, Predicate<Class[]> consumer) {
        List<List<Class>> classPosibilitys = Lists.newArrayList();
        Arrays.stream(types).forEach(type -> {
            List<Class> possibleClasses = Lists.newArrayList();
            if (type == Byte.class) {
                possibleClasses.add(byte.class);
            } else if (type == Short.class) {
                possibleClasses.add(short.class);
            } else if (type == Integer.class) {
                possibleClasses.add(int.class);
            } else if (type == Long.class) {
                possibleClasses.add(long.class);
            } else if (type == Float.class) {
                possibleClasses.add(float.class);
            } else if (type == Double.class) {
                possibleClasses.add(double.class);
            } else if (type == Character.class) {
                possibleClasses.add(char.class);
            } else if (type == Boolean.class) {
                possibleClasses.add(boolean.class);
            } else if (type == byte.class) {
                possibleClasses.add(byte.class);
                type = Byte.class;
            } else if (type == short.class) {
                possibleClasses.add(short.class);
                type = Short.class;
            } else if (type == int.class) {
                possibleClasses.add(int.class);
                type = Integer.class;
            } else if (type == long.class) {
                possibleClasses.add(long.class);
                type = Long.class;
            } else if (type == float.class) {
                possibleClasses.add(float.class);
                type = Float.class;
            } else if (type == double.class) {
                possibleClasses.add(double.class);
                type = Double.class;
            } else if (type == char.class) {
                possibleClasses.add(char.class);
                type = Character.class;
            } else if (type == boolean.class) {
                possibleClasses.add(boolean.class);
                type = Boolean.class;
            }
            do {
                possibleClasses.add(type);
                possibleClasses.addAll(Arrays.asList(type.getInterfaces()));
                type = type.getSuperclass();
            } while (type != Object.class);
            possibleClasses.add(Object.class);
            classPosibilitys.add(possibleClasses);
        });

        int solutions = 1;
        for (int i = 0; i < classPosibilitys.size(); solutions *= classPosibilitys.get(i).size(), i++) ;
        for (int i = 0; i < solutions; i++) {
            int j = 1;
            List<Class> consumerInstance = Lists.newArrayList();
            for (List<Class> set : classPosibilitys) {
                consumerInstance.add(set.get((i / j) % set.size()));
                j *= set.size();
            }
//            System.out.println("Searching with " + consumerInstance);
            if (consumer.test(consumerInstance.stream().toArray(Class[]::new)))
                return;
        }
    }

    /**
     * @author theminecoder
     */
    private static class NMSInvokationHandler implements InvocationHandler {

        private Object handle;

        public NMSInvokationHandler(Object handle) {
            this.handle = handle;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("getProxyHandle")) {
                return handle;
            }

            if (method.getAnnotation(NMSMethod.class) != null || method.getDeclaringClass() == Object.class) {
                NMSMethod nmsMethodAnnotation = method.getAnnotation(NMSMethod.class);

                Object[] fixedArgs = unwrapArguments(args);
                Class[] fixedArgTypes = Arrays.stream(fixedArgs).map(Object::getClass).toArray(Class[]::new);

                Map<Method, Method> methodMap = proxyToNmsMethodMap.computeIfAbsent(proxy.getClass().getInterfaces()[0], key -> Maps.newConcurrentMap());
                Method nmsMethod = methodMap.get(method);
                if (nmsMethod == null) {
                    String methodName = method.getName();

                    if (method.getDeclaringClass() != Object.class) {
                        for (NMSVersionName methodVersion :
                                nmsMethodAnnotation.versionNames()) {
                            if (methodVersion.version().equalsIgnoreCase(NMS_VERSION)) {
                                methodName = methodVersion.name();
                                break;
                            }
                        }
                    }

                    final Class nmsClass = proxyToNMSClassMap.get(proxy.getClass().getInterfaces()[0]);

                    AtomicReference<Method> methodSearchRef = new AtomicReference<>();
                    String finalMethodName = methodName;
                    eachType(fixedArgTypes, (searchTypes) -> {
                        Class searchClass = nmsClass;
                        do {
                            try {
                                methodSearchRef.set(searchClass.getDeclaredMethod(finalMethodName, searchTypes));
                                return true;
                            } catch (NoSuchMethodException ignored) {
                                searchClass = searchClass.getSuperclass();
                            }
                        } while (methodSearchRef.get() == null && searchClass != null);
                        return false;
                    });

                    if (methodSearchRef.get() == null) {
                        throw new NoSuchMethodException(nmsClass.getCanonicalName() + "." + methodName);
                    }

                    nmsMethod = methodSearchRef.get();
                    if (!nmsMethod.isAccessible()) {
                        nmsMethod.setAccessible(true);
                    }

                    methodMap.put(method, nmsMethod);
                }

                Object invokerObject = method.getAnnotation(NMSStaticMethod.class) != null ? null : handle;
                Object returnObject = nmsMethod.invoke(invokerObject, fixedArgs);

                if (returnObject == null) {
                    return null;
                }

                if (NMSProxy.class.isAssignableFrom(method.getReturnType())) {
                    returnObject = getNMSObject((Class<? extends NMSProxy>) method.getReturnType(), returnObject);
                }

                return returnObject;
            } else if (method.getAnnotation(NMSField.class) != null) {
                NMSField fieldAnnotation = method.getAnnotation(NMSField.class);
                Map<Method, Field> fieldMap = proxyToNMSFieldMap.computeIfAbsent(proxy.getClass().getInterfaces()[0], key -> Maps.newConcurrentMap());

                Field field = fieldMap.get(method);
                if (field == null) {
                    String fieldName = method.getName();

                    for (NMSVersionName versionName : fieldAnnotation.versionNames()) {
                        if (versionName.version().equalsIgnoreCase(NMS_VERSION)) {
                            fieldName = versionName.name();
                            break;
                        }
                    }

                    registerNMSClasses((Class<? extends NMSProxy>) proxy.getClass().getInterfaces()[0]);
                    final Class nmsClass = proxyToNMSClassMap.get(proxy.getClass().getInterfaces()[0]);
                    Class searchClass = nmsClass;
                    do {
                        try {
                            field = searchClass.getDeclaredField(fieldName);
                        } catch (NoSuchFieldException ignored) {
                            searchClass = searchClass.getSuperclass();
                        }
                    } while (field != null && searchClass == Object.class);

                    if (field == null) {
                        throw new NoSuchFieldException(nmsClass.getCanonicalName() + "#" + fieldName);
                    }

                    if (!field.isAccessible()) {
                        field.setAccessible(true);
                    }

                    if (Modifier.isFinal(field.getModifiers())) {
                        Field modifierField = Field.class.getDeclaredField("modifiers");
                        modifierField.setAccessible(true);
                        modifierField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
                    }

                    fieldMap.put(method, field);
                }

                if (fieldAnnotation.type() == NMSField.Type.GETTER) {
                    if (args != null && args.length != 0) {
                        throw new IllegalArgumentException("Must have 0 arguments on proxy method!");
                    }
                    Object value = field.get(this.handle);
                    if (NMSProxy.class.isAssignableFrom(method.getReturnType())) {
                        value = getNMSObject((Class<? extends NMSProxy>) method.getReturnType(), value);
                    }
                    return value;
                } else {
                    if (args == null || args.length != 1) {
                        throw new IllegalArgumentException("Must only pass the new value to set!");
                    }

                    field.set(this.handle, unwrapArgument(args[0]));
                    return null;
                }
            } else {
                System.out.println("method @'s = " + Arrays.toString(method.getAnnotations()));
                throw new IllegalStateException("Proxy method \"" + method + "\" must have a annotation of either @NMSMethod or @NMSField");
            }
        }
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
                Class proxyClass = nmsToProxyClassMap.get(args[i].getClass());
                if (proxyClass != null) {
                    args[i] = getNMSObject(proxyClass, arg);
                }
            }
        }

    }
}
