package com.thebuzzmedia.cloudfront;

public abstract class AbstractLogEntry implements ILogEntry {
	protected Type type;
	protected char[][] values;

	public AbstractLogEntry(Type type, char[][] values)
			throws IllegalArgumentException {
		if (type == null)
			throw new IllegalArgumentException("type cannot be null");
		if (values == null || values.length == 0)
			throw new IllegalArgumentException(
					"values cannot be null or empty and must represent an array of one char[] instance for each field value.");

		this.type = type;
		this.values = values;
	}

	public String toString() {
		StringBuilder params = new StringBuilder();

		for (int i = 0; i < values.length; i++) {
			char[] v = values[i];

			if (v != null)
				params.append(v);

			if (i < values.length - 1)
				params.append(',');
		}

		return this.getClass().getName() + "@" + hashCode() + "[type=" + type
				+ ", fieldCount=" + values.length + ", values={"
				+ params.toString() + "}]";
	}

	public void reset() {
		for (int i = 0; i < values.length; i++)
			values[i] = null;
	}

	public Type getType() {
		return type;
	}

	public int getFieldCount() {
		return values.length;
	}

	public char[] getFieldValue(int fieldIndex) throws IllegalArgumentException {
		if (fieldIndex < 0 || fieldIndex >= values.length)
			throw new IllegalArgumentException("fieldIndex [" + fieldIndex
					+ "] must be >= 0 and < getFieldCount() [" + values.length
					+ "]");

		return values[fieldIndex];
	}

	public char[][] getFieldValues() {
		return values;
	}

	public void setFieldValue(int fieldIndex, char[] value)
			throws IllegalArgumentException {
		if (fieldIndex < 0 || fieldIndex >= values.length)
			throw new IllegalArgumentException("fieldIndex [" + fieldIndex
					+ "] must be >= 0 and < getFieldCount() [" + values.length
					+ "]");

		// Convert all unusable or empty values to null
		if (value != null
				&& (value.length == 0 || (value.length == 1 && value[0] == EMPTY_VALUE_FLAG)))
			value = null;

		values[fieldIndex] = value;
	}
}