package com.enterprise.payment.processor;

import com.enterprise.payment.payment.PaymentStatus;

public record ProcessorResult(PaymentStatus status, String providerReference, String maskedInstrument,
                              String failureReason) {

    public static ProcessorResult success(String reference, String maskedInstrument) {
        return new ProcessorResult(PaymentStatus.SUCCESS, reference, maskedInstrument, null);
    }

    public static ProcessorResult pending(String reference, String maskedInstrument) {
        return new ProcessorResult(PaymentStatus.PENDING, reference, maskedInstrument, null);
    }

    public static ProcessorResult failed(String reference, String maskedInstrument, String reason) {
        return new ProcessorResult(PaymentStatus.FAILED, reference, maskedInstrument, reason);
    }
}
