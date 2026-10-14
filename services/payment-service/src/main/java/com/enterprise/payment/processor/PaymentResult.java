package com.enterprise.payment.processor;

import com.enterprise.payment.payment.PaymentStatus;

/** The provider's answer: SUCCESS, FAILED (with the reason) or PENDING (cash on delivery). */
public record PaymentResult(PaymentStatus status, String providerReference, String maskedInstrument,
                            String failureReason) {

    public static PaymentResult success(String reference, String maskedInstrument) {
        return new PaymentResult(PaymentStatus.SUCCESS, reference, maskedInstrument, null);
    }

    public static PaymentResult pending(String reference, String maskedInstrument) {
        return new PaymentResult(PaymentStatus.PENDING, reference, maskedInstrument, null);
    }

    public static PaymentResult failed(String reference, String maskedInstrument, String reason) {
        return new PaymentResult(PaymentStatus.FAILED, reference, maskedInstrument, reason);
    }
}
