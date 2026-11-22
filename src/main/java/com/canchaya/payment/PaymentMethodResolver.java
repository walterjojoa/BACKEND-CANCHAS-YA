package com.canchaya.payment;

import com.canchaya.domain.PaymentMethodType;
import com.canchaya.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * GRASP Pure Fabrication: picks the right {@link PaymentMethod} for a type.
 * Spring injects every implementation it finds, so a new method is registered
 * automatically just by creating its class.
 */
@Component
public class PaymentMethodResolver {

    private final Map<PaymentMethodType, PaymentMethod> methods = new EnumMap<>(PaymentMethodType.class);

    public PaymentMethodResolver(List<PaymentMethod> available) {
        available.forEach(method -> methods.put(method.type(), method));
    }

    public PaymentMethod resolve(PaymentMethodType type) {
        PaymentMethod method = methods.get(type);
        if (method == null) {
            throw new BusinessException("Payment method not supported: " + type);
        }
        return method;
    }
}
