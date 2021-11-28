package net.coderbot.iris.shaderpack;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.blending.AlphaTestFunction;
import net.coderbot.iris.gl.blending.AlphaTestOverride;

/**
 * The parsed representation of the shaders.properties file. This class is not meant to be stored permanently, rather
 * it merely exists as an intermediate step until we build up PackDirectives and ProgramDirectives objects from the
 * values in here & the values parsed from shader source code.
 */
public class ShaderProperties {
	private boolean enableClouds = true;
	private OptionalBoolean oldHandLight = OptionalBoolean.DEFAULT;
	private OptionalBoolean dynamicHandLight = OptionalBoolean.DEFAULT;
	private OptionalBoolean oldLighting = OptionalBoolean.DEFAULT;
	private OptionalBoolean shadowTerrain = OptionalBoolean.DEFAULT;
	private OptionalBoolean shadowTranslucent = OptionalBoolean.DEFAULT;
	private OptionalBoolean shadowEntities = OptionalBoolean.DEFAULT;
	private OptionalBoolean shadowBlockEntities = OptionalBoolean.DEFAULT;
	private OptionalBoolean underwaterOverlay = OptionalBoolean.DEFAULT;
	private OptionalBoolean sun = OptionalBoolean.DEFAULT;
	private OptionalBoolean moon = OptionalBoolean.DEFAULT;
	private OptionalBoolean vignette = OptionalBoolean.DEFAULT;
	private OptionalBoolean backFaceSolid = OptionalBoolean.DEFAULT;
	private OptionalBoolean backFaceCutout = OptionalBoolean.DEFAULT;
	private OptionalBoolean backFaceCutoutMipped = OptionalBoolean.DEFAULT;
	private OptionalBoolean backFaceTranslucent = OptionalBoolean.DEFAULT;
	private OptionalBoolean rainDepth = OptionalBoolean.DEFAULT;
	private OptionalBoolean beaconBeamDepth = OptionalBoolean.DEFAULT;
	private OptionalBoolean separateAo = OptionalBoolean.DEFAULT;
	private OptionalBoolean frustumCulling = OptionalBoolean.DEFAULT;
	private List<String> sliderOptions = new ArrayList<>();
	private final Map<String, List<String>> profiles = new LinkedHashMap<>();
	private List<String> mainScreenOptions = new ArrayList<>();
	private final Map<String, List<String>> subScreenOptions = new HashMap<>();
	private Integer mainScreenColumnCount = null;
	private final Map<String, Integer> subScreenColumnCount = new HashMap<>();
	// TODO: Custom textures
	// TODO: private Map<String, String> optifineVersionRequirements;
	// TODO: Parse custom uniforms / variables
	private final Object2ObjectMap<String, AlphaTestOverride> alphaTestOverrides = new Object2ObjectOpenHashMap<>();
	private final Object2FloatMap<String> viewportScaleOverrides = new Object2FloatOpenHashMap<>();
	private final ObjectSet<String> blendDisabled = new ObjectOpenHashSet<>();
	private String noiseTexturePath = null;

	private ShaderProperties() {
		// empty
	}

	public ShaderProperties(Properties properties) {
		properties.forEach((keyObject, valueObject) -> {
			String key = (String) keyObject;
			String value = (String) valueObject;

			if ("texture.noise".equals(key)) {
				noiseTexturePath = value;
			}

			if ("clouds".equals(key) && value.equals("off")) {
				// TODO: Force clouds to fast / fancy as well if the shaderpack wants it
				enableClouds = false;
			}

			handleBooleanDirective(key, value, "oldHandLight", bool -> oldHandLight = bool);
			handleBooleanDirective(key, value, "dynamicHandLight", bool -> dynamicHandLight = bool);
			handleBooleanDirective(key, value, "oldLighting", bool -> oldLighting = bool);
			handleBooleanDirective(key, value, "shadowTerrain", bool -> shadowTerrain = bool);
			handleBooleanDirective(key, value, "shadowTranslucent", bool -> shadowTranslucent = bool);
			handleBooleanDirective(key, value, "shadowEntities", bool -> shadowEntities = bool);
			handleBooleanDirective(key, value, "shadowBlockEntities", bool -> shadowBlockEntities = bool);
			handleBooleanDirective(key, value, "underwaterOverlay", bool -> underwaterOverlay = bool);
			handleBooleanDirective(key, value, "sun", bool -> sun = bool);
			handleBooleanDirective(key, value, "moon", bool -> moon = bool);
			handleBooleanDirective(key, value, "vignette", bool -> vignette = bool);
			handleBooleanDirective(key, value, "backFace.solid", bool -> backFaceSolid = bool);
			handleBooleanDirective(key, value, "backFace.cutout", bool -> backFaceCutout = bool);
			handleBooleanDirective(key, value, "backFace.cutoutMipped", bool -> backFaceCutoutMipped = bool);
			handleBooleanDirective(key, value, "backFace.translucent", bool -> backFaceTranslucent = bool);
			handleBooleanDirective(key, value, "rain.depth", bool -> rainDepth = bool);
			handleBooleanDirective(key, value, "beacon.beam.depth", bool -> beaconBeamDepth = bool);
			handleBooleanDirective(key, value, "separateAo", bool -> separateAo = bool);
			handleBooleanDirective(key, value, "frustum.culling", bool -> frustumCulling = bool);

			// Defining "sliders" multiple times in the properties file will only result in
			// the last definition being used, should be tested if behavior matches OptiFine
			handleWhitespacedListDirective(key, value, "sliders", sliders -> sliderOptions = sliders);
			handlePrefixedWhitespacedListDirective("profile.", key, value, profiles::put);
			handleWhitespacedListDirective(key, value, "screen", options -> mainScreenOptions = options);
			handlePrefixedWhitespacedListDirective("screen.", key, value, subScreenOptions::put);
			handleIntDirective(key, value, "screen.columns", columns -> mainScreenColumnCount = columns);
			handleAffixedIntDirective("screen.", ".columns", key, value, subScreenColumnCount::put);

			// TODO: Min optifine versions, custom textures
			// TODO: Custom uniforms

			handlePassDirective("scale.", key, value, pass -> {
				float scale;

				try {
					scale = Float.parseFloat(value);
				} catch (NumberFormatException e) {
					Iris.logger.error("Unable to parse scale directive for " + pass + ": " + value, e);
					return;
				}

				viewportScaleOverrides.put(pass, scale);
			});

			handlePassDirective("alphaTest.", key, value, pass -> {
				if ("off".equals(value)) {
					alphaTestOverrides.put(pass, new AlphaTestOverride.Off());
					return;
				}

				String[] parts = value.split(" ");

				if (parts.length > 2) {
					Iris.logger.warn("Weird alpha test directive for " + pass + " contains more parts than we expected: " + value);
				} else if (parts.length < 2) {
					Iris.logger.error("Invalid alpha test directive for " + pass + ": " + value);
					return;
				}

				Optional<AlphaTestFunction> function = AlphaTestFunction.fromString(parts[0]);

				if (!function.isPresent()) {
					Iris.logger.error("Unable to parse alpha test directive for " + pass + ", unknown alpha test function " + parts[0] + ": " + value);
					return;
				}

				float reference;

				try {
					reference = Float.parseFloat(parts[1]);
				} catch (NumberFormatException e) {
					Iris.logger.error("Unable to parse alpha test directive for " + pass + ": " + value, e);
					return;
				}

				alphaTestOverrides.put(pass, new AlphaTestOverride(function.get(), reference));
			});

			handlePassDirective("blend.", key, value, pass -> {
				if (pass.contains(".")) {
					// TODO: Support per-buffer blending directives (glBlendFuncSeparateI)
					Iris.logger.warn("Per-buffer pass blending directives are not supported, ignoring blend directive for " + key);
					return;
				}

				if (!"off".equals(value)) {
					// TODO: Support custom blending modes
					Iris.logger.warn("Custom blending mode directives are not supported, ignoring blend directive for " + key);
					return;
				}

				blendDisabled.add(pass);
			});

			// TODO: Buffer flip, size directives
			// TODO: Conditional program enabling directives
		});
	}

	private static void handleBooleanDirective(String key, String value, String expectedKey, Consumer<OptionalBoolean> handler) {
		if (!expectedKey.equals(key)) {
			return;
		}

		if ("true".equals(value)) {
			handler.accept(OptionalBoolean.TRUE);
		} else if ("false".equals(value)) {
			handler.accept(OptionalBoolean.FALSE);
		} else {
			Iris.logger.warn("Unexpected value for boolean key " + key + " in shaders.properties: got " + value + ", but expected either true or false");
		}
	}

	private static void handleIntDirective(String key, String value, String expectedKey, Consumer<Integer> handler) {
		if (!expectedKey.equals(key)) {
			return;
		}

		try {
			int result = Integer.parseInt(value);

			handler.accept(result);
		} catch (NumberFormatException nex) {
			Iris.logger.warn("Unexpected value for integer key " + key + " in shaders.properties: got " + value + ", but expected an integer");
		}
	}

	private static void handleAffixedIntDirective(String prefix, String suffix, String key, String value, BiConsumer<String, Integer> handler) {
		if (key.startsWith(prefix) && key.endsWith(suffix)) {
			int substrBegin = prefix.length();
			int substrEnd = key.length() - suffix.length();

			if (substrEnd <= substrBegin) {
				return;
			}

			String affixStrippedKey = key.substring(substrBegin, substrEnd);

			try {
				int result = Integer.parseInt(value);

				handler.accept(affixStrippedKey, result);
			} catch (NumberFormatException nex) {
				Iris.logger.warn("Unexpected value for integer key " + key + " in shaders.properties: got " + value + ", but expected an integer");
			}
		}
	}

	private static void handlePassDirective(String prefix, String key, String value, Consumer<String> handler) {
		if (key.startsWith(prefix)) {
			String pass = key.substring(prefix.length());

			handler.accept(pass);
		}
	}

	private static void handleWhitespacedListDirective(String key, String value, String expectedKey, Consumer<List<String>> handler) {
		if (!expectedKey.equals(key)) {
			return;
		}

		String[] elements = value.split(" +");

		handler.accept(Arrays.asList(elements));
	}

	private static void handlePrefixedWhitespacedListDirective(String prefix, String key, String value, BiConsumer<String, List<String>> handler) {
		if (key.startsWith(prefix)) {
			String prefixStrippedKey = key.substring(prefix.length());
			String[] elements = value.split(" +");

			handler.accept(prefixStrippedKey, Arrays.asList(elements));
		}
	}

	public static ShaderProperties empty() {
		return new ShaderProperties();
	}

	public boolean areCloudsEnabled() {
		return enableClouds;
	}

	public OptionalBoolean getOldHandLight() {
		return oldHandLight;
	}

	public OptionalBoolean getDynamicHandLight() {
		return dynamicHandLight;
	}

	public OptionalBoolean getOldLighting() {
		return oldLighting;
	}

	public OptionalBoolean getShadowTerrain() {
		return shadowTerrain;
	}

	public OptionalBoolean getShadowTranslucent() {
		return shadowTranslucent;
	}

	public OptionalBoolean getShadowEntities() {
		return shadowEntities;
	}

	public OptionalBoolean getShadowBlockEntities() {
		return shadowBlockEntities;
	}

	public OptionalBoolean getUnderwaterOverlay() {
		return underwaterOverlay;
	}

	public OptionalBoolean getSun() {
		return sun;
	}

	public OptionalBoolean getMoon() {
		return moon;
	}

	public OptionalBoolean getVignette() {
		return vignette;
	}

	public OptionalBoolean getBackFaceSolid() {
		return backFaceSolid;
	}

	public OptionalBoolean getBackFaceCutout() {
		return backFaceCutout;
	}

	public OptionalBoolean getBackFaceCutoutMipped() {
		return backFaceCutoutMipped;
	}

	public OptionalBoolean getBackFaceTranslucent() {
		return backFaceTranslucent;
	}

	public OptionalBoolean getRainDepth() {
		return rainDepth;
	}

	public OptionalBoolean getBeaconBeamDepth() {
		return beaconBeamDepth;
	}

	public OptionalBoolean getSeparateAo() {
		return separateAo;
	}

	public OptionalBoolean getFrustumCulling() {
		return frustumCulling;
	}

	public Object2ObjectMap<String, AlphaTestOverride> getAlphaTestOverrides() {
		return alphaTestOverrides;
	}

	public Object2FloatMap<String> getViewportScaleOverrides() {
		return viewportScaleOverrides;
	}

	public ObjectSet<String> getBlendDisabled() {
		return blendDisabled;
	}

	public Optional<String> getNoiseTexturePath() {
		return Optional.ofNullable(noiseTexturePath);
	}

	public List<String> getSliderOptions() {
		return sliderOptions;
	}

	public Map<String, List<String>> getProfiles() {
		return profiles;
	}

	public List<String> getMainScreenOptions() {
		return mainScreenOptions;
	}

	public Map<String, List<String>> getSubScreenOptions() {
		return subScreenOptions;
	}

	public Optional<Integer> getMainScreenColumnCount() {
		return Optional.ofNullable(mainScreenColumnCount);
	}

	public Map<String, Integer> getSubScreenColumnCount() {
		return subScreenColumnCount;
	}
}
