/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

package net.fabricmc.loom.configuration.providers.minecraft;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import net.fabricmc.loom.configuration.ConfigContext;
import net.fabricmc.loom.configuration.decompile.DecompileConfiguration;
import net.fabricmc.loom.configuration.decompile.SingleJarDecompileConfiguration;
import net.fabricmc.loom.configuration.decompile.SplitDecompileConfiguration;
import net.fabricmc.loom.configuration.processors.MinecraftJarProcessorManager;
import net.fabricmc.loom.configuration.providers.forge.minecraft.ForgeMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.IntermediaryMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.MappedMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.NamedMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.ProcessedNamedMinecraftProvider;
import net.fabricmc.loom.configuration.providers.minecraft.mapped.SrgMinecraftProvider;

public enum MinecraftJarConfiguration {
	MERGED(
		ForgeMinecraftProvider::createMerged,
		IntermediaryMinecraftProvider.MergedImpl::new,
		NamedMinecraftProvider.MergedImpl::new,
		SrgMinecraftProvider.MergedImpl::new,
		ProcessedNamedMinecraftProvider.MergedImpl::new,
		SingleJarDecompileConfiguration::new,
		List.of("client", "server")
	),
	SERVER_ONLY(
		ForgeMinecraftProvider::createServerOnly,
		IntermediaryMinecraftProvider.SingleJarImpl::server,
		NamedMinecraftProvider.SingleJarImpl::server,
		SrgMinecraftProvider.SingleJarImpl::server,
		ProcessedNamedMinecraftProvider.SingleJarImpl::server,
		SingleJarDecompileConfiguration::new,
		List.of("server")
	),
	CLIENT_ONLY(
		ForgeMinecraftProvider::createClientOnly,
		IntermediaryMinecraftProvider.SingleJarImpl::client,
		NamedMinecraftProvider.SingleJarImpl::client,
		SrgMinecraftProvider.SingleJarImpl::client,
		ProcessedNamedMinecraftProvider.SingleJarImpl::client,
		SingleJarDecompileConfiguration::new,
		List.of("client")
	),
	SPLIT(
		SplitMinecraftProvider::new,
		IntermediaryMinecraftProvider.SplitImpl::new,
		NamedMinecraftProvider.SplitImpl::new,
		SrgMinecraftProvider.SplitImpl::new,
		ProcessedNamedMinecraftProvider.SplitImpl::new,
		SplitDecompileConfiguration::new,
		List.of("client", "server")
	);

	private final Function<ConfigContext, MinecraftProvider> minecraftProviderFunction;
	private final BiFunction<ConfigContext, MinecraftProvider, IntermediaryMinecraftProvider<?>> intermediaryMinecraftProviderBiFunction;
	private final BiFunction<ConfigContext, MinecraftProvider, NamedMinecraftProvider<?>> namedMinecraftProviderBiFunction;
	private final BiFunction<ConfigContext, MinecraftProvider, SrgMinecraftProvider<?>> srgMinecraftProviderBiFunction;
	private final BiFunction<NamedMinecraftProvider<?>, MinecraftJarProcessorManager, ProcessedNamedMinecraftProvider<?, ?>> processedNamedMinecraftProviderBiFunction;
	private final BiFunction<ConfigContext, MappedMinecraftProvider, DecompileConfiguration<?>> decompileConfigurationBiFunction;
	private final List<String> supportedEnvironments;

	@SuppressWarnings("unchecked") // Just a bit of a generic mess :)
	<M extends MinecraftProvider, P extends NamedMinecraftProvider<M>, Q extends MappedMinecraftProvider> MinecraftJarConfiguration(
			Function<ConfigContext, M> minecraftProviderFunction,
			BiFunction<ConfigContext, M, IntermediaryMinecraftProvider<M>> intermediaryMinecraftProviderBiFunction,
			BiFunction<ConfigContext, M, P> namedMinecraftProviderBiFunction,
			BiFunction<ConfigContext, M, SrgMinecraftProvider<M>> srgMinecraftProviderBiFunction,
			BiFunction<P, MinecraftJarProcessorManager, ProcessedNamedMinecraftProvider<M, P>> processedNamedMinecraftProviderBiFunction,
			BiFunction<ConfigContext, Q, DecompileConfiguration<?>> decompileConfigurationBiFunction,
			List<String> supportedEnvironments
	) {
		this.minecraftProviderFunction = (Function<ConfigContext, MinecraftProvider>) minecraftProviderFunction;
		this.intermediaryMinecraftProviderBiFunction = (BiFunction<ConfigContext, MinecraftProvider, IntermediaryMinecraftProvider<?>>) (Object) intermediaryMinecraftProviderBiFunction;
		this.namedMinecraftProviderBiFunction = (BiFunction<ConfigContext, MinecraftProvider, NamedMinecraftProvider<?>>) namedMinecraftProviderBiFunction;
		this.srgMinecraftProviderBiFunction = (BiFunction<ConfigContext, MinecraftProvider, SrgMinecraftProvider<?>>) (Object) srgMinecraftProviderBiFunction;
		this.processedNamedMinecraftProviderBiFunction = (BiFunction<NamedMinecraftProvider<?>, MinecraftJarProcessorManager, ProcessedNamedMinecraftProvider<?, ?>>) (Object) processedNamedMinecraftProviderBiFunction;
		this.decompileConfigurationBiFunction = (BiFunction<ConfigContext, MappedMinecraftProvider, DecompileConfiguration<?>>) decompileConfigurationBiFunction;
		this.supportedEnvironments = supportedEnvironments;
	}

	public Function<ConfigContext, MinecraftProvider> getMinecraftProviderFunction() {
		return minecraftProviderFunction;
	}

	public BiFunction<ConfigContext, MinecraftProvider, IntermediaryMinecraftProvider<?>> getIntermediaryMinecraftProviderBiFunction() {
		return intermediaryMinecraftProviderBiFunction;
	}

	public BiFunction<ConfigContext, MinecraftProvider, NamedMinecraftProvider<?>> getNamedMinecraftProviderBiFunction() {
		return namedMinecraftProviderBiFunction;
	}

	public BiFunction<NamedMinecraftProvider<?>, MinecraftJarProcessorManager, ProcessedNamedMinecraftProvider<?, ?>> getProcessedNamedMinecraftProviderBiFunction() {
		return processedNamedMinecraftProviderBiFunction;
	}

	public BiFunction<ConfigContext, MappedMinecraftProvider, DecompileConfiguration<?>> getDecompileConfigurationBiFunction() {
		return decompileConfigurationBiFunction;
	}

	public BiFunction<ConfigContext, MinecraftProvider, SrgMinecraftProvider<?>> getSrgMinecraftProviderBiFunction() {
		return srgMinecraftProviderBiFunction;
	}

	public List<String> getSupportedEnvironments() {
		return supportedEnvironments;
	}
}
