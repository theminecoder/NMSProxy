package me.theminecoder.minecraft.nmsproxy.proxy;

import me.theminecoder.minecraft.nmsproxy.NMSProxy;
import me.theminecoder.minecraft.nmsproxy.annotations.NMSField;
import me.theminecoder.minecraft.nmsproxy.annotations.NMSMethod;
import me.theminecoder.minecraft.nmsproxy.annotations.NMSStatic;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * @author theminecoder
 */
public class NMSProxyInvocationHandler implements InvocationHandler {

    private static Constructor<MethodHandles.Lookup> methodLookupConstructor;

    private final Object handle;
    private final NMSProxyInvocationMapper invocationMapper;
    private final NMSProxyProvider proxyProvider;

    NMSProxyInvocationHandler(Object handle, NMSProxyInvocationMapper invocationMapper, NMSProxyProvider proxyProvider) {
        this.handle = handle;
        this.invocationMapper = invocationMapper;
        this.proxyProvider = proxyProvider;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getName().equals("getProxyHandle")) {
            return handle;
        }

        if (method.getName().equals("getStaticProxyObject")) {
            if (handle == null) {
                return this;
            }

            return proxyProvider.getStaticNMSObject((Class<? extends NMSProxy>) proxy.getClass().getInterfaces()[0]);
        }

        if (method.isDefault()) {
            final Class<?> declaringClass = method.getDeclaringClass();
            if (methodLookupConstructor == null) {
                methodLookupConstructor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class);
                methodLookupConstructor.setAccessible(true);
            }
            return methodLookupConstructor.newInstance(declaringClass, -1) //Trusted Flag
                    .unreflectSpecial(method, declaringClass)
                    .bindTo(proxy)
                    .invokeWithArguments(args);
        }

        if (handle == null && method.getAnnotation(NMSStatic.class) == null) {
            throw new IllegalStateException("Proxy method \"" + method + "\" is attempting to call to instance method/field on a static proxy. Please mark the proxy method with @NMSStatic");
        }

        if (method.getAnnotation(NMSMethod.class) != null || method.getDeclaringClass() == Object.class) {
            NMSMethod nmsMethodAnnotation = method.getAnnotation(NMSMethod.class);

            Object[] fixedArgs = proxyProvider.unwrapArguments(args);
            Class[] fixedArgTypes = Arrays.stream(fixedArgs).map(Object::getClass).toArray(Class[]::new);

            Method nmsMethod = invocationMapper.findNMSMethod((Class<? extends NMSProxy>) proxy.getClass().getInterfaces()[0], method, nmsMethodAnnotation, fixedArgTypes);

            Object invokerObject = method.getAnnotation(NMSStatic.class) != null ? null : handle;
            Object returnObject = nmsMethod.invoke(invokerObject, fixedArgs);

            if (returnObject == null) {
                return null;
            }

            if (method.getName().equals("toString") && method.getParameterCount() == 0) {
                return "Proxy|" + proxy.getClass().getInterfaces()[0].getCanonicalName() + "(" + returnObject + ")";
            }

            if (NMSProxy.class.isAssignableFrom(method.getReturnType())) {
                returnObject = proxyProvider.getNMSObject((Class<? extends NMSProxy>) method.getReturnType(), returnObject);
            }

            return returnObject;
        } else if (method.getAnnotation(NMSField.class) != null) {
            NMSField fieldAnnotation = method.getAnnotation(NMSField.class);

            Field field = invocationMapper.findNMSField((Class<? extends NMSProxy>) proxy.getClass().getInterfaces()[0], method, fieldAnnotation);

            Object invokerObject = method.getAnnotation(NMSStatic.class) != null ? null : handle;
            if (fieldAnnotation.value() == NMSField.Type.GETTER) {
                if (args != null && args.length != 0) {
                    throw new IllegalArgumentException("Must have 0 arguments on proxy method!");
                }

                Object value = field.get(invokerObject);
                if (NMSProxy.class.isAssignableFrom(method.getReturnType())) {
                    value = proxyProvider.getNMSObject((Class<? extends NMSProxy>) method.getReturnType(), value);
                }
                return value;
            } else {
                if (args == null || args.length != 1) {
                    throw new IllegalArgumentException("Must only pass the new value to set!");
                }

                field.set(invokerObject, proxyProvider.unwrapArgument(args[0]));
                return null;
            }
        } else {
            System.out.println("method @'s = " + Arrays.toString(method.getAnnotations()));
            throw new IllegalStateException("Proxy method \"" + method + "\" must have a annotation of either @NMSMethod or @NMSField");
        }
    }
}
