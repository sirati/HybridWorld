/*
 *  This file is part of Cubic World Generation, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2015-2020 contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package hybridworld.world.gen.structure.feature;

import io.github.opencubicchunks.cubicchunks.api.util.CubePos;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import io.github.opencubicchunks.cubicchunks.api.worldgen.CubePrimer;
import io.github.opencubicchunks.cubicchunks.api.worldgen.structure.ICubicStructureGenerator;
import io.github.opencubicchunks.cubicchunks.cubicgen.StructureGenUtil;
import io.github.opencubicchunks.cubicchunks.cubicgen.customcubic.structure.CaveCommonBaseStructureGenerator;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.github.opencubicchunks.cubicchunks.api.util.Coords.*;
import static io.github.opencubicchunks.cubicchunks.api.worldgen.CubePrimer.DEFAULT_STATE;
import static io.github.opencubicchunks.cubicchunks.cubicgen.StructureGenUtil.normalizedDistance;
import static java.lang.Math.max;
import static java.lang.System.nanoTime;
import static net.minecraft.util.math.MathHelper.*;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CubicCaveConnectorGenerator implements ICubicStructureGenerator {
    private static MethodHandle cubePrimerFastCtor;

    static {
        try {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Constructor<CubePrimer> ctor = CubePrimer.class.getDeclaredConstructor(char[].class);
        ctor.setAccessible(true);
        cubePrimerFastCtor = lookup.unreflectConstructor(ctor);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static  CubePrimer ctorFast(char c) {
        try {
            char[] values = new char[4096];
            Arrays.fill(values, c);
            return (CubePrimer) cubePrimerFastCtor.invokeExact(values);
        } catch (Throwable throwable) {
            throw new IllegalStateException(throwable);
        }
    }

    private final ICubicStructureGenerator[] caveGens;

    public CubicCaveConnectorGenerator(ICubicStructureGenerator... caveGens) {
        this.caveGens = caveGens;
    }


    private boolean isAir(IBlockState state) {
        return state.getMaterial() == Material.AIR;
    }


    private boolean isAir(CubePrimer p, int x, int y, int z) {
        return isAir(p.getBlockState(x, y, z));
    }


    private boolean bothAir(CubePrimer p1, CubePrimer p2, int x, int y, int z) {
        return isAir(p1.getBlockState(x, y, z)) && isAir(p2.getBlockState(x, y, z));
    }

    private Random createRandom(World world, CubePos pos) {
        Random rand = new Random(world.getSeed());
        long randXMul = rand.nextLong();
        long randYMul = rand.nextLong();
        long randZMul = rand.nextLong();
        long seed = randXMul * pos.getX() ^ randYMul * pos.getY() ^ randZMul * pos.getZ();
        rand.setSeed(seed);

        return rand;
    }

    @Override
    public void generate(World world, CubePrimer cubePrimer, CubePos posHere) {
        Random rand = createRandom(world, posHere);
        int value = Block.BLOCK_STATE_IDS.get(Blocks.STONE.getDefaultState());
        char lsb = (char)value;
        CubePrimer here = ctorFast(lsb);
        CubePrimer below = ctorFast(lsb);
        CubePos posBelow = posHere.below();

        for (ICubicStructureGenerator gen:caveGens) {
            gen.generate(world, here, posHere);
            gen.generate(world, below, posBelow);
        }


        int width = (byte) cubeToMaxBlock(0);
        int length = (byte) cubeToMaxBlock(0);
        int height = (byte) cubeToMaxBlock(0);

        //find size of to be reduced cave
        int[][] depth = new int[width+1][length+1];
        int minX = width;
        int maxX = 0;
        int minZ = length;
        int maxZ = 0;
        int maxY = 0;
        for (int x = 0; x <= width; x++) {
            boolean foundX = false;
            
            for (int z = 0; z <= length; z++) {
                if (isAir(below.getBlockState(x, height, z))) {
                    boolean foundZ = false;
                    
                    depth[x][z] = height + 1;
                    boolean foundY = false;
                    for (byte y = 0; y <= height; y++) {
                        if (!isAir(below.getBlockState(x, y, z))) {
                            depth[x][z] = y;
                            maxY = Math.max(maxY, y);
                            foundY = true;
                            break;
                        }
                        foundZ = true;
                    }
                    if (!foundY) {
                        maxY = height;
                    }

                    if (foundZ) {
                        foundX = true;
                        minZ = Math.min(minZ, z);
                        maxZ = Math.max(maxZ, z);
                    }
                }
                
            }
            if (foundX) {
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
            }
        }

        if (maxY == 0) return;

        boolean debug = true;

        System.out.printf("/tp %d %d %d\n", posHere.getXCenter(), posHere.getMinBlockY(), posHere.getMinBlockZ());
        System.out.printf("maxY: %d, minX: %d minZ: %d, maxX: %d maxZ: %d, :\n", maxY, minX, minZ, maxX, maxZ);
        System.out.printf("%s\n", Arrays.stream(depth).map(ints -> Arrays.stream(ints).mapToObj(i ->String.format("%02d", i)).collect(Collectors.joining(",", "[", "]"))).collect(Collectors.joining(",\n")));
        for (int y = 0; y <= maxY; y++) {
            boolean left = rand.nextBoolean();
            boolean right = rand.nextBoolean();
            boolean front = rand.nextBoolean();
            boolean back = rand.nextBoolean();


            //shrink cave
            if (right) {
                int limit = Math.min(maxX, width -1);
                for (int x = minX; x <= limit; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        //we are air but right of us no air
                        if ( depth[x][z] > y && depth[x+1][z] <= y) {
                            depth[x][z] = y; //we become stone
                        }
                    }
                }
            }

            if (left) {
                int limit = Math.max(minX, 1);
                for (int x = maxX; x >= limit; x--) {
                    for (int z = minZ; z <= maxZ; z++) {
                        //we are air but left of us no air
                        if ( depth[x][z] > y && depth[x-1][z] <= y) {
                            depth[x][z] = y; //we become stone
                        }
                    }
                }
            }


            if (front) {
                int limit = Math.min(maxZ, length -1);
                for (int z = minZ; z <= limit; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        //we are air but right of us no air
                        if ( depth[x][z] > y && depth[x][z+1] <= y) {
                            depth[x][z] = y; //we become stone
                        }
                    }
                }
            }

            if (back) {
                int limit = Math.max(minZ, 1);
                for (int z = maxZ; z >= limit; z--) {
                    for (int x = minX; x <= maxX; x++) {
                        //we are air but left of us no air
                        if ( depth[x][z] > y && depth[x][z-1] <= y) {
                            depth[x][z] = y; //we become stone
                        }
                    }
                }
            }


            System.out.printf("Doing layer %d\n", y);
            System.out.printf("maxY: %d, minX: %d minZ: %d, maxX: %d maxZ: %d, :\n", maxY, minX, minZ, maxX, maxZ);
            System.out.printf("%s\n", Arrays.stream(depth).map(ints -> Arrays.stream(ints).mapToObj(i ->String.format("%02d", i)).collect(Collectors.joining(",", "[", "]"))).collect(Collectors.joining("\n")));

            //apply shrunk cave slice
            int newMinX = maxX;
            int newMaxX = minX;
            int newMinZ = maxZ;
            int newMaxZ = minZ;
            boolean foundAny = false;
            for (int x = minX; x <= maxX; x++) {
                boolean found = false;
                for (int z = minZ; z <= maxZ; z++) {
                    if (depth[x][z] > y) {
                        cubePrimer.setBlockState(x, y, z, Blocks.GLASS.getDefaultState());
                        if (debug) {
                            debug = found;
                        }
                        found = true;
                        newMinZ = Math.min(newMinZ, z);
                        newMaxZ = Math.max(newMaxZ, z);
                    }
                }
                if (found) {
                    foundAny = true;
                    newMinX = Math.min(newMinX, x);
                    newMaxX = Math.max(newMaxX, x);
                }
            }

            if(!foundAny) {
                System.out.printf("Finished. Result:\n");
                System.out.printf("%s\n", Arrays.stream(depth).map(ints -> Arrays.stream(ints).mapToObj(i ->String.format("%02d", i)).collect(Collectors.joining(",", "[", "]"))).collect(Collectors.joining("\n")));
                return;
            }

            if (newMinX == newMaxX || newMinZ == newMaxZ)return;

            minX = newMinX;
            maxX = newMaxX;
            minZ = newMinZ;
            maxZ = newMaxZ;
        }
    }
}
