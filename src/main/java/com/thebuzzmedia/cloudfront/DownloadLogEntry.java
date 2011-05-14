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

import java.util.HashMap;
import java.util.Map;

public class DownloadLogEntry extends AbstractLogEntry {
	// TODO: Define every one of these as public static final Strings that can
	// be used to identify the col name for callers.
	protected static final String[] FIELD_NAMES = { "date", "time",
			"x-edge-location", "sc-bytes", "c-ip", "cs-method", "cs(Host)",
			"cs-uri-stem", "sc-status", "cs(Referer)", "cs(User-Agent)",
			"cs-uri-query" };

	protected static final Map<String, Integer> FIELD_INDEX_MAP = new HashMap<String, Integer>(
			MAX_DOWNLOAD_FIELDS * 5);

	static {
		for (int i = 0; i < FIELD_NAMES.length; i++)
			FIELD_INDEX_MAP.put(FIELD_NAMES[i], i);
	}

	public DownloadLogEntry() throws IllegalArgumentException {
		super(Type.DOWNLOAD, new char[MAX_DOWNLOAD_FIELDS][]);
	}

	public String[] getFieldNames() {
		return FIELD_NAMES;
	}

	public int getFieldIndex(String fieldName) {
		Integer index = FIELD_INDEX_MAP.get(fieldName);
		return (index == null ? INVALID_INDEX : index.intValue());
	}

	public char[] getFieldValue(String fieldName) {
		Integer index = FIELD_INDEX_MAP.get(fieldName);
		return (index == null ? null : values[index.intValue()]);
	}
}