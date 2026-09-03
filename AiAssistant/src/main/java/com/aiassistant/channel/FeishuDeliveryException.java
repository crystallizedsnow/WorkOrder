package com.aiassistant.channel;

public class FeishuDeliveryException extends RuntimeException {
    private final boolean retryable;
    private final String errorCode;

    public FeishuDeliveryException(boolean retryable, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
        this.errorCode = errorCode;
    }

    public boolean isRetryable() { return retryable; }
    public String getErrorCode() { return errorCode; }

    static FeishuDeliveryException from(int code, String message) {
        boolean retryable = code == 99991400 || code == 99991401 || code == 99991663 || code >= 500000;
        String category = retryable ? "RATE_LIMIT_OR_SERVER" : "INVALID_RECIPIENT_OR_PERMISSION";
        return new FeishuDeliveryException(retryable, category + "_" + code,
                "Feishu API code=" + code + ", message=" + message, null);
    }
}
