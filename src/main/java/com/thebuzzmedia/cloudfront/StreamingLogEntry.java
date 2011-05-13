package com.thebuzzmedia.cloudfront;

import java.util.HashMap;
import java.util.Map;

public class StreamingLogEntry extends AbstractLogEntry {
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