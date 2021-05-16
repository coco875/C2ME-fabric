package org.yatopiamc.c2me.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.yatopiamc.c2me.common.config.C2MEConfig;

import java.util.List;
import java.util.Set;

public class C2MEMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("C2ME Mixin");

    @Override
    public void onLoad(String mixinPackage) {
        C2MEConfig.threadedWorldGenConfig.getClass().getName(); // Load configuration
        LOGGER.info("Successfully loaded configuration for C2ME");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.startsWith("org.yatopiamc.c2me.mixin.threading.worldgen."))
            return C2MEConfig.threadedWorldGenConfig.enabled;
        if (mixinClassName.startsWith("org.yatopiamc.c2me.mixin.threading.chunkio."))
            return C2MEConfig.asyncIoConfig.enabled;
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}
