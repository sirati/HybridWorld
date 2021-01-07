package hybridworld.world.gen;

import static hybridworld.HybridWorldMod.LOGGER;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.CharBuffer;

import hybridworld.world.gen.structure.feature.CubicCaveConnectorGenerator;
import io.github.opencubicchunks.cubicchunks.api.util.CubePos;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import io.github.opencubicchunks.cubicchunks.api.worldgen.CubePrimer;
import io.github.opencubicchunks.cubicchunks.core.worldgen.generator.vanilla.VanillaCompatibilityGenerator;
import io.github.opencubicchunks.cubicchunks.cubicgen.CustomCubicMod;
import io.github.opencubicchunks.cubicchunks.cubicgen.customcubic.CustomGeneratorSettings;
import io.github.opencubicchunks.cubicchunks.cubicgen.customcubic.CustomTerrainGenerator;
import io.github.opencubicchunks.cubicchunks.cubicgen.preset.CustomGenSettingsSerialization;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.gen.IChunkGenerator;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class HybridTerrainGenerator extends VanillaCompatibilityGenerator {

	private static final String FILE_NAME = "custom_generator_settings.json";
	private CustomTerrainGenerator2 cubicGenerator;
	private final World world;
	private CubicCaveConnectorGenerator caveConnectorGenerator;
	
	public HybridTerrainGenerator(IChunkGenerator vanilla, World world) {
		super(vanilla, world);
		this.world = world;
		this.onLoad(world);
	}

	public void onLoad(World world) {
		int dimension = world.provider.getDimension();
		String settingJsonString = loadJsonStringFromSaveFolder(world, FILE_NAME);
		CustomGeneratorSettings settings = null;
		if (settingJsonString != null) {
			settings = CustomGeneratorSettings.fromJson(settingJsonString);
		} else {
			settings = DefaultGeneratorSettings.get(dimension);
			if (settings != null)
				saveJsonStringToSaveFolder(world, FILE_NAME, settings.toJsonObject().toJson(CustomGenSettingsSerialization.OUT_GRAMMAR));
		}
		if (settings == null)
			return;
		this.cubicGenerator = new CustomTerrainGenerator2(world, world.getBiomeProvider(), settings, world.getSeed());
		this.caveConnectorGenerator = new CubicCaveConnectorGenerator(cubicGenerator.getCaveGenerator(), cubicGenerator.getRavineGenerator());
	}

	@Override
	public CubePrimer generateCube(int cubeX, int cubeY, int cubeZ) {
		if (cubeY >= 0 && cubeY < 16) {
			CubePrimer result = super.generateCube(cubeX, cubeY, cubeZ);
			CubePos pos =  new CubePos(cubeX, cubeY, cubeZ);
			if (cubeY == 0 || cubeY == 15) {
				cubicGenerator.generateStructures(result,pos);
			} else {
				if (cubeY == 1) {
					caveConnectorGenerator.generate(this.world, result, pos);
				}
				cubicGenerator.getStrongholds().generate(this.world, result, pos);
			}
			return result;
		}
		if (cubicGenerator == null)
			return super.generateCube(cubeX, cubeY, cubeZ);
		return cubicGenerator.generateCube(cubeX, cubeY, cubeZ);
	}

	@Override
	public void populate(ICube cube) {
		super.populate(cube);
		int cubeY = cube.getY();
		if (cubicGenerator != null && (cubeY < 0 || cubeY >= 16))
			cubicGenerator.populate(cube);
	}

	private static File getSettingsFile(World world, String fileName) {
		File worldDirectory = world.getSaveHandler().getWorldDirectory();
		String subfolder = world.provider.getSaveFolder();
		if (subfolder == null)
			subfolder = "";
		else
			subfolder += "/";
		File settings = new File(worldDirectory, "./" + subfolder + "data/" + CustomCubicMod.MODID + "/" + fileName);
		return settings;
	}

	public static String loadJsonStringFromSaveFolder(World world, String fileName) {
		File settings = getSettingsFile(world, fileName);
		if (settings.exists()) {
			try (FileReader reader = new FileReader(settings)) {
				CharBuffer sb = CharBuffer.allocate((int) settings.length());
				reader.read(sb);
				sb.flip();
				return sb.toString();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			LOGGER.info("No settings provided at path:" + settings.toString());
		}
		return null;
	}
	
	public static void saveJsonStringToSaveFolder(World world, String fileName, String json) {
		File settings = getSettingsFile(world, fileName);
		settings.getParentFile().mkdirs();
		try (FileWriter writer = new FileWriter(settings)) {
			writer.write(json);
			CustomCubicMod.LOGGER.info("Default generator settings saved at " + settings.getAbsolutePath());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
    @Override
	public BlockPos getClosestStructure(String name, BlockPos pos, boolean findUnexplored) {
		BlockPos vanillaStructurePos = super.getClosestStructure(name, pos, findUnexplored);
		if (cubicGenerator == null || (!name.equals("Strongholds") && !name.equals("CubicStrongholds")))
			return vanillaStructurePos;
		BlockPos cubicPos = cubicGenerator.getClosestStructure("Strongholds", pos, findUnexplored);
		if (vanillaStructurePos == null) {
			return cubicPos;
		}
		if (cubicPos == null) {
			return vanillaStructurePos;
		} else if (pos.distanceSq(cubicPos) < pos.distanceSq(vanillaStructurePos)) {
			return cubicPos;
		}
		return vanillaStructurePos;
	}

	public class CustomTerrainGenerator2 extends CustomTerrainGenerator {
		public CustomTerrainGenerator2(World world, long seed) {
			super(world, seed);
		}

		public CustomTerrainGenerator2(World world, BiomeProvider biomeProvider, CustomGeneratorSettings settings, long seed) {
			super(world, biomeProvider, settings, seed);
		}

		@Override
		public void reloadPreset(String settings) {
			super.reloadPreset(settings);
			caveConnectorGenerator = new CubicCaveConnectorGenerator(getCaveGenerator(), getRavineGenerator());
		}
	}

}
