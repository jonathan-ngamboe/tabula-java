package technology.tabula.detectors;

import technology.tabula.*;

import java.util.*;

/**
 * A bottom-up approach to table detection.
 * <p>
 * 1. Clustering: Groups text lines vertically into logical blocks (paragraphs vs tables).
 * 2. Verification: Checks each block for vertical white streams (column structure).
 * </p>
 */
public class ProjectionProfileDetectionAlgorithm implements DetectionAlgorithm {
    private static final float MAX_LINE_GAP = 10.0f;
    private static final float MIN_COL_GAP = 3.0f;

    @Override
    public List<Rectangle> detect(Page page) {
        List<Rectangle> detectedTables = new ArrayList<>();

        List<TextElement> textElements = new ArrayList<>(page.getText());

        if (textElements.isEmpty()) {
            return detectedTables;
        }

        textElements.sort(Comparator.comparingDouble(Rectangle::getTop));

        List<Rectangle> candidateBlocks = findTextBlocks(textElements);

        for (Rectangle block : candidateBlocks) {
            if (hasColumnStructure(page, block)) {
                detectedTables.add(block);
            }
        }

        return detectedTables;
    }

    private List<Rectangle> findTextBlocks(List<TextElement> texts) {
        List<Rectangle> blocks = new ArrayList<>();
        if (texts.isEmpty()) return blocks;

        TextElement first = texts.getFirst();
        float currentTop = (float) first.getY();
        float currentBottom = (float) first.getMaxY();
        float minX = (float) first.getX();
        float maxX = (float) first.getMaxX();

        for (int i = 1; i < texts.size(); i++) {
            TextElement t = texts.get(i);

            float gap = (float) t.getY() - currentBottom;

            if (gap < MAX_LINE_GAP) {
                currentBottom = Math.max(currentBottom, (float) t.getMaxY());
                minX = Math.min(minX, (float) t.getX());
                maxX = Math.max(maxX, (float) t.getMaxX());
            } else {
                blocks.add(new Rectangle(currentTop, minX, maxX - minX, currentBottom - currentTop));

                currentTop = (float) t.getY();
                currentBottom = (float) t.getMaxY();
                minX = (float) t.getX();
                maxX = (float) t.getMaxX();
            }
        }
        blocks.add(new Rectangle(currentTop, minX, maxX - minX, currentBottom - currentTop));

        return blocks;
    }

    private boolean hasColumnStructure(Page page, Rectangle block) {
        List<TextElement> elementsInBlock = page.getText(block);

        if (elementsInBlock.size() < 2) {
            return false;
        }

        List<FloatRange> rowRanges = new ArrayList<>();
        for (TextElement t : elementsInBlock) {
            rowRanges.add(new FloatRange((float) t.getX(), (float) t.getMaxX()));
        }

        List<FloatRange> mergedRanges = mergeOverlappingRanges(rowRanges);

        int significantGaps = 0;

        for (int i = 0; i < mergedRanges.size() - 1; i++) {
            FloatRange current = mergedRanges.get(i);
            FloatRange next = mergedRanges.get(i + 1);

            float gapSize = next.start - current.end;

            if (gapSize >= MIN_COL_GAP) {
                significantGaps++;
            }
        }

        return significantGaps >= 1;
    }

    private List<FloatRange> mergeOverlappingRanges(List<FloatRange> ranges) {
        if (ranges.isEmpty()) return ranges;

        ranges.sort(Comparator.comparing(r -> r.start));

        List<FloatRange> merged = new ArrayList<>();
        FloatRange current = ranges.getFirst();

        for (int i = 1; i < ranges.size(); i++) {
            FloatRange next = ranges.get(i);

            if (current.end >= next.start) {
                current.end = Math.max(current.end, next.end);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    private static class FloatRange {
        float start, end;

        FloatRange(float start, float end) {
            this.start = start;
            this.end = end;
        }
    }
}