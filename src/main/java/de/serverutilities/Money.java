package de.serverutilities;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

public final class Money {
    private static final DecimalFormat FORMAT = new DecimalFormat("#,##0.00");

    private Money() {
    }

    public static String format(double value) {
        return FORMAT.format(value);
    }

    public static String format(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
