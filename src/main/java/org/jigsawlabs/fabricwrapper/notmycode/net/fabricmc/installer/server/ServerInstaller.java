/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jigsawlabs.fabricwrapper.notmycode.net.fabricmc.installer.server;

import mjson.Json;
import org.jigsawlabs.fabricwrapper.FabricWrapper;
import org.jigsawlabs.fabricwrapper.notmycode.net.fabricmc.installer.util.*;
import org.jigsawlabs.fabricwrapper.notmycode.net.fabricmc.loader.util.UrlUtil;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ServerInstaller {
	private static final String servicesDir = "META-INF/services/";

	public static void install(File dir, String loaderVersion, String gameVersion, InstallerProgress progress) throws IOException {
		progress.updateProgress(new MessageFormat(Utils.BUNDLE.getString("progress.installing.server")).format(new Object[]{String.format("%s(%s)", loaderVersion, gameVersion)}));
		File libsDir = new File(".", ".cache" + File.separator + "fabric-wrapper" + File.separator + "libraries");
		if (!libsDir.exists()) {
			if (!libsDir.mkdirs()) {
				throw new IOException("Could not create " + libsDir.getAbsolutePath() + "!");
			}
		}
		if(!dir.exists()){
			if (!dir.mkdirs()) {
				throw new IOException("Could not create " + dir.getAbsolutePath() + "!");
			}
		}

		progress.updateProgress(Utils.BUNDLE.getString("progress.download.libraries"));


		URL profileUrl = new URL(Reference.getMetaServerEndpoint(String.format("v2/versions/loader/%s/%s/server/json", gameVersion, loaderVersion)));
		Json json = Json.read(Utils.readTextFile(profileUrl));

		List<File> libraryFiles = new ArrayList<>();

		for (Json libraryJson : json.at("libraries").asJsonList()) {
			Library library = new Library(libraryJson);

			progress.updateProgress(new MessageFormat(Utils.BUNDLE.getString("progress.download.library.entry")).format(new Object[]{library.name}));
			File libraryFile = new File(libsDir, library.getFileName());
			Utils.downloadFile(new URL(library.getURL()), libraryFile.toPath());
			libraryFiles.add(libraryFile);
		}

		progress.updateProgress(Utils.BUNDLE.getString("progress.generating.launch.jar"));

		//Generate launchJar to be used as fallback if needed.
		File launchJar = new File(dir, "fabric-server-launch.jar");
		String mainClass = json.at("mainClass").asString();
		makeLaunchJar(launchJar, mainClass, libraryFiles);

		progress.updateProgress(new MessageFormat(Utils.BUNDLE.getString("progress.done.loader")).format(new Object[]{launchJar.getName()}));
		FabricWrapper.loadUrls.addAll(libraryFiles.stream().map(UrlUtil::asUrl).collect(Collectors.toList()));
	}

	private static void makeLaunchJar(File file, String mainclass, List<File> libraryFiles) throws IOException {
		if (file.exists()) {
			if (!file.delete()) {
				throw new IOException("Could not delete file: " + file.getAbsolutePath());
			}
		}

		FileOutputStream outputStream = new FileOutputStream(file);
		ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);

		Set<String> addedEntries = new HashSet<>();

		{
			addedEntries.add("META-INF/MANIFEST.MF");
			zipOutputStream.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));

			Manifest manifest = new Manifest();
			manifest.getMainAttributes().put(new Attributes.Name("Manifest-Version"), "1.0");
			manifest.getMainAttributes().put(new Attributes.Name("Main-Class"), "net.fabricmc.loader.launch.server.FabricServerLauncher");
			manifest.write(zipOutputStream);

			zipOutputStream.closeEntry();

			addedEntries.add("fabric-server-launch.properties");
			zipOutputStream.putNextEntry(new ZipEntry("fabric-server-launch.properties"));
			zipOutputStream.write(("launch.mainClass=" + mainclass + "\n").getBytes(StandardCharsets.UTF_8));
			zipOutputStream.closeEntry();

			Map<String, Set<String>> services = new HashMap<>();
			byte[] buffer = new byte[32768];

			for (File f : libraryFiles) {

				// read service definitions (merging them), copy other files
				try (
						FileInputStream is = new FileInputStream(f);
						JarInputStream jis = new JarInputStream(is)
				) {
					JarEntry entry;
					while ((entry = jis.getNextJarEntry()) != null) {
						if (entry.isDirectory()) continue;

						String name = entry.getName();

						if (name.startsWith(servicesDir) && name.indexOf('/', servicesDir.length()) < 0) { // service definition file
							parseServiceDefinition(name, jis, services);
						} else if (!addedEntries.add(name)) {
							System.out.printf("duplicate file: %s%n", name);
						} else {
							JarEntry newEntry = new JarEntry(name);
							zipOutputStream.putNextEntry(newEntry);

							int r;
							while ((r = jis.read(buffer, 0, buffer.length)) >= 0) {
								zipOutputStream.write(buffer, 0, r);
							}

							zipOutputStream.closeEntry();
						}
					}
				}
			}

			// write service definitions
			for (Map.Entry<String, Set<String>> entry : services.entrySet()) {
				JarEntry newEntry = new JarEntry(entry.getKey());
				zipOutputStream.putNextEntry(newEntry);

				writeServiceDefinition(entry.getValue(), zipOutputStream);

				zipOutputStream.closeEntry();
			}
		}

		zipOutputStream.close();
		outputStream.close();
	}

	private static void parseServiceDefinition(String name, InputStream rawIs, Map<String, Set<String>> services) throws IOException {
		Collection<String> out = null;
		BufferedReader reader = new BufferedReader(new InputStreamReader(rawIs, StandardCharsets.UTF_8));
		String line;

		while ((line = reader.readLine()) != null) {
			int pos = line.indexOf('#');
			if (pos >= 0) line = line.substring(0, pos);
			line = line.trim();

			if (!line.isEmpty()) {
				if (out == null) out = services.computeIfAbsent(name, ignore -> new LinkedHashSet<>());

				out.add(line);
			}
		}
	}

	private static void writeServiceDefinition(Collection<String> defs, OutputStream os) throws IOException {
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));

		for (String def : defs) {
			writer.write(def);
			writer.write('\n');
		}

		writer.flush();
	}
}
