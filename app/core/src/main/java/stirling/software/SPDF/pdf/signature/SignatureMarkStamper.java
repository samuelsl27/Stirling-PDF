package stirling.software.SPDF.pdf.signature;

import java.io.IOException;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination;
import org.apache.pdfbox.util.Matrix;

/**
 * Repeats the look of the visible signature on the other pages. What it draws is page content, not
 * a signature: only the signed page carries the widget a reader can validate, so each copy links to
 * it. The copies have to be drawn before signing, so that the signature covers them.
 */
public final class SignatureMarkStamper {

    private SignatureMarkStamper() {}

    /**
     * Draws {@code stamp} on every page but the signed one, where the signature sits as the reader
     * sees the signed page, shrunk and moved inwards on a page too small for that. In a tagged
     * document the copies are marked as artifacts and get no link, which would need a structure
     * element of its own.
     *
     * @param stamp a one-page document holding the signature's look, upright, at its visible size
     * @return how many pages were drawn on
     */
    public static int stampOtherPages(
            PDDocument document, SignatureStampPlacement signed, PDDocument stamp)
            throws IOException {
        if (document.getNumberOfPages() < 2) {
            return 0;
        }
        PDPage signedPage = document.getPage(signed.pageIndex());
        PDRectangle visible = visibleBox(signed, signedPage.getCropBox());
        PDFormXObject form = new LayerUtility(document).importPageAsForm(stamp, 0);
        boolean tagged = document.getDocumentCatalog().getStructureTreeRoot() != null;

        int stamped = 0;
        for (int index = 0; index < document.getNumberOfPages(); index++) {
            if (index == signed.pageIndex()) {
                continue;
            }
            PDPage page = document.getPage(index);
            PDRectangle cropBox = page.getCropBox();
            int rotation = Math.floorMod(Math.round(page.getRotation() / 90f) * 90, 360);
            boolean quarterTurned = rotation == 90 || rotation == 270;
            float pageWidth = quarterTurned ? cropBox.getHeight() : cropBox.getWidth();
            float pageHeight = quarterTurned ? cropBox.getWidth() : cropBox.getHeight();
            float scale =
                    Math.min(
                            1,
                            Math.min(
                                    pageWidth / visible.getWidth(),
                                    pageHeight / visible.getHeight()));
            float width = visible.getWidth() * scale;
            float height = visible.getHeight() * scale;
            PDRectangle target =
                    SignatureStampPlacement.toUserSpace(
                            cropBox,
                            rotation,
                            clamp(visible.getLowerLeftX(), pageWidth - width),
                            clamp(visible.getLowerLeftY(), pageHeight - height),
                            width,
                            height);

            try (PDPageContentStream content =
                    new PDPageContentStream(document, page, AppendMode.APPEND, true, true)) {
                content.beginMarkedContent(COSName.ARTIFACT);
                content.saveGraphicsState();
                content.transform(upright(target, rotation));
                content.transform(Matrix.getScaleInstance(scale, scale));
                content.drawForm(form);
                content.restoreGraphicsState();
                content.endMarkedContent();
            }
            if (!tagged) {
                linkTo(page, target, signedPage);
            }
            stamped++;
        }
        return stamped;
    }

    /** The signature's box as the reader sees it, from the visible page's bottom-left corner. */
    static PDRectangle visibleBox(SignatureStampPlacement signed, PDRectangle cropBox) {
        PDRectangle box = signed.rectangle();
        float width = signed.visibleWidth();
        float height = signed.visibleHeight();
        return switch (signed.rotation()) {
            case 90 ->
                    new PDRectangle(
                            box.getLowerLeftY() - cropBox.getLowerLeftY(),
                            cropBox.getUpperRightX() - box.getUpperRightX(),
                            width,
                            height);
            case 180 ->
                    new PDRectangle(
                            cropBox.getUpperRightX() - box.getUpperRightX(),
                            cropBox.getUpperRightY() - box.getUpperRightY(),
                            width,
                            height);
            case 270 ->
                    new PDRectangle(
                            cropBox.getUpperRightY() - box.getUpperRightY(),
                            box.getLowerLeftX() - cropBox.getLowerLeftX(),
                            width,
                            height);
            default ->
                    new PDRectangle(
                            box.getLowerLeftX() - cropBox.getLowerLeftX(),
                            box.getLowerLeftY() - cropBox.getLowerLeftY(),
                            width,
                            height);
        };
    }

    private static float clamp(float value, float max) {
        return Math.max(0, Math.min(value, max));
    }

    // Moves the origin to the corner of the target that the reader sees at the bottom-left, and
    // turns the axes so that content drawn upright reads upright once the page is rotated.
    private static Matrix upright(PDRectangle target, int rotation) {
        return switch (rotation) {
            case 90 ->
                    Matrix.getRotateInstance(
                            Math.toRadians(90), target.getUpperRightX(), target.getLowerLeftY());
            case 180 ->
                    Matrix.getRotateInstance(
                            Math.toRadians(180), target.getUpperRightX(), target.getUpperRightY());
            case 270 ->
                    Matrix.getRotateInstance(
                            Math.toRadians(270), target.getLowerLeftX(), target.getUpperRightY());
            default -> Matrix.getTranslateInstance(target.getLowerLeftX(), target.getLowerLeftY());
        };
    }

    private static void linkTo(PDPage page, PDRectangle area, PDPage signedPage)
            throws IOException {
        PDAnnotationLink link = new PDAnnotationLink();
        link.setRectangle(area);
        PDBorderStyleDictionary border = new PDBorderStyleDictionary();
        border.setWidth(0);
        link.setBorderStyle(border);
        PDPageFitWidthDestination destination = new PDPageFitWidthDestination();
        destination.setPage(signedPage);
        PDActionGoTo action = new PDActionGoTo();
        action.setDestination(destination);
        link.setAction(action);
        link.setContents("Go to the digital signature");
        page.getAnnotations().add(link);
    }
}
