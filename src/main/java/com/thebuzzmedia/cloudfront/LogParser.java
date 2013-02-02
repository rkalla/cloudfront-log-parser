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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import com.thebuzzmedia.common.IToken;
import com.thebuzzmedia.common.charset.DecodingUtils;
import com.thebuzzmedia.common.lexer.CharArrayTokenizer;
import com.thebuzzmedia.common.lexer.IDelimitedTokenizer;
import com.thebuzzmedia.common.util.ArrayUtils;

public class LogParser {
	public static final String BUFFER_SIZE_PROPERTY_NAME = "cloudfront.logparser.bufferSize";
	public static final String GZIP_BUFFER_SIZE_PROPERTY_NAME = "cloudfront.logparser.gzipBufferSize";

	public static final int BUFFER_SIZE = Integer.getInteger(
			BUFFER_SIZE_PROPERTY_NAME, 32768);

	public static final int GZIP_BUFFER_SIZE = Integer.getInteger(
			GZIP_BUFFER_SIZE_PROPERTY_NAME, 32768);

	public static final byte LF = 10; // \n

	public static final char[] DELIMITERS = { ' ', '\t', '\r', '\n' };

	private static final int MIN_BUFFER_SIZE = 1024;
	private static final int MIN_GZIP_BUFFER_SIZE = 1024;

	private static final char[] FIELDS_DIRECTIVE_PREFIX = { '#', 'F', 'i', 'e',
			'l', 'd', 's', ':' };

	/**
	 * Map containing a collection of field names that belong only to DOWNLOAD
	 * distribution log files or STREAMING distribution log files. In order for
	 * the parser to auto-detect the type of log it is parsing, it uses this map
	 * to do a quick-match of unique field names. Once the log file type is
	 * determined, the parser knows how to parse and store the repsective field
	 * values.
	 */
	private static final Map<String, ILogEntry.Type> LOG_TYPE_DETECTION_MAP = new HashMap<String, ILogEntry.Type>(
			32);

	static {
		// Init system properties
		if (BUFFER_SIZE <= MIN_BUFFER_SIZE)
			throw new RuntimeException(
					"System property '"
							+ BUFFER_SIZE_PROPERTY_NAME
							+ "' is currently set below the min allowed value of "
							+ MIN_GZIP_BUFFER_SIZE
							+ ". You must increase this value for the parser to operate correctly.");

		if (GZIP_BUFFER_SIZE <= MIN_GZIP_BUFFER_SIZE)
			throw new RuntimeException(
					"System property '"
							+ GZIP_BUFFER_SIZE_PROPERTY_NAME
							+ "' is currently set below the min allowed value of "
							+ MIN_GZIP_BUFFER_SIZE
							+ ". You must increase this value for the parser to operate correctly.");

		// Init the detection map with DOWNLOAD-only fields
		LOG_TYPE_DETECTION_MAP.put("cs-method", ILogEntry.Type.DOWNLOAD);
		LOG_TYPE_DETECTION_MAP.put("sc-status", ILogEntry.Type.DOWNLOAD);
		LOG_TYPE_DETECTION_MAP.put("cs(Host)", ILogEntry.Type.DOWNLOAD);
		LOG_TYPE_DETECTION_MAP.put("cs(Referer)", ILogEntry.Type.DOWNLOAD);
		LOG_TYPE_DETECTION_MAP.put("cs(User-Agent)", ILogEntry.Type.DOWNLOAD);
		LOG_TYPE_DETECTION_MAP.put("cs(Cookie)", ILogEntry.Type.DOWNLOAD);
		LOG_TYPE_DETECTION_MAP.put("x-edge-result-type", ILogEntry.Type.DOWNLOAD);
		LOG_TYPE_DETECTION_MAP.put("x-edge-request-id", ILogEntry.Type.DOWNLOAD);

		// Init the detection map with STREAMING-only fields
		LOG_TYPE_DETECTION_MAP.put("x-event", ILogEntry.Type.STREAMING);
		LOG_TYPE_DETECTION_MAP.put("x-cf-status", ILogEntry.Type.STREAMING);
		LOG_TYPE_DETECTION_MAP.put("c-referrer", ILogEntry.Type.STREAMING);
		LOG_TYPE_DETECTION_MAP.put("c-user-agent", ILogEntry.Type.STREAMING);
		LOG_TYPE_DETECTION_MAP.put("x-page-url", ILogEntry.Type.STREAMING);
		LOG_TYPE_DETECTION_MAP.put("x-sname", ILogEntry.Type.STREAMING);
		LOG_TYPE_DETECTION_MAP.put("x-sname-query", ILogEntry.Type.STREAMING);
		LOG_TYPE_DETECTION_MAP.put("x-file-ext", ILogEntry.Type.STREAMING);
		LOG_TYPE_DETECTION_MAP.put("x-sid", ILogEntry.Type.STREAMING);
	}

	private int index;
	private int length;
	private int readCount;
	private byte[] buffer;

	private ILogEntry.Type logType;

	private ILogEntry logEntryWrapper;
	private ILogEntry downloadLogEntryWrapper;
	private ILogEntry streamingLogEntryWrapper;

	private List<String> parsedFieldNames;
	private List<Integer> activeFieldIndices;
	private Set<Integer> skippedFieldPositionSet;
	private IDelimitedTokenizer<char[], char[]> tokenizer;

	public LogParser() {
		buffer = new byte[BUFFER_SIZE];
		tokenizer = new CharArrayTokenizer();

		/*
		 * Have tokenizer re-use the same IToken<char[]> instance when reporting
		 * tokens to us down in the parseLogEntry method. We don't expose the
		 * underlying token outside of this class and we don't store it, so we
		 * can save on memory allocation and CPU time by doing this.
		 */
		tokenizer.setReuseToken(true);

		// Pre-alloc the two wrapper instances this parser will ever use
		downloadLogEntryWrapper = new DownloadLogEntry();
		streamingLogEntryWrapper = new StreamingLogEntry();

		// Pre-size both lists to the max possible size (streaming field count)
		parsedFieldNames = new ArrayList<String>(ILogEntry.MAX_STREAMING_FIELDS);
		activeFieldIndices = new ArrayList<Integer>(
				ILogEntry.MAX_STREAMING_FIELDS);
		skippedFieldPositionSet = new HashSet<Integer>();
	}

	public String toString() {
		return this.getClass().getName() + "@" + hashCode() + "[index=" + index
				+ ", length=" + length + ", bufferSize=" + buffer.length
				+ ", readCount=" + readCount + ", logType=" + logType + "]";
	}

	public void reset() {
		index = 0;
		length = 0;
		readCount = 0;

		logType = null;
		logEntryWrapper = null;

		tokenizer.reset();
		parsedFieldNames.clear();
		activeFieldIndices.clear();
		skippedFieldPositionSet.clear();
	}

	public void parse(InputStream stream, ILogParserCallback callback)
			throws IllegalArgumentException, IOException,
			MalformedContentException, RuntimeException {
		if (stream == null)
			throw new IllegalArgumentException("stream cannot be null");
		if (callback == null)
			throw new IllegalArgumentException("callback cannot be null");

		// Reset parser state
		reset();

		// Prepare GZIP stream for reading.
		GZIPInputStream gzipStream = new GZIPInputStream(stream,
				GZIP_BUFFER_SIZE);

		/*
		 * When reading new bytes from the GZip stream into our internal buffer,
		 * be sure to insert it after any kept data from the previous iteration
		 * (which was moved to the front of the buffer). Also be sure to only
		 * read up to enough data to fill in the remaining space of the buffer
		 * (depending on how much was kept).
		 */
		while ((length = gzipStream.read(buffer, index, buffer.length - index)) != -1) {
			// Keep track of read counts for easier debugging
			readCount++;

			/*
			 * Length has been set to the result of the read() operation above,
			 * but if we kept any bytes from a previous iteration, we adjust
			 * length here to correctly account for them (since index was
			 * pointing at the position right after the last byte if we did).
			 */
			length += index;

			// Now reset the index to point back at the front of the buffer.
			index = 0;

			/*
			 * We want to decode on full-line boundaries because we want to
			 * process log entries on full-line boundaries (easier logic).
			 * 
			 * So first we search from back to front, looking for the last
			 * line-feed (\n) character we can find and then use that as our
			 * end-point that we decode the entire buffer up to.
			 * 
			 * This could mean we are decoding 1 or 1000 lines of content;
			 * whatever fit into our read buffer in the last read op.
			 * 
			 * At the end of this iteration of the loop, we move any bytes that
			 * weren't decoded to the front of the buffer, fill in the rest of
			 * the buffer and decode (again) up to the last line boundary.
			 */
			int lfIndex = ArrayUtils.lastIndexOfNoCheck(LF, buffer, index,
					length);

			if (lfIndex == -1)
				throw new MalformedContentException(
						"Could not find the \\n (LINE FEED) character after scanning "
								+ length
								+ " bytes from the read buffer (read cycle "
								+ readCount
								+ ", buffer size "
								+ buffer.length
								+ " bytes). The log file is likely malformed or a single log entry line is so long it won't fit easily into the current read buffer. Consider making the buffer bigger by adjust the "
								+ BUFFER_SIZE_PROPERTY_NAME
								+ " system property.");

			// Decode the buffer contents up to our LF terminator
			char[] content = DecodingUtils.decode(buffer,
					DecodingUtils.ASCII_CHARSET, index, lfIndex + 1);

			// Process the log content line-by-line
			for (int sIndex = 0, eIndex = 0; eIndex <= lfIndex; eIndex++) {
				/*
				 * Every time we find \n (at position eIndex) we know sIndex is
				 * pointing back at the beginning of the line so sIndex to
				 * eIndex is our line; then we adjust sIndex to 1 past our \n
				 * and start again.
				 */
				if (content[eIndex] == LF) {
					/*
					 * Lines beginning with '#' are log directives and provide
					 * important metadata about our log structure. All other
					 * lines are log entries.
					 */
					switch (content[sIndex]) {
					case '#':
						// Determine the directive type
						if (ArrayUtils.equalsNoCheck(FIELDS_DIRECTIVE_PREFIX,
								0, content, sIndex,
								FIELDS_DIRECTIVE_PREFIX.length))
							parseFieldsDirective(content, sIndex, eIndex
									- sIndex + 1, callback);
						break;

					default:
						parseLogEntry(content, sIndex, eIndex - sIndex + 1,
								callback);
						break;
					}

					// Update startIndex pointer
					sIndex = eIndex + 1;
				}
			}

			/*
			 * Before looping around and reading more in from our stream, move
			 * any undecoded bytes to the front of the buffer and insert all new
			 * bytes in the buffer after them.
			 * 
			 * Be sure to adjust the index to point at the position immediately
			 * after the kept bytes so our next stream read operation doesn't
			 * overwrite any of the kept data and instead appends after it.
			 * 
			 * We increment lfIndex right away because it was pointing at the
			 * previous line's terminating \n char, we want it to point at the
			 * first valid char we want to keep.
			 */
			if ((++lfIndex) < length) {
				System.arraycopy(buffer, lfIndex, buffer, 0, length - lfIndex);

				/*
				 * Update the buffer index to point right after the kept bytes
				 * so we know where to insert new read-in bytes on the next
				 * itter.
				 */
				index = length - lfIndex;
			} else {
				// Otherwise there were no kept bytes, so back to the beginning!
				index = 0;
			}
		}

		try {
			/*
			 * Try to cleanly close our internal GZip stream without percolating
			 * a generic "I can't read this" IOException up to the caller. We
			 * want to reserve IOExceptions for read errors.
			 */
			gzipStream.close();
		} catch (IOException e) {
			throw new RuntimeException(
					"An exception occurred while trying to close the interal GZipInputStream wrapping the given source InputStream. Please ensure the source InputStream is closed now and the VM should GC the failed streams.");
		}
	}

	protected void parseFieldsDirective(char[] line, int index, int length,
			ILogParserCallback callback) throws MalformedContentException {
		IToken<char[]> token = null;

		// Init the tokenizer so we can parse the line easily.
		tokenizer.setSource(line, index, length, DELIMITERS,
				IDelimitedTokenizer.DelimiterMode.MATCH_ANY);

		/*
		 * We parse all the field names out of the #Fields directive, detecting
		 * the type of log we are parsing (DOWNLOAD or STREAMING). Then we
		 * determine the indices for each of the field names as-stored in the
		 * ILogEntry.
		 */
		while ((token = tokenizer.nextToken()) != null) {
			// Skip "#Fields:" token, get to the field names.
			if (token.getSource()[token.getIndex()] == '#')
				continue;

			String name = new String(token.getValue());

			// Use the name to try and determine the log type if needed
			if (logType == null)
				logType = LOG_TYPE_DETECTION_MAP.get(name);

			parsedFieldNames.add(name);
		}

		// Make sure we determined the logType by now, otherwise we can't work.
		if (logType == null)
			throw new MalformedContentException(
					"Unable to determine the type of log we are parsing from looking at names in the '#Fields:' directive: "
							+ new String(line, index, length));

		// Assign the appropriate wrapper that we will be using
		switch (logType) {
		case DOWNLOAD:
			logEntryWrapper = downloadLogEntryWrapper;
			break;

		case STREAMING:
			logEntryWrapper = streamingLogEntryWrapper;
			break;
		}

		/*
		 * Now that we know the log type, we know the class we need to check for
		 * field indices based on their names. Cycle back through our field
		 * names and get all the indices for them.
		 */
		for (int i = 0, size = parsedFieldNames.size(); i < size; i++) {
			int fieldIndex = -1;

			switch (logType) {
			case DOWNLOAD:
				fieldIndex = logEntryWrapper.getFieldIndex(parsedFieldNames
						.get(i));
				break;

			case STREAMING:
				fieldIndex = logEntryWrapper.getFieldIndex(parsedFieldNames
						.get(i));
				break;
			}

			/*
			 * It is possible that Amazon writes out field names we don't know
			 * how to parse yet, so we skip adding them to our active field
			 * list, but we remember the position the unknown field was add so
			 * we can avoid the associated values later.
			 */
			if (fieldIndex == -1)
				skippedFieldPositionSet.add(Integer.valueOf(i));
			else
				activeFieldIndices.add(fieldIndex);
		}
	}

	protected void parseLogEntry(char[] line, int index, int length,
			ILogParserCallback callback) {
		IToken<char[]> token = null;

		// Reset the wrapper
		logEntryWrapper.reset();

		// Init the tokenizer so we can parse the line easily.
		tokenizer.setSource(line, index, length, DELIMITERS,
				IDelimitedTokenizer.DelimiterMode.MATCH_ANY);

		/*
		 * Keep track of the index of the value we are parsing, this is how we
		 * map the values back to the specific fields we know are in the file.
		 */
		int valueIndex = 0;

		while ((token = tokenizer.nextToken()) != null) {
			// Ensure this value didn't belong to a skipped field name
			if (skippedFieldPositionSet.contains(Integer.valueOf(valueIndex))) {
				valueIndex++;
				continue;
			}

			// Value belonged to an active field, so store it.
			logEntryWrapper.setFieldValue(activeFieldIndices.get(valueIndex++)
					.intValue(), token.getValue());
		}

		// Notify the callback of the parsed values
		callback.logEntryParsed(logEntryWrapper);
	}
}