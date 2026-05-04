package de.serverutilities;

public enum TimeRange {
    DAY("Tag", 86_400_000L),
    MONTH("Monat", 2_592_000_000L),
    YEAR("Jahr", 31_536_000_000L),
    ALL("Alles", Long.MAX_VALUE);

    private final String label;
    private final long millis;

    TimeRange(String label, long millis) {
        this.label = label;
        this.millis = millis;
    }

    public String label() {
        return label;
    }

    public long millis() {
        return millis;
    }

    public TimeRange next() {
        TimeRange[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
