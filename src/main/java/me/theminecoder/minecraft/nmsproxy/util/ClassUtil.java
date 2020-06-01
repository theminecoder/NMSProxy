package me.theminecoder.minecraft.nmsproxy.util;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * @author theminecoder
 */
@SuppressWarnings("rawtypes")
public final class ClassUtil {

    private static final BiMap<Class, Class> PRIMITIVE_TO_CHECK_CLASS_MAP = HashBiMap.create();

    static {
        PRIMITIVE_TO_CHECK_CLASS_MAP.put(Byte.class, byte.class);
        PRIMITIVE_TO_CHECK_CLASS_MAP.put(Short.class, short.class);
        PRIMITIVE_TO_CHECK_CLASS_MAP.put(Integer.class, int.class);
        PRIMITIVE_TO_CHECK_CLASS_MAP.put(Long.class, long.class);
        PRIMITIVE_TO_CHECK_CLASS_MAP.put(Float.class, float.class);
        PRIMITIVE_TO_CHECK_CLASS_MAP.put(Double.class, double.class);
        PRIMITIVE_TO_CHECK_CLASS_MAP.put(Character.class, char.class);
        PRIMITIVE_TO_CHECK_CLASS_MAP.put(Boolean.class, boolean.class);
    }

    private ClassUtil() {
    }

    public static void forEachClassPossibility(Class[] types, Predicate<Class[]> consumer) {
        List<List<Class>> classPossibilities = Lists.newArrayList();
        Arrays.stream(types).forEach(type -> {
            List<Class> possibleClasses = Lists.newArrayList();

            if (PRIMITIVE_TO_CHECK_CLASS_MAP.containsKey(type)) {
                possibleClasses.add(PRIMITIVE_TO_CHECK_CLASS_MAP.get(type));
            } else if (PRIMITIVE_TO_CHECK_CLASS_MAP.inverse().containsKey(type)) {
                possibleClasses.add(type);
                type = PRIMITIVE_TO_CHECK_CLASS_MAP.inverse().get(type);
            }

            do {
                possibleClasses.add(type);
                possibleClasses.addAll(Arrays.asList(type.getInterfaces()));
                type = type.getSuperclass();
            } while (type != Object.class);
            possibleClasses.add(Object.class);
            classPossibilities.add(possibleClasses);
        });

        int solutions = 1;
        //noinspection StatementWithEmptyBody
        for (int i = 0; i < classPossibilities.size(); solutions *= classPossibilities.get(i).size(), i++) ;
        for (int i = 0; i < solutions; i++) {
            int j = 1;
            List<Class> consumerInstance = Lists.newArrayList();
            for (List<Class> set : classPossibilities) {
                consumerInstance.add(set.get((i / j) % set.size()));
                j *= set.size();
            }

            if (consumer.test(consumerInstance.stream().toArray(Class[]::new)))
                return;
        }
    }

}
