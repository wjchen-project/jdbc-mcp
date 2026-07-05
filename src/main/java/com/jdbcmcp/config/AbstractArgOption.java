package com.jdbcmcp.config;

import lombok.Getter;

/**
 * 命令行参数选项抽象基类。
 * <p>
 * 选项通过长指令和可选短指令进行匹配，并通过 skipParameterCount
 * 声明解析 args 数组时该选项后续需要消费/跳过的参数数量。
 * 例如布尔开关为 0，带单个值的选项为 1。
 */
@Getter
public abstract class AbstractArgOption {

    private final String  command;
    private final String  shortCommand;
    private final String  description;
    private final int     skipParameterCount;
    private final boolean required;

    protected AbstractArgOption(String command,
                                String shortCommand,
                                String description,
                                int skipParameterCount,
                                boolean required) {
        this.command = normalizeCommand(requireCommand(command));
        this.shortCommand = normalizeShortCommand(shortCommand);
        this.description = description == null ? "" : description;
        if (skipParameterCount < 0) {
            throw new IllegalArgumentException("skipParameterCount must be greater than or equal to 0");
        }
        this.skipParameterCount = skipParameterCount;
        this.required = required;
    }

    public boolean matches(String value) {
        String normalizedValue = normalizeCommand(value);
        return command.equals(normalizedValue)
                || (shortCommand != null && shortCommand.equals(normalizedValue));
    }

    protected static String normalizeCommand(String command) {
        if (command == null) {
            return null;
        }
        return command.trim().replace('_', '-');
    }

    private static String normalizeShortCommand(String shortCommand) {
        if (shortCommand == null || shortCommand.isBlank()) {
            return null;
        }
        String normalized = normalizeCommand(shortCommand);
        if (!normalized.startsWith("-") || normalized.startsWith("--")) {
            throw new IllegalArgumentException("shortCommand must start with a single '-': " + shortCommand);
        }
        return normalized;
    }

    private static String requireCommand(String command) {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command cannot be blank");
        }
        if (!command.trim().startsWith("--")) {
            throw new IllegalArgumentException("command must start with '--': " + command);
        }
        return command;
    }
}
