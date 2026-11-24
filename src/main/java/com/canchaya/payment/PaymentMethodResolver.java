package com.canchaya.payment;

import com.canchaya.domain.PaymentMethodType;
import com.canchaya.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Elige el {@link PaymentMethod} correcto según el tipo pedido.
 * <p>
 * GRASP Fabricación Pura: esta clase no representa nada del mundo real (no existe un
 * "resolvedor" en una cancha); se inventa para mantener las demás clases cohesivas.
 * Spring le inyecta todas las implementaciones que encuentra, así que un medio nuevo
 * queda registrado automáticamente con solo crear su clase.
 */
@Component
public class PaymentMethodResolver {

    /** Mapa tipo → implementación. EnumMap es un mapa rápido para llaves enum. */
    private final Map<PaymentMethodType, PaymentMethod> methods = new EnumMap<>(PaymentMethodType.class);

    /** Spring pasa en la lista todos los @Component que implementan PaymentMethod. */
    public PaymentMethodResolver(List<PaymentMethod> available) {
        available.forEach(method -> methods.put(method.type(), method));
    }

    /** Devuelve el medio de pago para ese tipo, o lanza error si no se soporta. */
    public PaymentMethod resolve(PaymentMethodType type) {
        PaymentMethod method = methods.get(type);
        // Wompi no se cobra aquí: tiene su propio flujo con checkout (WompiCheckoutService).
        if (type == PaymentMethodType.WOMPI) {
            throw new BusinessException("Wompi payments go through POST /api/reservations/{id}/payments/wompi (checkout)");
        }
        if (method == null) {
            // P. ej. NEQUI/DAVIPLATA simulados en producción (canchaya.payments.simulated-enabled=false).
            throw new BusinessException("Payment method not available: " + type + ". Pay through Wompi");
        }
        return method;
    }
}
