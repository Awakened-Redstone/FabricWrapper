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

import java.net.URL;
import java.net.URLStreamHandler;
import java.util.Hashtable;

public class JimfsURLHandlerAppender implements PreLaunchDispatcher.Handler {
	private final Logger LOGGER = LogManager.getLogger();

	@Override
	public void handlePreLaunch(ClassLoader loadingClassloader) {
		// Jimfs requires some funky hacks to load under a custom classloader, Java protocol handlers don't handle custom classloaders very well
		// See also FileSystemProviderAppender
		try {
			Class<?> handler = Class.forName("com.google.common.jimfs.Handler", true, loadingClassloader);
			// Add the jimfs handler to the URL handlers field, because Class.forName by default uses the classloader that loaded the calling class (in this case the system classloader, so we have to do it manually)
			Hashtable<String, URLStreamHandler> handlers = ReflectionUtil.reflectStaticField(URL.class, "handlers");
			handlers.putIfAbsent("jimfs", (URLStreamHandler) handler.getDeclaredConstructor().newInstance());
		} catch (ClassNotFoundException ignored) {
			// Ignore class not found - jimfs presumably isn't in the classpath
		} catch (Throwable e) {
			LOGGER.warn("Failed to fix jimfs loading, jar-in-jar may not work", e);
		}
	}
}
