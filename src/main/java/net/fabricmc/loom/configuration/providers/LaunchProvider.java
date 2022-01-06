/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019-2021 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.configuration.providers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.api.plugins.JavaPlugin;

import net.fabricmc.loom.configuration.DependencyProvider;
import net.fabricmc.loom.configuration.RemappedConfigurationEntry;
import net.fabricmc.loom.configuration.launch.LaunchProviderSettings;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.PropertyUtil;

public class LaunchProvider extends DependencyProvider {
	public LaunchProvider(Project project) {
		super(project);
	}

	@Override
	public void provide(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) throws IOException {
		final String nativesPath = getExtension().getFiles().getNativesDirectory(getProject()).getAbsolutePath();

		final LaunchConfig launchConfig = new LaunchConfig()
				.property("fabric.development", "true")
				.property("fabric.remapClasspathFile", getRemapClasspathFile().getAbsolutePath())
				.property("log4j.configurationFile", getAllLog4JConfigFiles())
				.property("log4j2.formatMsgNoLookups", "true")

				.property("client", "java.library.path", nativesPath)
				.property("client", "org.lwjgl.librarypath", nativesPath);

		if (!getExtension().isForge()) {
			launchConfig
					.argument("client", "--assetIndex")
					.argument("client", getExtension().getMinecraftProvider().getVersionInfo().assetIndex().fabricId(getExtension().getMinecraftProvider().minecraftVersion()))
					.argument("client", "--assetsDir")
					.argument("client", new File(getDirectories().getUserCache(), "assets").getAbsolutePath());
		}

		if (getExtension().isForge()) {
			launchConfig
					// Should match YarnNamingService.PATH_TO_MAPPINGS in forge-runtime
					.property("fabric.yarnWithSrg.path", getExtension().getMappingsProvider().tinyMappingsWithSrg.toAbsolutePath().toString())

					.argument("data", "--all")
					.argument("data", "--mod")
					.argument("data", String.join(",", getExtension().getForge().getDataGenMods()))
					.argument("data", "--output")
					.argument("data", getProject().file("src/generated/resources").getAbsolutePath())

					.property("mixin.env.remapRefMap", "true");

			if (PropertyUtil.getAndFinalize(getExtension().getForge().getUseCustomMixin())) {
				launchConfig.property("mixin.forgeloom.inject.mappings.srg-named", getExtension().getMappingsProvider().mixinTinyMappingsWithSrg.toAbsolutePath().toString());
			} else {
				launchConfig.property("net.minecraftforge.gradle.GradleStart.srg.srg-mcp", getExtension().getMappingsProvider().srgToNamedSrg.toAbsolutePath().toString());
			}

			Set<String> mixinConfigs = PropertyUtil.getAndFinalize(getExtension().getForge().getMixinConfigs());

			if (!mixinConfigs.isEmpty()) {
				for (String config : mixinConfigs) {
					launchConfig.argument("-mixin.config");
					launchConfig.argument(config);
				}
			}
		}

		addDependency(Constants.Dependencies.DEV_LAUNCH_INJECTOR + Constants.Dependencies.Versions.DEV_LAUNCH_INJECTOR, Constants.Configurations.LOOM_DEVELOPMENT_DEPENDENCIES);
		addDependency(Constants.Dependencies.TERMINAL_CONSOLE_APPENDER + Constants.Dependencies.Versions.TERMINAL_CONSOLE_APPENDER, Constants.Configurations.LOOM_DEVELOPMENT_DEPENDENCIES);
		addDependency(Constants.Dependencies.JETBRAINS_ANNOTATIONS + Constants.Dependencies.Versions.JETBRAINS_ANNOTATIONS, JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME);

		if (getExtension().isForge()) {
			addDependency(Constants.Dependencies.FORGE_RUNTIME + Constants.Dependencies.Versions.FORGE_RUNTIME, Constants.Configurations.FORGE_EXTRA);
			addDependency(Constants.Dependencies.JAVAX_ANNOTATIONS + Constants.Dependencies.Versions.JAVAX_ANNOTATIONS, JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME);
		}

		for (LaunchProviderSettings settings : getExtension().getLaunchConfigs()) {
			settings.evaluateNow();

			for (String argument : settings.getArguments()) {
				launchConfig.argument(settings.getName(), argument);
			}

			for (Map.Entry<String, String> property : settings.getProperties()) {
				launchConfig.property(settings.getName(), property.getKey(), property.getValue());
			}
		}

		final boolean plainConsole = getProject().getGradle().getStartParameter().getConsoleOutput() == ConsoleOutput.Plain;
		final boolean ansiSupportedIDE = new File(getProject().getRootDir(), ".vscode").exists()
				|| new File(getProject().getRootDir(), ".idea").exists()
				|| (Arrays.stream(getProject().getRootDir().listFiles()).anyMatch(file -> file.getName().endsWith(".iws")));

		//Enable ansi by default for idea and vscode when gradle is not ran with plain console.
		if (ansiSupportedIDE && !plainConsole) {
			launchConfig.property("fabric.log.disableAnsi", "false");
		}

		writeLog4jConfig();
		FileUtils.writeStringToFile(getDirectories().getDevLauncherConfig(), launchConfig.asString(), StandardCharsets.UTF_8);

		postPopulationScheduler.accept(this::writeRemapClassPath);
	}

	private File getLog4jConfigFile() {
		return getDirectories().getDefaultLog4jConfigFile();
	}

	private String getAllLog4JConfigFiles() {
		return getExtension().getLog4jConfigs().getFiles().stream()
				.map(File::getAbsolutePath)
				.collect(Collectors.joining(","));
	}

	private File getRemapClasspathFile() {
		return new File(getDirectories().getDevLauncherConfig().getParentFile(), "remapClasspath.txt");
	}

	private void writeLog4jConfig() {
		try (InputStream is = LaunchProvider.class.getClassLoader().getResourceAsStream("log4j2.fabric.xml")) {
			Files.deleteIfExists(getLog4jConfigFile().toPath());
			Files.copy(is, getLog4jConfigFile().toPath());
		} catch (IOException e) {
			throw new RuntimeException("Failed to generate log4j config", e);
		}
	}

	private void writeRemapClassPath() {
		List<String> inputConfigurations = new ArrayList<>();
		inputConfigurations.add(Constants.Configurations.LOADER_DEPENDENCIES);
		inputConfigurations.addAll(Constants.MOD_COMPILE_ENTRIES.stream().map(RemappedConfigurationEntry::sourceConfiguration).collect(Collectors.toList()));

		List<File> remapClasspath = new ArrayList<>();

		for (String inputConfiguration : inputConfigurations) {
			remapClasspath.addAll(getProject().getConfigurations().getByName(inputConfiguration).getFiles());
		}

		remapClasspath.add(getExtension().getMinecraftMappedProvider().getIntermediaryJar());

		if (getExtension().isForgeAndNotOfficial()) {
			remapClasspath.add(getExtension().getMinecraftMappedProvider().getForgeIntermediaryJar());
		}

		String str = remapClasspath.stream()
				.map(File::getAbsolutePath)
				.collect(Collectors.joining(File.pathSeparator));

		try {
			Files.writeString(getRemapClasspathFile().toPath(), str);
		} catch (IOException e) {
			throw new RuntimeException("Failed to generate remap classpath", e);
		}
	}

	@Override
	public String getTargetConfig() {
		return Constants.Configurations.MINECRAFT_NAMED;
	}

	public static class LaunchConfig {
		private final Map<String, List<String>> values = new HashMap<>();

		public LaunchConfig property(String key, String value) {
			return property("common", key, value);
		}

		public LaunchConfig property(String side, String key, String value) {
			values.computeIfAbsent(side + "Properties", (s -> new ArrayList<>()))
					.add(String.format("%s=%s", key, value));
			return this;
		}

		public LaunchConfig argument(String value) {
			return argument("common", value);
		}

		public LaunchConfig argument(String side, String value) {
			values.computeIfAbsent(side + "Args", (s -> new ArrayList<>()))
					.add(value);
			return this;
		}

		public String asString() {
			StringJoiner stringJoiner = new StringJoiner("\n");

			for (Map.Entry<String, List<String>> entry : values.entrySet()) {
				stringJoiner.add(entry.getKey());

				for (String s : entry.getValue()) {
					stringJoiner.add("\t" + s);
				}
			}

			return stringJoiner.toString();
		}
	}
}
