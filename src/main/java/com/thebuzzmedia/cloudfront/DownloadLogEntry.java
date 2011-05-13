package com.thebuzzmedia.cloudfront;

import java.util.HashMap;
import java.util.Map;

public class DownloadLogEntry extends AbstractLogEntry {
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