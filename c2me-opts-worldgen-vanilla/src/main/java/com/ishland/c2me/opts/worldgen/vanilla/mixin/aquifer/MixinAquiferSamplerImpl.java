package com.ishland.c2me.opts.worldgen.vanilla.mixin.aquifer;

import com.ishland.c2me.opts.worldgen.general.common.random_instances.RandomUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.random.RandomSplitter;
import net.minecraft.world.biome.source.util.VanillaBiomeParameters;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.chunk.AquiferSampler;
import net.minecraft.world.gen.chunk.ChunkNoiseSampler;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AquiferSampler.Impl.class)
public abstract class MixinAquiferSamplerImpl {

    @Shadow
    @Final
    private int startX;

    @Shadow
    @Final
    private int startY;

    @Shadow
    @Final
    private int startZ;

    @Shadow
    @Final
    private int sizeZ;

    @Shadow @Final private int sizeX;

    @Shadow @Final private long[] blockPositions;

    @Shadow @Final private RandomSplitter randomDeriver;

    @Shadow
    @Final
    private AquiferSampler.FluidLevel[] waterLevels;

    @Shadow
    @Final
    private static int[][] CHUNK_POS_OFFSETS;

    @Shadow
    @Final
    private ChunkNoiseSampler chunkNoiseSampler;

    @Shadow
    @Final
    private DensityFunction barrierNoise;

    @Shadow
    @Final
    private DensityFunction fluidLevelFloodednessNoise;

    @Shadow
    @Final
    private DensityFunction fluidLevelSpreadNoise;

    @Shadow
    @Final
    private DensityFunction fluidTypeNoise;

    @Shadow
    @Final
    private static double NEEDS_FLUID_TICK_DISTANCE_THRESHOLD;

    @Shadow
    private boolean needsFluidTick;

    @Shadow
    @Final
    private AquiferSampler.FluidLevelSampler fluidLevelSampler;

    @Shadow protected abstract int index(int x, int y, int z);

    @Shadow
    protected static double maxDistance(int i, int a) {
        throw new AbstractMethodError();
    }

    @Shadow protected abstract int getNoiseBasedFluidLevel(int blockX, int blockY, int blockZ, int surfaceHeightEstimate);

    @Shadow protected abstract AquiferSampler.FluidLevel getFluidLevel(int blockX, int blockY, int blockZ);

    @Shadow @Final private DensityFunction erosionDensityFunction;

    @Shadow @Final private DensityFunction depthDensityFunction;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo info) {
        // preload position cache
        if (this.blockPositions.length % (this.sizeX * this.sizeZ) != 0) {
            throw new AssertionError("Array length");
        }
        int sizeY = this.blockPositions.length / (this.sizeX * this.sizeZ);
        final Random random = RandomUtils.getRandom(this.randomDeriver);
        // index: z, x, y
        for (int z = 0; z < this.sizeZ; z++) {
            for (int x = 0; x < this.sizeX; x++) {
                for (int y = 0; y < sizeY; y++) {
                    final int x1 = x + this.startX;
                    final int y1 = y + this.startY;
                    final int z1 = z + this.startZ;
                    RandomUtils.derive(this.randomDeriver, random, x1, y1, z1);
                    this.blockPositions[this.index(x1, y1, z1)] = BlockPos.asLong(x1 * 16 + random.nextInt(10), y1 * 12 + random.nextInt(9), z1 * 16 + random.nextInt(10));
                }
            }
        }
        for (long blockPosition : this.blockPositions) {
            if (blockPosition == Long.MAX_VALUE) {
                throw new AssertionError("Array initialization");
            }
        }
    }

    /**
     * @author ishland
     * @reason make C2 happier by splitting method into three
     */
    @Overwrite
    public BlockState apply(DensityFunction.NoisePos pos, double density) {
        int i = pos.blockX();
        int j = pos.blockY();
        int k = pos.blockZ();
        if (density > 0.0) {
            this.needsFluidTick = false;
            return null;
        } else {
            AquiferSampler.FluidLevel fluidLevel = this.fluidLevelSampler.getFluidLevel(i, j, k);
            if (fluidLevel.getBlockState(j).isOf(Blocks.LAVA)) {
                this.needsFluidTick = false;
                return Blocks.LAVA.getDefaultState();
            } else {
                final Result1 result = aquiferExtracted$getResult1(i, j, k);

                AquiferSampler.FluidLevel fluidLevel2 = this.getWaterLevel(result.r());
                double d = maxDistance(result.o(), result.p());
                BlockState blockState = fluidLevel2.getBlockState(j);
                if (d <= 0.0) {
                    this.needsFluidTick = d >= NEEDS_FLUID_TICK_DISTANCE_THRESHOLD;
                    return blockState;
                } else if (blockState.isOf(Blocks.WATER) && this.fluidLevelSampler.getFluidLevel(i, j - 1, k).getBlockState(j - 1).isOf(Blocks.LAVA)) {
                    this.needsFluidTick = true;
                    return blockState;
                } else {
                    MutableDouble mutableDouble = new MutableDouble(Double.NaN);
                    AquiferSampler.FluidLevel fluidLevel3 = this.getWaterLevel(result.s());
                    double e = d * this.calculateDensity(pos, mutableDouble, fluidLevel2, fluidLevel3);
                    if (density + e > 0.0) {
                        this.needsFluidTick = false;
                        return null;
                    } else {
                        return aquiferExtracted$getFinalBlockState(pos, density, result, d, mutableDouble, fluidLevel2, fluidLevel3, blockState);
                    }
                }
            }
        }
    }

    @Unique
    private BlockState aquiferExtracted$getFinalBlockState(DensityFunction.NoisePos pos, double density, Result1 result, double d, MutableDouble mutableDouble, AquiferSampler.FluidLevel fluidLevel2, AquiferSampler.FluidLevel fluidLevel3, BlockState blockState) {
        AquiferSampler.FluidLevel fluidLevel4 = this.getWaterLevel(result.t());
        double f = maxDistance(result.o(), result.q());
        if (f > 0.0) {
            double g = d * f * this.calculateDensity(pos, mutableDouble, fluidLevel2, fluidLevel4);
            if (density + g > 0.0) {
                this.needsFluidTick = false;
                return null;
            }
        }

        double g = maxDistance(result.p(), result.q());
        if (g > 0.0) {
            double h = d * g * this.calculateDensity(pos, mutableDouble, fluidLevel3, fluidLevel4);
            if (density + h > 0.0) {
                this.needsFluidTick = false;
                return null;
            }
        }

        this.needsFluidTick = true;
        return blockState;
    }

    @Unique
    @NotNull
    private Result1 aquiferExtracted$getResult1(int i, int j, int k) {
        int l = (i - 5) >> 4;
        int m = Math.floorDiv(j + 1, 12);
        int n = (k - 5) >> 4;
        int o = Integer.MAX_VALUE;
        int p = Integer.MAX_VALUE;
        int q = Integer.MAX_VALUE;
        long r = 0L;
        long s = 0L;
        long t = 0L;

        for(int u = 0; u <= 1; ++u) {
            for(int v = -1; v <= 1; ++v) {
                for(int w = 0; w <= 1; ++w) {
                    int x = l + u;
                    int y = m + v;
                    int z = n + w;
                    int aa = this.index(x, y, z);
                    long ab = this.blockPositions[aa];
                    long ac = ab; // cache preloaded

                    int ad = BlockPos.unpackLongX(ac) - i;
                    int ae = BlockPos.unpackLongY(ac) - j;
                    int af = BlockPos.unpackLongZ(ac) - k;
                    int ag = ad * ad + ae * ae + af * af;
                    if (o >= ag) {
                        t = s;
                        s = r;
                        r = ac;
                        q = p;
                        p = o;
                        o = ag;
                    } else if (p >= ag) {
                        t = s;
                        s = ac;
                        q = p;
                        p = ag;
                    } else if (q >= ag) {
                        t = ac;
                        q = ag;
                    }
                }
            }
        }
        return new Result1(o, p, q, r, s, t);
    }

    @SuppressWarnings("MixinInnerClass")
    private record Result1(int o, int p, int q, long r, long s, long t) {
    }


    /**
     * @author ishland
     * @reason optimize
     */
    @Overwrite
    private AquiferSampler.FluidLevel getWaterLevel(long pos) {
        int i = BlockPos.unpackLongX(pos);
        int j = BlockPos.unpackLongY(pos);
        int k = BlockPos.unpackLongZ(pos);
        int l = i >> 4; // C2ME - inline: floorDiv(i, 16)
        int m = Math.floorDiv(j, 12); // C2ME - inline
        int n = k >> 4; // C2ME - inline: floorDiv(k, 16)
        int o = this.index(l, m, n);
        AquiferSampler.FluidLevel fluidLevel = this.waterLevels[o];
        if (fluidLevel != null) {
            return fluidLevel;
        } else {
            AquiferSampler.FluidLevel fluidLevel2 = this.getFluidLevel(i, j, k);
            this.waterLevels[o] = fluidLevel2;
            return fluidLevel2;
        }
    }

    /**
     * @author ishland
     * @reason optimize
     */
    @Overwrite
    private int getFluidBlockY(int blockX, int blockY, int blockZ, AquiferSampler.FluidLevel defaultFluidLevel, int surfaceHeightEstimate, boolean bl) {
        DensityFunction.UnblendedNoisePos unblendedNoisePos = new DensityFunction.UnblendedNoisePos(blockX, blockY, blockZ);
        double d;
        double e;
        if (VanillaBiomeParameters.inDeepDarkParameters(this.erosionDensityFunction, this.depthDensityFunction, unblendedNoisePos)) {
            d = -1.0;
            e = -1.0;
        } else {
            int i = surfaceHeightEstimate + 8 - blockY;
            double f = bl ? MathHelper.clampedLerp(1.0, 0.0, ((double) i) / 64.0) : 0.0; // inline
            double g = MathHelper.clamp(this.fluidLevelFloodednessNoise.sample(unblendedNoisePos), -1.0, 1.0);
            d = g + 0.8 + (f - 1.0) * 1.2; // inline
            e = g + 0.3 + (f - 1.0) * 1.1; // inline
        }

        int i;
        if (e > 0.0) {
            i = defaultFluidLevel.y;
        } else if (d > 0.0) {
            i = this.getNoiseBasedFluidLevel(blockX, blockY, blockZ, surfaceHeightEstimate);
        } else {
            i = DimensionType.field_35479;
        }

        return i;
    }

    /**
     * @author ishland
     * @reason optimize, split method into two
     */
    @Overwrite
    private double calculateDensity(
            DensityFunction.NoisePos pos, MutableDouble mutableDouble, AquiferSampler.FluidLevel fluidLevel, AquiferSampler.FluidLevel fluidLevel2
    ) {
        int i = pos.blockY();
        BlockState blockState = fluidLevel.getBlockState(i);
        BlockState blockState2 = fluidLevel2.getBlockState(i);
        if ((!blockState.isOf(Blocks.LAVA) || !blockState2.isOf(Blocks.WATER)) && (!blockState.isOf(Blocks.WATER) || !blockState2.isOf(Blocks.LAVA))) {
            int j = Math.abs(fluidLevel.y - fluidLevel2.y);
            if (j == 0) {
                return 0.0;
            } else {
                double d = 0.5 * (double)(fluidLevel.y + fluidLevel2.y);
                final double q = aquiferExtracted$getQ(i, d, j);

                double r;
                if (!(q < -2.0) && !(q > 2.0)) {
                    double s = mutableDouble.getValue();
                    if (Double.isNaN(s)) {
                        double t = this.barrierNoise.sample(pos);
                        mutableDouble.setValue(t);
                        r = t;
                    } else {
                        r = s;
                    }
                } else {
                    r = 0.0;
                }

                return 2.0 * (r + q);
            }
        } else {
            return 2.0;
        }
    }

    @Unique
    private static double aquiferExtracted$getQ(double i, double d, double j) {
        double e = i + 0.5 - d;
        double f = j / 2.0;
        double o = f - Math.abs(e);
        double q;
        if (e > 0.0) {
            if (o > 0.0) {
                q = o / 1.5;
            } else {
                q = o / 2.5;
            }
        } else {
            double p = 3.0 + o;
            if (p > 0.0) {
                q = p / 3.0;
            } else {
                q = p / 10.0;
            }
        }
        return q;
    }

    /**
     * @author ishland
     * @reason optimize
     */
    @Overwrite
    private BlockState getFluidBlockState(int blockX, int blockY, int blockZ, AquiferSampler.FluidLevel defaultFluidLevel, int fluidLevel) {
        BlockState blockState = defaultFluidLevel.state;
        if (fluidLevel <= -10 && fluidLevel != DimensionType.field_35479 && defaultFluidLevel.state != Blocks.LAVA.getDefaultState()) {
            int k = blockX >> 6; // floorDiv(blockX, 64)
            int l = Math.floorDiv(blockY, 40);
            int m = blockZ >> 6; // floorDiv(blockZ, 64)
            double d = this.fluidTypeNoise.sample(new DensityFunction.UnblendedNoisePos(k, l, m));
            if (Math.abs(d) > 0.3) {
                blockState = Blocks.LAVA.getDefaultState();
            }
        }

        return blockState;
    }

}
