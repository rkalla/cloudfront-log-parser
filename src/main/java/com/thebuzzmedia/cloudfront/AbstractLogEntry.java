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