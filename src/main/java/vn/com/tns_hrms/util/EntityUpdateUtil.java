package vn.com.tns_hrms.util;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.Consumer;

public final class EntityUpdateUtil {
    private EntityUpdateUtil() {
    }

    public static <T> void updateIfChanged(T currentValue, T requestedValue, Consumer<T> setter) {
        boolean unchanged = currentValue instanceof BigDecimal currentAmount
                && requestedValue instanceof BigDecimal requestedAmount
                        ? currentAmount.compareTo(requestedAmount) == 0
                        : Objects.equals(currentValue, requestedValue);
        if (!unchanged) {
            setter.accept(requestedValue);
        }
    }
}
