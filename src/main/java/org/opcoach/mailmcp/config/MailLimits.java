package org.opcoach.mailmcp.config;

import java.util.Properties;

public record MailLimits(
        int defaultSearchLimit,
        int maxSearchLimit,
        int snippetBytes,
        int maxBodyBytes,
        int maxHtmlBytes,
        int maxAttachmentBytes,
        int maxResultBytes
) {

    public static final MailLimits DEFAULTS = new MailLimits(
            10,
            25,
            500,
            12_000,
            20_000,
            5 * 1024 * 1024,
            100_000
    );

    public MailLimits {
        requirePositive(defaultSearchLimit, "limits.defaultSearchLimit");
        requirePositive(maxSearchLimit, "limits.maxSearchLimit");
        requirePositive(snippetBytes, "limits.snippetBytes");
        requirePositive(maxBodyBytes, "limits.maxBodyBytes");
        requirePositive(maxHtmlBytes, "limits.maxHtmlBytes");
        requirePositive(maxAttachmentBytes, "limits.maxAttachmentBytes");
        requirePositive(maxResultBytes, "limits.maxResultBytes");
        if (defaultSearchLimit > maxSearchLimit) {
            throw new ConfigurationException("limits.defaultSearchLimit must not exceed limits.maxSearchLimit.");
        }
    }

    public static MailLimits from(Properties properties) {
        return new MailLimits(
                intValue(properties, "limits.defaultSearchLimit", DEFAULTS.defaultSearchLimit),
                intValue(properties, "limits.maxSearchLimit", DEFAULTS.maxSearchLimit),
                intValue(properties, "limits.snippetBytes", DEFAULTS.snippetBytes),
                intValue(properties, "limits.maxBodyBytes", DEFAULTS.maxBodyBytes),
                intValue(properties, "limits.maxHtmlBytes", DEFAULTS.maxHtmlBytes),
                intValue(properties, "limits.maxAttachmentBytes", DEFAULTS.maxAttachmentBytes),
                intValue(properties, "limits.maxResultBytes", DEFAULTS.maxResultBytes)
        );
    }

    public int boundedSearchLimit(Integer requestedLimit) {
        if (requestedLimit == null || requestedLimit <= 0) {
            return defaultSearchLimit;
        }
        return Math.min(requestedLimit, maxSearchLimit);
    }

    private static int intValue(Properties properties, String key, int defaultValue) {
        String rawValue = properties.getProperty(key);
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException exception) {
            throw new ConfigurationException("Invalid integer value for " + key + ": " + rawValue);
        }
    }

    private static void requirePositive(int value, String field) {
        if (value <= 0) {
            throw new ConfigurationException(field + " must be strictly positive.");
        }
    }
}
