package stirling.software.SPDF.pdf.signature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.ToDoubleFunction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import stirling.software.SPDF.pdf.signature.StampLayout.GraphicPosition;

class StampLayoutPositionTest {

    private static final ToDoubleFunction<String> MEASURE = line -> line.length() * 0.5;
    private static final List<String> DETAILS = List.of("Digitally signed by Ada", "Date: 2026");

    private static StampLayout layout(float width, float height, GraphicPosition position) {
        return StampLayout.of(width, height, null, 1, DETAILS, MEASURE, position);
    }

    private static float textLeft(StampLayout layout) {
        return layout.details().lefts().getFirst();
    }

    @ParameterizedTest
    @CsvSource({"200, 50", "60, 160"})
    void autoMatchesTheLayoutWithoutAPosition(float width, float height) {
        assertEquals(
                StampLayout.of(width, height, null, 1, DETAILS, MEASURE),
                layout(width, height, GraphicPosition.AUTO));
    }

    @ParameterizedTest
    @CsvSource({"200, 100", "100, 200"})
    void leftAndRightSplitTheWidthWhateverTheShape(float width, float height) {
        StampLayout left = layout(width, height, GraphicPosition.LEFT);
        StampLayout right = layout(width, height, GraphicPosition.RIGHT);

        assertTrue(left.logo().x() + left.logo().width() <= textLeft(left) + 0.01f);
        assertTrue(textLeft(right) < right.logo().x());
        assertTrue(right.logo().x() >= width / 2 - 0.01f);
    }

    @ParameterizedTest
    @CsvSource({"200, 100", "100, 200"})
    void topAndBottomSplitTheHeightWhateverTheShape(float width, float height) {
        StampLayout top = layout(width, height, GraphicPosition.TOP);
        StampLayout bottom = layout(width, height, GraphicPosition.BOTTOM);

        assertTrue(top.logo().y() >= height / 2 - 0.01f);
        assertTrue(top.details().baselines().getFirst() < height / 2);
        assertTrue(bottom.logo().y() + bottom.logo().height() <= height / 2 + 0.01f);
        assertTrue(bottom.details().baselines().getLast() > height / 2);
    }

    @Test
    void theSignersNameMovesWithTheGraphic() {
        StampLayout right =
                StampLayout.of(200, 50, "Ada", 0, DETAILS, MEASURE, GraphicPosition.RIGHT);

        assertTrue(right.name().lefts().getFirst() > textLeft(right));
    }
}
