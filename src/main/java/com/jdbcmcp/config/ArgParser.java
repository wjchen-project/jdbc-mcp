package com.jdbcmcp.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 命令行参数解析器。
 * <p>
 * 通过 Builder 注册 {@link ArgOption} 后，可解析长指令、短指令以及 --key=value 形式参数。
 */
public class ArgParser {

    private final String                 usageHeader;
    private final List<ArgOption>        options;
    private final Map<String, ArgOption> optionIndex;

    private ArgParser(Builder builder) {
        this.usageHeader = builder.usageHeader;
        this.options = Collections.unmodifiableList(new ArrayList<>(builder.options));
        this.optionIndex = Collections.unmodifiableMap(buildOptionIndex(this.options));
    }

    public static Builder builder() {
        return new Builder();
    }

    public ArgParseResult parse(String[] args) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        if (args == null) {
            args = new String[0];
        }

        for (int i = 0; i < args.length; i++) {
            String rawArg = args[i];
            if (rawArg == null || rawArg.isBlank()) {
                continue;
            }

            InlineArg inlineArg = splitInlineArg(rawArg);
            ArgOption option = optionIndex.get(inlineArg.command());
            if (option == null) {
                throw new IllegalArgumentException("Unknown argument: " + rawArg);
            }
            if (values.containsKey(option.getCommand())) {
                throw new IllegalArgumentException("Duplicate argument: " + option.getCommand());
            }

            List<String> optionValues = new ArrayList<>();
            if (inlineArg.value() != null) {
                if (option.getSkipParameterCount() == 0) {
                    throw new IllegalArgumentException("Argument does not accept value: " + option.getCommand());
                }
                optionValues.add(inlineArg.value());
            }

            int remainingValueCount = option.getSkipParameterCount() - optionValues.size();
            for (int offset = 0; offset < remainingValueCount; offset++) {
                int valueIndex = i + 1;
                if (valueIndex >= args.length || args[valueIndex] == null) {
                    throw new IllegalArgumentException("Missing value for argument: " + option.getCommand());
                }
                if (isRegisteredOptionToken(args[valueIndex])) {
                    throw new IllegalArgumentException("Missing value for argument: " + option.getCommand());
                }
                optionValues.add(args[valueIndex]);
                i++;
            }

            values.put(option.getCommand(), Collections.unmodifiableList(optionValues));
        }

        for (ArgOption option : options) {
            if (option.isRequired() && !values.containsKey(option.getCommand())) {
                throw new IllegalArgumentException("Missing required argument: " + option.getCommand());
            }
            if (values.containsKey(option.getCommand())) {
                option.validate(values.get(option.getCommand()));
            }
        }

        return new ArgParseResult(optionIndex, Collections.unmodifiableMap(values));
    }

    public String usage() {
        StringBuilder builder = new StringBuilder();
        if (usageHeader != null && !usageHeader.isBlank()) {
            builder.append(usageHeader).append(System.lineSeparator());
        }
        builder.append("Options:").append(System.lineSeparator());
        for (ArgOption option : options) {
            builder.append("  ")
                    .append(option.getCommand());
            if (option.getShortCommand() != null) {
                builder.append(", ").append(option.getShortCommand());
            }
            for (int i = 0; i < option.getSkipParameterCount(); i++) {
                builder.append(" <value>");
            }
            if (option.isRequired()) {
                builder.append(" (required)");
            }
            if (!option.getDescription().isBlank()) {
                builder.append(System.lineSeparator())
                        .append("      ")
                        .append(option.getDescription());
            }
            builder.append(System.lineSeparator());
        }
        return builder.toString();
    }

    private boolean isRegisteredOptionToken(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        InlineArg inlineArg = splitInlineArg(value);
        return optionIndex.containsKey(inlineArg.command());
    }

    private static InlineArg splitInlineArg(String rawArg) {
        String command = rawArg;
        String value = null;
        int equalsIndex = rawArg.indexOf('=');
        if (equalsIndex >= 0) {
            command = rawArg.substring(0, equalsIndex);
            value = rawArg.substring(equalsIndex + 1);
        }
        return new InlineArg(AbstractArgOption.normalizeCommand(command), value);
    }

    private static Map<String, ArgOption> buildOptionIndex(List<ArgOption> options) {
        Map<String, ArgOption> optionIndex = new LinkedHashMap<>();
        for (ArgOption option : options) {
            putUnique(optionIndex, option.getCommand(), option);
            if (option.getShortCommand() != null) {
                putUnique(optionIndex, option.getShortCommand(), option);
            }
        }
        return optionIndex;
    }

    private static void putUnique(Map<String, ArgOption> optionIndex, String command, ArgOption option) {
        if (optionIndex.containsKey(command)) {
            throw new IllegalArgumentException("Duplicate argument definition: " + command);
        }
        optionIndex.put(command, option);
    }

    private record InlineArg(String command, String value) {
    }

    public static class Builder {
        private String          usageHeader;
        private final List<ArgOption> options = new ArrayList<>();

        public Builder usageHeader(String usageHeader) {
            this.usageHeader = usageHeader;
            return this;
        }

        public Builder register(ArgOption option) {
            if (option == null) {
                throw new IllegalArgumentException("option cannot be null");
            }
            options.add(option);
            return this;
        }

        public ArgParser build() {
            return new ArgParser(this);
        }
    }
}
