package com.thebuzzmedia.cloudfront;

public interface ILogEntry {
	public static final int MAX_DOWNLOAD_FIELDS = 13;
	public static final int MAX_STREAMING_FIELDS = 17;

	public static final char EMPTY_VALUE_FLAG = '-';

	public enum Type {
		DOWNLOAD, STREAMING;
	}

	public void reset();

	public Type getType();

	public int getFieldCount();

	public String[] getFieldNames();

	public int getFieldIndex(String fieldName);

	public char[] getFieldValue(int fieldIndex) throws IllegalArgumentException;

	public char[] getFieldValue(String fieldName)
			throws IllegalArgumentException;

	public char[][] getFieldValues();

	public void setFieldValue(int fieldIndex, char[] value)
			throws IllegalArgumentException;
}