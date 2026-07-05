package com.jdbcmcp.config;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 命令行参数解析结果。
 */
public class ArgParseResult {

    private final Map<String, ArgOption>  optionIndex;
    private final Map<String, List<String>> values;

    ArgParseResult(Map<String, ArgOption> optionIndex, Map<String, List<String>> values) {
        this.optionIndex = optionIndex;
        this.values = values;
    }

    public boolean contains(String command) {
        ArgOption option = optionOf(command);
        return values.containsKey(option.getCommand());
    }

    public String get(String command) {
        List<String> optionValues = getValues(command);
        if (optionValues.isEmpty()) {
            return null;
        }
        return optionValues.get(0);
    }

    public String getOrDefault(String command, String defaultValue) {
        String value = get(command);
        return value == null ? defaultValue : value;
    }

    public List<String> getValues(String command) {
        ArgOption option = optionOf(command);
        return values.getOrDefault(option.getCommand(), Collections.emptyList());
    }

    private ArgOption optionOf(String command) {
        String normalizedCommand = AbstractArgOption.normalizeCommand(command);
        ArgOption option = optionIndex.get(normalizedCommand);
        if (option == null) {
            throw new IllegalArgumentException("Unknown argument definition: " + command);
        }
        return option;
    }
}
