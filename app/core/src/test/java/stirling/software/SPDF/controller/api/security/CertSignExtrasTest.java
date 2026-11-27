package stirling.software.SPDF.controller.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;

import stirling.software.SPDF.model.api.security.CertificateAttribute;
import stirling.software.SPDF.model.api.security.SignPDFWithCertRequest;
import stirling.software.SPDF.service.HardwareKeyStoreService;
import stirling.software.common.service.CustomPDFDocumentFactory;
import stirling.software.common.service.SignatureLogoStore;
import stirling.software.common.util.TempFile;
import stirling.software.common.util.TempFileManager;

/** Certificate fields on the stamp, the graphic's side, and the copies on the other pages. */
@ExtendWith(MockitoExtension.class)
class CertSignExtrasTest {

    private static final PDRectangle BOX = new PDRectangle(300, 40, 220, 80);

    @Mock private CustomPDFDocumentFactory pdfDocumentFactory;
    @Mock private TempFileManager tempFileManager;
    @Mock private HardwareKeyStoreService hardwareKeyStoreService;
    @Mock private SignatureLogoStore signatureLogoStore;
    @Mock private HttpServletRequest httpRequest;

    @InjectMocks private CertSignController certSignController;

    private byte[] pfxBytes;

    @BeforeEach
    void setUp() throws Exception {
        lenient()
                .when(tempFileManager.createManagedTempFile(anyString()))
                .thenAnswer(
                        inv -> {
                            File f =
                                    Files.createTempFile("test", inv.<String>getArgument(0))
                                            .toFile();
                            TempFile tf = mock(TempFile.class);
                            lenient().when(tf.getFile()).thenReturn(f);
                            lenient().when(tf.getPath()).thenReturn(f.toPath());
                            return tf;
                        });
        lenient()
                .when(pdfDocumentFactory.load(any(MultipartFile.class)))
                .thenAnswer(inv -> Loader.loadPDF(inv.<MultipartFile>getArgument(0).getBytes()));
        try (InputStream in = new ClassPathResource("certs/test-cert.pfx").getInputStream()) {
            pfxBytes = in.readAllBytes();
        }
    }

    @Test
    void addsTheChosenCertificateFieldsToTheStamp() throws Exception {
        SignPDFWithCertRequest request = request(pages(1, 0, false));
        request.setCertificateFields(List.of(CertificateAttribute.SERIAL_NUMBER));
        request.setCertificateFieldLabels(List.of("Numero:"));
        String serial = testCertificate().getSerialNumber().toString(16);

        String text = pageText(sign(request), 0, true);

        assertTrue(text.replaceAll("\\s", "").contains("Numero:" + serial), text);
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "0, 90", "90, 0", "180, 270", "270, 180"})
    void copiesTheStampToTheSamePlaceAsTheReaderSeesIt(int signedRotation, int otherRotation)
            throws Exception {
        byte[] pdf;
        try (PDDocument doc = new PDDocument()) {
            for (int rotation : new int[] {otherRotation, signedRotation, otherRotation}) {
                PDPage page = new PDPage(new PDRectangle(600, 600));
                page.setRotation(rotation);
                doc.addPage(page);
            }
            pdf = save(doc);
        }
        SignPDFWithCertRequest request = request(pdf);
        request.setPageNumber(2);
        request.setMarkAllPages(true);

        byte[] signed = sign(request);

        try (PDDocument doc = Loader.loadPDF(signed)) {
            int[] original = inkBounds(doc, 1);
            for (int page : new int[] {0, 2}) {
                int[] copy = inkBounds(doc, page);
                for (int side = 0; side < 4; side++) {
                    assertEquals(original[side], copy[side], 2, "page " + page + " side " + side);
                }
                assertTrue(
                        doc.getPage(page).getAnnotations().getFirst() instanceof PDAnnotationLink);
            }
            assertCmsVerifies(signed, doc.getLastSignatureDictionary());
        }
        assertTrue(pageText(signed, 0, false).contains("Digitally signed by"));
    }

    @Test
    void shrinksTheCopyOntoAPageSmallerThanTheBox() throws Exception {
        byte[] pdf;
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.LETTER));
            doc.addPage(new PDPage(new PDRectangle(100, 100)));
            pdf = save(doc);
        }
        SignPDFWithCertRequest request = request(pdf);
        request.setMarkAllPages(true);

        byte[] signed = sign(request);

        try (PDDocument doc = Loader.loadPDF(signed)) {
            PDRectangle link = doc.getPage(1).getAnnotations().getFirst().getRectangle();
            assertTrue(link.getLowerLeftX() >= 0 && link.getUpperRightX() <= 100.01f);
            assertEquals(220f / 80f, link.getWidth() / link.getHeight(), 0.01);
        }
    }

    @Test
    void leavesTheOtherPagesAloneUnlessAsked() throws Exception {
        byte[] signed = sign(request(pages(2, 0, false)));

        assertFalse(pageText(signed, 1, false).contains("Digitally signed by"));
    }

    @Test
    void doesNotAddCopiesToADocumentAlreadySigned() throws Exception {
        byte[] once = sign(request(pages(2, 0, false)));
        SignPDFWithCertRequest again = request(once);
        again.setPageNumber(1);
        again.setX(20f);
        again.setY(600f);
        again.setMarkAllPages(true);

        byte[] twice = sign(again);

        assertFalse(pageText(twice, 1, false).contains("Digitally signed by"));
        try (PDDocument doc = Loader.loadPDF(twice)) {
            assertEquals(2, doc.getSignatureDictionaries().size());
        }
    }

    @Test
    void marksTheCopiesAsArtifactsAndSkipsTheLinkInATaggedDocument() throws Exception {
        SignPDFWithCertRequest request = request(pages(2, 0, true));
        request.setMarkAllPages(true);

        byte[] signed = sign(request);

        try (PDDocument doc = Loader.loadPDF(signed)) {
            assertTrue(doc.getPage(1).getAnnotations().isEmpty());
            try (InputStream in = doc.getPage(1).getContents()) {
                assertTrue(new String(in.readAllBytes()).contains(COSName.ARTIFACT.getName()));
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"LEFT", "RIGHT", "TOP", "BOTTOM"})
    void drawsTheImageOnTheChosenSide(String position) throws Exception {
        SignPDFWithCertRequest request = request(pages(1, 0, false));
        request.setStampGraphic("IMAGE");
        request.setSignatureImage(redSquare());
        request.setGraphicPosition(position);

        try (PDDocument doc = Loader.loadPDF(sign(request))) {
            BufferedImage rendered = new PDFRenderer(doc).renderImage(0, 1f);
            int[] red = bounds(rendered, rgb -> (rgb & 0xffffff) == 0xff0000);
            float pageHeight = PDRectangle.LETTER.getHeight();
            float centreX = BOX.getLowerLeftX() + BOX.getWidth() / 2;
            float centreY = pageHeight - (BOX.getLowerLeftY() + BOX.getHeight() / 2);
            switch (position) {
                case "LEFT" -> assertTrue(red[2] <= centreX + 1);
                case "RIGHT" -> assertTrue(red[0] >= centreX - 1);
                case "TOP" -> assertTrue(red[3] <= centreY + 1);
                default -> assertTrue(red[1] >= centreY - 1);
            }
        }
    }

    @Test
    void drawsTheImageFadedUnderTheDetailsWhenBehind() throws Exception {
        SignPDFWithCertRequest request = request(pages(1, 0, false));
        request.setStampGraphic("IMAGE");
        request.setSignatureImage(redSquare());
        request.setGraphicPosition("BEHIND");

        try (PDDocument doc = Loader.loadPDF(sign(request))) {
            BufferedImage rendered = new PDFRenderer(doc).renderImage(0, 1f);
            int x = Math.round(BOX.getLowerLeftX() + BOX.getWidth() / 2);
            int y =
                    Math.round(
                            PDRectangle.LETTER.getHeight()
                                    - (BOX.getLowerLeftY() + BOX.getHeight() / 2));
            int[] pink =
                    bounds(
                            rendered,
                            rgb -> {
                                Color colour = new Color(rgb);
                                return colour.getRed() == 255
                                        && colour.getGreen() > 180
                                        && colour.getGreen() < 230;
                            });
            assertTrue(pink[0] < x && pink[2] > x && pink[1] < y && pink[3] > y);
        }
    }

    @ParameterizedTest
    @CsvSource({"BEHIND, NAME", "SIDEWAYS, IMAGE"})
    void rejectsAPositionItCannotDraw(String position, String graphic) throws Exception {
        SignPDFWithCertRequest request = request(pages(1, 0, false));
        request.setStampGraphic(graphic);
        request.setSignatureImage(redSquare());
        request.setGraphicPosition(position);

        assertThrows(
                IllegalArgumentException.class,
                () -> certSignController.signPDFWithCert(request, httpRequest));
    }

    private SignPDFWithCertRequest request(byte[] pdf) {
        SignPDFWithCertRequest request = new SignPDFWithCertRequest();
        request.setFileInput(
                new MockMultipartFile(
                        "fileInput", "test.pdf", MediaType.APPLICATION_PDF_VALUE, pdf));
        request.setCertType("PFX");
        request.setP12File(
                new MockMultipartFile(
                        "p12File", "test-cert.pfx", "application/x-pkcs12", pfxBytes));
        request.setPassword("password");
        request.setShowSignature(true);
        request.setShowLogo(false);
        request.setX(BOX.getLowerLeftX());
        request.setY(BOX.getLowerLeftY());
        request.setWidth(BOX.getWidth());
        request.setHeight(BOX.getHeight());
        return request;
    }

    private byte[] sign(SignPDFWithCertRequest request) throws Exception {
        ResponseEntity<Resource> response =
                certSignController.signPDFWithCert(request, httpRequest);
        try (InputStream in = response.getBody().getInputStream()) {
            return in.readAllBytes();
        }
    }

    private static byte[] pages(int count, int rotation, boolean tagged) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < count; i++) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                page.setRotation(rotation);
                doc.addPage(page);
            }
            if (tagged) {
                doc.getDocumentCatalog().setStructureTreeRoot(new PDStructureTreeRoot());
            }
            return save(doc);
        }
    }

    private static byte[] save(PDDocument doc) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.save(out);
        return out.toByteArray();
    }

    private static MockMultipartFile redSquare() throws Exception {
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, 40, 40);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new MockMultipartFile("signatureImage", "red.png", "image/png", out.toByteArray());
    }

    /** The page's text; {@code flatten} first moves the signature's appearance into the page. */
    private static String pageText(byte[] pdf, int page, boolean flatten) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            if (flatten) {
                doc.getDocumentCatalog().getAcroForm(null).flatten();
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(page + 1);
            stripper.setEndPage(page + 1);
            // The stamp wraps its lines to fit the box.
            return stripper.getText(doc).replaceAll("\\s+", " ");
        }
    }

    private interface PixelTest {
        boolean matches(int rgb);
    }

    private static int[] inkBounds(PDDocument doc, int page) throws Exception {
        return bounds(
                new PDFRenderer(doc).renderImage(page, 1f),
                rgb -> {
                    Color colour = new Color(rgb);
                    return colour.getRed() + colour.getGreen() + colour.getBlue() < 3 * 160;
                });
    }

    private static int[] bounds(BufferedImage image, PixelTest test) {
        int[] bounds = {Integer.MAX_VALUE, Integer.MAX_VALUE, -1, -1};
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (test.matches(image.getRGB(x, y))) {
                    bounds[0] = Math.min(bounds[0], x);
                    bounds[1] = Math.min(bounds[1], y);
                    bounds[2] = Math.max(bounds[2], x);
                    bounds[3] = Math.max(bounds[3], y);
                }
            }
        }
        assertTrue(bounds[2] >= 0, "nothing matched on the page");
        return bounds;
    }

    private X509Certificate testCertificate() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new ByteArrayInputStream(pfxBytes), "password".toCharArray());
        return (X509Certificate) keyStore.getCertificate(keyStore.aliases().nextElement());
    }

    private void assertCmsVerifies(byte[] signedPdf, PDSignature signature) throws Exception {
        CMSSignedData cms =
                new CMSSignedData(
                        new CMSProcessableByteArray(signature.getSignedContent(signedPdf)),
                        new ByteArrayInputStream(signature.getContents(signedPdf)));
        SignerInformation signer = cms.getSignerInfos().getSigners().iterator().next();
        assertTrue(
                signer.verify(new JcaSimpleSignerInfoVerifierBuilder().build(testCertificate())));
    }
}
