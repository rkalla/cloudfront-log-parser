/**   
 * Copyright 2011 The Buzz Media, LLC
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.thebuzzmedia.cloudfront;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;

import org.junit.Before;
import org.junit.Test;

public class LogParserTest {
	static final LogParser PARSER = new LogParser();

	static int lineNumber = 0;

	@Before
	public void reset() {
		lineNumber = 0;
	}

	@Test
	public void test100() throws IOException {
		// Setup JDK parsing.
		final BufferedReader reader = new BufferedReader(
				new InputStreamReader(new GZIPInputStream(LogParserTest.class
						.getResourceAsStream("samples/example-100.gz"))));

		// Burn the first two directives lines
		reader.readLine();
		reader.readLine();

		// Create comparison-callback
		ILogParserCallback callback = new ILogParserCallback() {
			public void logEntryParsed(ILogEntry entry) {
				try {
					// JDK-parse the line
					String[] fieldValues = reader.readLine().split("\t");

					/*
					 * TRICK: we happen to know the format of the test docs; all
					 * fields have values except the last one, so we hard-code
					 * to skip checking it.
					 */
					for (int i = 0; i < fieldValues.length - 1; i++) {
						assertEquals(fieldValues[i],
								new String(entry.getFieldValue(i)));
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};

		InputStream inputStream = LogParserTest.class
				.getResourceAsStream("samples/example-100.gz");

		// Start test
		PARSER.parse(inputStream, callback);

		reader.close();
		inputStream.close();
	}

	@Test
	public void test100k() throws IOException {
		// Setup JDK parsing.
		final BufferedReader reader = new BufferedReader(new InputStreamReader(
				new GZIPInputStream(LogParserTest.class
						.getResourceAsStream("samples/example-100k.gz"))));

		// Burn the first two directives lines
		reader.readLine();
		reader.readLine();

		// Create comparison-callback
		ILogParserCallback callback = new ILogParserCallback() {
			public void logEntryParsed(ILogEntry entry) {
				try {
					// JDK-parse the line
					String[] fieldValues = reader.readLine().split("\t");

					/*
					 * TRICK: we happen to know the format of the test docs; all
					 * fields have values except the last one, so we hard-code
					 * to skip checking it.
					 */
					for (int i = 0; i < fieldValues.length - 1; i++) {
						assertEquals(fieldValues[i],
								new String(entry.getFieldValue(i)));
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};

		InputStream inputStream = LogParserTest.class
				.getResourceAsStream("samples/example-100k.gz");

		// Start test
		PARSER.parse(inputStream, callback);

		reader.close();
		inputStream.close();
	}

	@Test
	public void testRealSamples() throws FileNotFoundException, IOException {
		File[] files = new File(
				"src/test/java/com/thebuzzmedia/cloudfront/realsamples")
				.listFiles(new FileFilter() {
					public boolean accept(File pathname) {
						return pathname.getName().endsWith(".gz");
					}
				});

		if (files == null)
			throw new RuntimeException(
					"Unable to find any files in the src/test/java/.../realsamples package.");

		System.out.println("Found " + files.length + " log files...");

		for (int j = 0; j < files.length; j++) {
			File file = files[j];
			System.out.println("Processing: " + file.getName());

			// Setup JDK parsing.
			final BufferedReader reader = new BufferedReader(
					new InputStreamReader(new GZIPInputStream(
							new FileInputStream(file))));

			// Burn the first two directives lines
			reader.readLine();
			reader.readLine();

			// Create comparison-callback
			ILogParserCallback callback = new ILogParserCallback() {
				public void logEntryParsed(ILogEntry entry) {
					try {
						// JDK-parse the line
						String[] fieldValues = reader.readLine().split("\t");

						/*
						 * TRICK: we happen to know the format of the test docs;
						 * all fields have values except the last one, so we
						 * hard-code to skip checking it.
						 */
						for (int i = 0; i < fieldValues.length - 1; i++) {
							char[] value = entry.getFieldValue(i);

							// Null/empty '-' value, compare it differently
							if (value == null || value.length == 0) {
								// value is null when field was empty char: '-'
								assertEquals(1, fieldValues[i].length());
								assertEquals('-', fieldValues[i].charAt(0));
							} else {
								assertEquals(fieldValues[i],
										new String(entry.getFieldValue(i)));
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			};

			FileInputStream inputStream = new FileInputStream(file);

			// Start test
			PARSER.parse(inputStream, callback);

			reader.close();
			inputStream.close();
		}
	}
}