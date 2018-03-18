package me.theminecoder.minecraft.nmsproxy.util;

import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * @author theminecoder
 */
public class ClassUtil {

    private ClassUtil() {
    }

    public static void eachType(Class[] types, Predicate<Class[]> consumer) {
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

}
