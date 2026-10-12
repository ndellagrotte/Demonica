package net.coderbot.iris.shaderpack;

import net.coderbot.iris.features.FeatureFlags;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Thrown by {@link ShaderPack}'s constructor when {@code iris.features.required} names a feature this mod (or this
 * PC) does not support.
 *
 * <p>Demonica: upstream Iris (1.11.4, {@code ShaderPack}) shows {@code FeatureMissingErrorScreen} and calls
 * {@code setShadersEnabledAndApply(false)} from inside the constructor, which runs {@code Iris.reload()} while the
 * outer load is still building the pack: for a zip pack {@code destroyEverything} closes the zip file system under
 * the constructor ({@code ClosedFileSystemException}, a second "Failed to load shader pack" line), for a folder pack
 * the outer load finishes and installs the pack with {@code enableShaders=false}. Demonica leaves the constructor
 * with this exception instead; {@code Iris.loadExternalShaderpack} shows the screen, disables shaders in the config
 * and ends the load (docs/IRIS_PORTING_PLAN.md, section 11 item 7).
 */
public class MissingFeaturesException extends IllegalStateException {
	private final List<FeatureFlags> invalidFlags;

	public MissingFeaturesException(List<FeatureFlags> invalidFlags) {
		// No I18n here: the message is for the log, and the lang table is not loaded in unit tests.
		super("The shader pack requires unsupported features: "
				+ invalidFlags.stream().map(FeatureFlags::getHumanReadableName).collect(Collectors.joining(", ")));
		this.invalidFlags = Collections.unmodifiableList(invalidFlags);
	}

	/** The unsupported required flags, in the order the pack declared them. */
	public List<FeatureFlags> getInvalidFlags() {
		return invalidFlags;
	}

	/** Who lacks the features: Iris, the PC, or both (the {@code iris.unsupported.*} lang text). */
	public String getInvalidStatus() {
		return FeatureFlags.getInvalidStatus(invalidFlags);
	}

	/** The human-readable flag names, comma separated, for the screen and the chat line. */
	public String getFlagList() {
		return invalidFlags.stream().map(FeatureFlags::getHumanReadableName).collect(Collectors.joining(", "));
	}
}
