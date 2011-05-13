package com.thebuzzmedia.cloudfront;

import java.io.IOException;

public class LogParserBenchmark {
	static final LogParser PARSER = new LogParser();
	static final ILogParserCallback CALLBACK = new ILogParserCallback() {
		public void logEntryParsed(ILogEntry entry) {
			if (entry == null || entry.getFieldValue(0) == null)
				throw new RuntimeException(
						"entry was null or entry.value[0] was null (typically the date).");
		}
	};

	public static void main(String[] args) {
		try {
			parse100();
			parse100k();
			parse174k();
			parse1000k();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(PARSER);
		}
	}

	public static void parse100() throws IllegalArgumentException, IOException,
			RuntimeException {
		long t = System.currentTimeMillis();

		PARSER.parse(LogParserBenchmark.class
				.getResourceAsStream("samples/example-100.gz"), CALLBACK);

		t = System.currentTimeMillis() - t;
		System.out.println("Parsed 100 Log Entries in " + t + "ms ("
				+ (100 * 1000 / t) + " entries / sec)");
	}

	public static void parse100k() throws IllegalArgumentException,
			IOException, RuntimeException {
		long t = System.currentTimeMillis();

		PARSER.parse(LogParserBenchmark.class
				.getResourceAsStream("samples/example-100k.gz"), CALLBACK);

		t = System.currentTimeMillis() - t;
		System.out.println("Parsed 100,000 Log Entries in " + t + "ms ("
				+ (100000 * 1000 / t) + " entries / sec)");
	}

	public static void parse174k() throws IllegalArgumentException,
			IOException, RuntimeException {
		long t = System.currentTimeMillis();

		PARSER.parse(LogParserBenchmark.class
				.getResourceAsStream("samples/example-174k.gz"), CALLBACK);

		t = System.currentTimeMillis() - t;
		System.out.println("Parsed 174,200 Log Entries in " + t + "ms ("
				+ (174200 * 1000 / t) + " entries / sec)");
	}

	public static void parse1000k() throws IllegalArgumentException,
			IOException, RuntimeException {
		long t = System.currentTimeMillis();

		PARSER.parse(LogParserBenchmark.class
				.getResourceAsStream("samples/example-1000k.gz"), CALLBACK);

		t = System.currentTimeMillis() - t;
		System.out.println("Parsed 1,000,000 Log Entries in " + t + "ms ("
				+ (1000000 * 1000 / t) + " entries / sec)");
	}
}