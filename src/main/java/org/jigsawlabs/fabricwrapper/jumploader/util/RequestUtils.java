/*
 * Copyright (c) 2021  comp500
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

package org.jigsawlabs.fabricwrapper.jumploader.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

public class RequestUtils {
	private RequestUtils() {}

	public static JsonElement getJson(String requestUrl) throws IOException {
		return getJson(new URL(requestUrl));
	}

	public static JsonElement getJson(URL requestUrl) throws IOException {
		URLConnection conn = requestUrl.openConnection();
		conn.setRequestProperty("User-Agent", "FabricWrapper");
		conn.setRequestProperty("Accept", "application/json");

		try (InputStream res = conn.getInputStream(); InputStreamReader isr = new InputStreamReader(res)) {
			JsonParser parser = new JsonParser();
			return parser.parse(isr);
		}
	}

	/*public static int postJsonForResCode(URL requestUrl, JsonElement requestData) throws IOException {
		HttpURLConnection conn = (HttpURLConnection) requestUrl.openConnection();
		conn.setDoOutput(true);
		conn.setRequestProperty("User-Agent", "FabricWrapper");
		conn.setRequestProperty("Content-Type", "application/json");
		conn.setRequestProperty("Accept", "application/json");

		try (OutputStream req = conn.getOutputStream(); OutputStreamWriter writer = new OutputStreamWriter(req)) {
			Gson gson = new Gson();
			gson.toJson(requestData, writer);
		}

		return conn.getResponseCode();
	}*/

	public static String getString(URL requestUrl) throws IOException {
		URLConnection conn = requestUrl.openConnection();
		conn.setRequestProperty("User-Agent", "FabricWrapper");
		conn.setRequestProperty("Accept", "text/plain");

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		try (InputStream res = conn.getInputStream()) {
			int n;
			while ((n = res.read(buffer, 0, 1024)) != -1) {
				baos.write(buffer, 0, n);
			}
		}
		return baos.toString(StandardCharsets.UTF_8.name());
	}

	public static URI resolveMavenPath(URI baseUrl, String mavenPath) {
		String[] mavenPathSplit = mavenPath.split(":");
		if (mavenPathSplit.length != 3) {
			throw new RuntimeException("Invalid maven path: " + mavenPath);
		}
		return baseUrl.resolve(
			String.join("/", mavenPathSplit[0].split("\\.")) + "/" + // Group ID
				mavenPathSplit[1] + "/" + // Artifact ID
				mavenPathSplit[2] + "/" + // Version
				mavenPathSplit[1] + "-" + mavenPathSplit[2] + ".jar"
		);
	}

	private static URL getSha1Url(URL downloadUrl) throws MalformedURLException {
		return new URL(downloadUrl.getProtocol(), downloadUrl.getHost(), downloadUrl.getPort(), downloadUrl.getFile() + ".sha1");
	}

	public static String getSha1Hash(URL downloadUrl) throws IOException {
		return getString(getSha1Url(downloadUrl));
	}
}
