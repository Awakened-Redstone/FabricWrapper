/*
 * Copyright (c) 2021-2021  comp500
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH
 * THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.jigsawlabs.fabricwrapper.jumploader.launch.serviceloading;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jigsawlabs.fabricwrapper.jumploader.launch.PreLaunchDispatcher;
import org.jigsawlabs.fabricwrapper.jumploader.launch.ReflectionUtil;

import java.nio.file.spi.FileSystemProvider;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public class FileSystemProviderAppender implements PreLaunchDispatcher.Handler {
	private final Logger LOGGER = LogManager.getLogger();

	private void loadProvidersFromClassLoader(ClassLoader classLoader, List<FileSystemProvider> list) {
		ServiceLoader<FileSystemProvider> loader = ServiceLoader.load(FileSystemProvider.class, classLoader);

		for (FileSystemProvider provider: loader) {
			String scheme = provider.getScheme();
			if (!scheme.equalsIgnoreCase("file")) {
				if (list.stream().noneMatch(p -> p.getScheme().equalsIgnoreCase(scheme))) {
					list.add(provider);
				}
			}
		}
	}

	@Override
	public void handlePreLaunch(ClassLoader loadingClassloader) {
		// Ensure the existing providers are loaded first
		FileSystemProvider.installedProviders();

		try {
			final Object lock = ReflectionUtil.reflectStaticField(FileSystemProvider.class, "lock");
			//noinspection SynchronizationOnLocalVariableOrMethodParameter
			synchronized (lock) {
				// Load providers from the Jumploader classloader, and add them to the FileSystemProvider list
				ReflectionUtil.<List<FileSystemProvider>>transformStaticField(FileSystemProvider.class, "installedProviders", existingProviders -> {
					List<FileSystemProvider> newList = new ArrayList<>(existingProviders);
					AccessController.doPrivileged((PrivilegedAction<Void>) () -> {loadProvidersFromClassLoader(loadingClassloader, newList); return null;});
					return newList;
				});
			}
		} catch (Throwable e) {
			LOGGER.warn("Failed to fix FileSystemProvider loading, jar-in-jar may not work", e);
		}
	}
}
