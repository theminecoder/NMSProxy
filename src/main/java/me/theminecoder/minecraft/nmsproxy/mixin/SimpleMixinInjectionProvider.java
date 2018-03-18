package me.theminecoder.minecraft.nmsproxy.mixin;

import com.google.common.collect.ImmutableSet;
import me.theminecoder.minecraft.nmsproxy.mixin.graph.DependencyGraph;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author theminecoder
 */
public class SimpleMixinInjectionProvider implements MixinInjectionProvider {

    private boolean needsRebuild = false;

    private List<Class<? extends NMSMixinClass>> mixinLoadOrder = new ArrayList<>();
    private DependencyGraph<Class<? extends NMSMixinClass>> mixinDependancyGraph = new DependencyGraph<>(mixinLoadOrder::add);

    @Override
    public void inject(Class<? extends NMSMixinClass> mixinClass) {
        mixinDependancyGraph.addDependency(mixinClass, null);
        needsRebuild = true;
    }

    @Override
    public void injectBefore(Class<? extends NMSMixinClass> mixinClass, Class<? extends NMSMixinClass> mixinBeforeTarget) {
        mixinDependancyGraph.addDependency(mixinBeforeTarget, mixinClass);
        needsRebuild = true;
    }

    @Override
    public void injectAfter(Class<? extends NMSMixinClass> mixinClass, Class<? extends NMSMixinClass> mixinAfterTarget) {
        mixinDependancyGraph.addDependency(mixinClass, mixinAfterTarget);
        needsRebuild = true;
    }

    public Set<Class<? extends NMSMixinClass>> getMixinClasses() {
        if (needsRebuild) {
            mixinLoadOrder.clear();
            mixinDependancyGraph.generateDependencies();
        }
        return ImmutableSet.copyOf(mixinLoadOrder.stream().filter(Objects::nonNull).collect(Collectors.toSet()));
    }
}
