package com.jdbcmcp.config;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * 命令行参数选项定义。
 */
public class ArgOption extends AbstractArgOption {

    private final BiConsumer<ArgOption, List<String>> validator;

    private ArgOption(Builder builder) {
        super(builder.command,
                builder.shortCommand,
                builder.description,
                builder.skipParameterCount,
                builder.required);
        this.validator = builder.validator;
    }

    public void validate(List<String> values) {
        validator.accept(this, values);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String  command;
        private String  shortCommand;
        private String  description;
        private int     skipParameterCount = 0;
        private boolean required = false;
        private BiConsumer<ArgOption, List<String>> validator = (option, values) -> {
        };

        public Builder command(String command) {
            this.command = command;
            return this;
        }

        public Builder shortCommand(String shortCommand) {
            this.shortCommand = shortCommand;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder skipParameterCount(int skipParameterCount) {
            this.skipParameterCount = skipParameterCount;
            return this;
        }

        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        public Builder validator(BiConsumer<ArgOption, List<String>> validator) {
            if (validator == null) {
                throw new IllegalArgumentException("validator cannot be null");
            }
            this.validator = validator;
            return this;
        }

        public ArgOption build() {
            return new ArgOption(this);
        }
    }
}
