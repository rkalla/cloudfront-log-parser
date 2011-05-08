package com.thebuzzmedia.cloudfront;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import com.thebuzzmedia.common.charset.DecodingUtils;
import com.thebuzzmedia.common.lexer.CharArrayTokenizer;
import com.thebuzzmedia.common.lexer.IToken;
import com.thebuzzmedia.common.lexer.ITokenizer;
import com.thebuzzmedia.common.util.ArrayUtils;

public class LogParser {
	public static final String GZIP_BUFFER_SIZE_PROPERTY_NAME = "cloudfront.logparser.gzipBufferSize";
	public static final String BUFFER_SIZE_PROPERTY_NAME = "cloudfront.logparser.bufferSize";

	// TODO: Maybe consider bigger defaults since this will run on a server
	public static final int GZIP_BUFFER_SIZE = Integer.getInteger(
			GZIP_BUFFER_SIZE_PROPERTY_NAME, 32768);

	public static final int BUFFER_SIZE = Integer.getInteger(
			BUFFER_SIZE_PROPERTY_NAME, 32768);

	public static final byte LF = 10; // \n

	public static final char[] DELIMITERS = { ' ', '\t', '\r', '\n' };

	private static final int MIN_GZIP_BUFFER_SIZE = 1024;
	private static final int MIN_BUFFER_SIZE = 1024;
	private static final char[] FIELDS_DIRECTIVE_PREFIX = { '#', 'F', 'i', 'e',
			'l', 'd', 's', ':' };

	private static final Map<String, ILogEntry.Type> LOG_TYPE_DETECTION_MAP = new HashMap<String, ILogEntry.Type>(
			32);

	static {
		// Init system properties
		if (GZIP_BUFFER_SIZE <= MIN_GZIP_BUFFER_SIZE)
			throw new RuntimeException(
					"GZIP_BUFFER_SIZE (sys prop "
							+ GZIP_BUFFER_SIZE_PROPERTY_NAME
							+ ") is currently set below the min allowed value of "
							+ MIN_GZIP_BUFFER_SIZE
							+ ". You must increase this value for the parser to operate correctly.");

		if (BUFFER_SIZE <= MIN_BUFFER_SIZE)
			throw new RuntimeException(
					"BUFFER_SIZE (sys prop "
							+ BUFFER_SIZE_PROPERTY_NAME
							+ ") is currently set below the min allowed value of "
							+ MIN_GZIP_BUFFER_SIZE
							+ ". You must increase this value for the parser to operate correctly.");

		// Init the detection map with DOWNLOAD-only fields
		LOG_TYPE_DETECTION_MAP.put("cs-method", ILogEntry.Type.DOWNLOAD);
		LOG_TYPE_DETECTION_MAP.put("sc-status", ILogEntry.Type.DOWNLOAD);
		LOG_TYPE_DETECTION_MAP.put("cs(Host)", ILogEntry.Type.DOWNLOAD);
		LOG_TYPE_DETECTION_MAP.put("cs(Referer)", ILogEntry.Type.DOWNLOAD);
		LOG_TYPE_DETECTION_MAP.put("cs(User-Agent)", ILogEntry.Type.DOWNLOAD);

		// Init the detection map with STREAMING-only fields
		LOG_TYPE_DETECTION_MAP.put("x-event", ILogEntry.Type.STREAMING);
		LOG_TYPE_DETECTION_MAP.put("x-cf-status", ILogEntry.Type.STREAMING);
		LOG_TYPE_DETECTION_MAP.put("c-referrer", ILogEntry.Type.STREAMING);
		LOG_TYPE_DETECTION_MAP.put("c-user-agent", ILogEntry.Type.STREAMING);
		LOG_TYPE_DETECTION_MAP.put("x-page-url", ILogEntry.Type.STREAMING);
	}

	private int index;
	private int length;
	private int readCount;
	private byte[] buffer;

	private ILogEntry.Type logType;
	private ILogEntry logEntryWrapper;

	private ITokenizer<char[]> tokenizer;
	private List<String> parsedFieldNames;
	private List<Integer> activeFieldIndices;

	public LogParser() {
		buffer = new byte[BUFFER_SIZE];
		tokenizer = new CharArrayTokenizer();
		parsedFieldNames = new ArrayList<String>(ILogEntry.MAX_STREAMING_FIELDS);
		activeFieldIndices = new ArrayList<Integer>(
				ILogEntry.MAX_STREAMING_FIELDS);
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
	}

	public void parse(InputStream stream, ILogParserCallback callback)
			throws IllegalArgumentException, IOException, RuntimeException {
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
				throw new RuntimeException(
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
	}

	protected void parseFieldsDirective(char[] line, int index, int length,
			ILogParserCallback callback) {
		IToken<char[]> token = null;

		// Init the tokenizer so we can parse the line easily.
		tokenizer.setSource(line, DELIMITERS,
				ITokenizer.DelimiterType.MATCH_ANY, index, length);

		/*
		 * We parse all the field names out of the #Fields directive, detecting
		 * the type of log we are parsing (DOWNLOAD or STREAMING). Then we
		 * determine the indices for each of the field names as-stored in the
		 * ILogEntry.
		 */
		while ((token = tokenizer.nextToken()) != null) {
			// Skip the value of it's the initial directive
			if (token.getSource()[token.getIndex()] == '#')
				continue;

			String name = token.toString();

			// Use the name to try and determine the log type if needed
			if (logType == null)
				logType = LOG_TYPE_DETECTION_MAP.get(name);

			parsedFieldNames.add(name);
		}

		// Make sure we determined the logType by now, otherwise we can't work.
		if (logType == null)
			throw new RuntimeException(
					"Unable to determine the type of log we are parsing from looking at the given #Fields directive from line: "
							+ new String(line, index, length));
		else {
			switch (logType) {
			case DOWNLOAD:
				logEntryWrapper = new DownloadLogEntry();
				break;

			case STREAMING:
				logEntryWrapper = new StreamingLogEntry();
				break;
			}
		}

		/*
		 * Now that we know the log type, we know the class we need to check for
		 * field indices based on their names. Cycle back through our field
		 * names and get all the indices for them.
		 */
		for (int i = 0, size = parsedFieldNames.size(); i < size; i++) {
			Integer fieldIndex = null;

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

			if (fieldIndex != null)
				activeFieldIndices.add(fieldIndex);
		}
	}

	protected void parseLogEntry(char[] line, int index, int length,
			ILogParserCallback callback) {
		IToken<char[]> token = null;

		// Init the tokenizer so we can parse the line easily.
		tokenizer.setSource(line, DELIMITERS,
				ITokenizer.DelimiterType.MATCH_ANY, index, length);

		/*
		 * Keep track of the index of the value we are parsing, this is how we
		 * map the values back to the specific fields we know are in the file.
		 */
		int valueIndex = 0;

		while ((token = tokenizer.nextToken()) != null)
			logEntryWrapper.setFieldValue(activeFieldIndices.get(valueIndex++),
					token.getValue());

		// Notify the callback of the parsed values
		callback.logEntryParsed(logEntryWrapper);
	}
}