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

public class StreamingLogEntry extends AbstractLogEntry {
	// TODO: Define every one of these as public static final Strings that can
	// be used to identify the col name for callers.
	protected static final String[] FIELD_NAMES = { "date", "time",
			"x-edge-location", "c-ip", "x-event", "sc-bytes", "x-cf-status",
			"x-cf-client-id", "cs-uri-stem", "cs-uri-query", "c-referrer",
			"x-page-url", "c-user-agent", "x-sname", "x-sname-query",
			"x-file-ext", "x-sid" };

	protected static final Map<String, Integer> FIELD_INDEX_MAP = new HashMap<String, Integer>(
			MAX_STREAMING_FIELDS * 5);

	static {
		for (int i = 0; i < FIELD_NAMES.length; i++)
			FIELD_INDEX_MAP.put(FIELD_NAMES[i], i);
	}

	public StreamingLogEntry() throws IllegalArgumentException {
		super(Type.STREAMING, new char[MAX_STREAMING_FIELDS][]);
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