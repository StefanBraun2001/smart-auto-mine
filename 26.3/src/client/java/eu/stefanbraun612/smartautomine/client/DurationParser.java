package eu.stefanbraun612.smartautomine.client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses free-form duration text like "90m", "1.5h", "5400s", "1h30m", or a bare
 * number (interpreted as seconds) into a tick count. Empty/unparseable input means
 * "unlimited" (0 ticks).
 */
public class DurationParser {
	private static final Pattern PART = Pattern.compile(
			"(\\d+(?:\\.\\d+)?)\\s*(h|hr|hrs|hour|hours|m|min|mins|minute|minutes|s|sec|secs|second|seconds)?",
			Pattern.CASE_INSENSITIVE
	);

	public static long parseTicks(String text) {
		if (text == null) {
			return 0;
		}
		String trimmed = text.trim();
		if (trimmed.isEmpty()) {
			return 0;
		}

		Matcher matcher = PART.matcher(trimmed);
		double totalSeconds = 0;
		boolean matchedAny = false;
		while (matcher.find()) {
			if (matcher.group(1) == null) {
				continue;
			}
			double value = Double.parseDouble(matcher.group(1));
			String unit = matcher.group(2);
			totalSeconds += toSeconds(value, unit);
			matchedAny = true;
		}

		if (!matchedAny) {
			return 0;
		}
		return Math.round(totalSeconds * 20.0);
	}

	private static double toSeconds(double value, String unit) {
		if (unit == null || unit.isEmpty()) {
			return value; // bare number = seconds
		}
		char firstChar = Character.toLowerCase(unit.charAt(0));
		return switch (firstChar) {
			case 'h' -> value * 3600.0;
			case 'm' -> value * 60.0;
			default -> value; // 's' or anything else = seconds
		};
	}
}
