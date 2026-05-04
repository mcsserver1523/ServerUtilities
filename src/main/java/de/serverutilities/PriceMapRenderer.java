package de.serverutilities;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

public final class PriceMapRenderer extends MapRenderer {
    private static final int SIZE = 128;
    private static final int LEFT = 10;
    private static final int RIGHT = 119;
    private static final int TOP = 10;
    private static final int BOTTOM = 111;
    private static final byte BACKGROUND = MapPalette.LIGHT_BROWN;
    private static final byte GRID = MapPalette.LIGHT_GRAY;
    private static final byte AXIS = MapPalette.DARK_GRAY;
    private static final byte GREEN = MapPalette.DARK_GREEN;
    private static final byte RED = MapPalette.RED;
    private static final byte FILL_GREEN = MapPalette.LIGHT_GREEN;
    private static final byte FILL_RED = MapPalette.RED;

    private final List<PricePoint> points;
    private final boolean rising;
    private boolean rendered;

    public PriceMapRenderer(List<PricePoint> points, boolean rising) {
        super(false);
        this.points = List.copyOf(points);
        this.rising = rising;
    }

    @Override
    public void render(MapView view, MapCanvas canvas, Player player) {
        if (rendered) {
            return;
        }
        rendered = true;

        fill(canvas, BACKGROUND);
        drawGrid(canvas);
        List<PlotPoint> plot = normalize();
        if (plot.isEmpty()) {
            drawFlatLine(canvas, rising ? GREEN : RED);
            return;
        }

        byte fillColor = rising ? FILL_GREEN : FILL_RED;
        byte lineColor = rising ? GREEN : RED;
        fillUnderGraph(canvas, plot, fillColor);
        drawGraphLine(canvas, plot, lineColor);
        drawAxes(canvas);
    }

    private List<PlotPoint> normalize() {
        if (points.isEmpty()) {
            return List.of();
        }
        double min = points.stream().mapToDouble(PricePoint::price).min().orElse(0.0);
        double max = points.stream().mapToDouble(PricePoint::price).max().orElse(min);
        long start = points.get(0).time();
        long end = points.get(points.size() - 1).time();
        double priceRange = Math.max(0.000001, max - min);
        long timeRange = Math.max(1L, end - start);

        List<PlotPoint> plot = new ArrayList<>();
        if (points.size() == 1 || Math.abs(max - min) < 0.000001) {
            for (int index = 0; index < Math.max(2, points.size()); index++) {
                int x = index == 0 ? LEFT : RIGHT;
                plot.add(new PlotPoint(x, (TOP + BOTTOM) / 2));
            }
            return plot;
        }

        for (PricePoint point : points) {
            int x = LEFT + (int) Math.round((RIGHT - LEFT) * ((double) (point.time() - start) / timeRange));
            int y = BOTTOM - (int) Math.round((BOTTOM - TOP) * ((point.price() - min) / priceRange));
            plot.add(new PlotPoint(clamp(x, LEFT, RIGHT), clamp(y, TOP, BOTTOM)));
        }
        return plot;
    }

    private void fill(MapCanvas canvas, byte color) {
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                canvas.setPixel(x, y, color);
            }
        }
    }

    private void drawGrid(MapCanvas canvas) {
        for (int x = LEFT; x <= RIGHT; x += 18) {
            drawLine(canvas, x, TOP, x, BOTTOM, GRID);
        }
        for (int y = TOP; y <= BOTTOM; y += 17) {
            drawLine(canvas, LEFT, y, RIGHT, y, GRID);
        }
        drawAxes(canvas);
    }

    private void drawAxes(MapCanvas canvas) {
        drawLine(canvas, LEFT, BOTTOM, RIGHT, BOTTOM, AXIS);
        drawLine(canvas, LEFT, TOP, LEFT, BOTTOM, AXIS);
    }

    private void drawFlatLine(MapCanvas canvas, byte color) {
        int y = (TOP + BOTTOM) / 2;
        drawLine(canvas, LEFT, y, RIGHT, y, color);
    }

    private void fillUnderGraph(MapCanvas canvas, List<PlotPoint> plot, byte color) {
        for (int index = 1; index < plot.size(); index++) {
            PlotPoint previous = plot.get(index - 1);
            PlotPoint current = plot.get(index);
            int minX = Math.min(previous.x, current.x);
            int maxX = Math.max(previous.x, current.x);
            for (int x = minX; x <= maxX; x++) {
                double progress = previous.x == current.x ? 0.0 : (double) (x - previous.x) / (current.x - previous.x);
                int y = (int) Math.round(previous.y + (current.y - previous.y) * progress);
                drawLine(canvas, x, y, x, BOTTOM - 1, color);
            }
        }
    }

    private void drawGraphLine(MapCanvas canvas, List<PlotPoint> plot, byte color) {
        for (int index = 1; index < plot.size(); index++) {
            PlotPoint previous = plot.get(index - 1);
            PlotPoint current = plot.get(index);
            drawLine(canvas, previous.x, previous.y, current.x, current.y, color);
            drawLine(canvas, previous.x, previous.y + 1, current.x, current.y + 1, color);
        }
    }

    private void drawLine(MapCanvas canvas, int x0, int y0, int x1, int y1, byte color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int error = dx - dy;

        while (true) {
            if (x0 >= 0 && x0 < SIZE && y0 >= 0 && y0 < SIZE) {
                canvas.setPixel(x0, y0, color);
            }
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int e2 = 2 * error;
            if (e2 > -dy) {
                error -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                error += dx;
                y0 += sy;
            }
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record PlotPoint(int x, int y) {
    }
}
