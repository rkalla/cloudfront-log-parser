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

public interface ILogEntry {
	public static final int MAX_DOWNLOAD_FIELDS = 13;
	public static final int MAX_STREAMING_FIELDS = 17;
	
	public static final int INVALID_INDEX = -1;

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

	public char[] getFieldValue(String fieldName);

	public char[][] getFieldValues();

	public void setFieldValue(int fieldIndex, char[] value)
			throws IllegalArgumentException;
}