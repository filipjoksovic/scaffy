package com.scaffy.backend.analyze;

import java.util.regex.Pattern;

record CommandRule(Pattern pattern, String ecosystem) {

	static CommandRule of(String ecosystem, String regex) {
		return new CommandRule(Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE), ecosystem);
	}
}
