package akyrion.thepredict.eval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The report SVG has no script and no external resource (M22.4). */
class EvalHtmlWriterTest {
    @Test
    void theDiagramIsInlineSvg() {
        var html = "<html>" + Calibration.svg(new double[][] {{0.5, 0.5, 2}}) + "</html>";
        assertTrue(html.contains("<svg"));
        assertTrue(!html.contains("<script"));
        assertTrue(!html.contains("https://"));
        assertTrue(!html.replace("xmlns=\"http://www.w3.org/2000/svg\"", "").contains("http://"));
    }
}
