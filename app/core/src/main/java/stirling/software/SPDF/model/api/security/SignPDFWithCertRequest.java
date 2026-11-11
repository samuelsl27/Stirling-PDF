package stirling.software.SPDF.model.api.security;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;
import lombok.EqualsAndHashCode;

import stirling.software.common.model.api.PDFFile;

@Data
@EqualsAndHashCode(callSuper = true)
public class SignPDFWithCertRequest extends PDFFile {

    @Schema(
            description =
                    "SERVER: this server's own certificate, no file or password. Signer shown is"
                            + " the server's organisation, not the user; self-signed by default, so"
                            + " other PDF readers may show it unverified. PEM needs"
                            + " certFile+privateKeyFile, PKCS12/PFX p12File, JKS jksFile."
                            + " WINDOWS_STORE/PKCS11: desktop app only.",
            allowableValues = {"SERVER", "PEM", "PKCS12", "PFX", "JKS", "WINDOWS_STORE", "PKCS11"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String certType;

    @Schema(
            description =
                    "Private key for certType PEM, used with certFile: a PEM text file (.pem or"
                            + " .key), unencrypted or password-protected.")
    private MultipartFile privateKeyFile;

    @Schema(
            description =
                    "Certificate for certType PEM, used with privateKeyFile (.pem, .crt, .cer or"
                            + " .der).")
    private MultipartFile certFile;

    @Schema(description = "PKCS#12 keystore for certType PKCS12 or PFX (.p12 or .pfx).")
    private MultipartFile p12File;

    @Schema(description = "Java KeyStore for certType JKS (.jks).")
    private MultipartFile jksFile;

    @Schema(
            description =
                    "Password for the PKCS12/PFX or JKS keystore or an encrypted PEM key, or the"
                            + " PKCS11 token PIN. Leave empty if the key has none. Not needed for"
                            + " SERVER or WINDOWS_STORE.",
            format = "password")
    private String password;

    @Schema(
            description =
                    "The alias of the certificate to sign with. Required for WINDOWS_STORE and"
                            + " recommended for PKCS11 tokens holding multiple certificates.")
    private String alias;

    @Schema(
            description =
                    "Absolute path to the PKCS#11 driver library (required for PKCS11 type). Must"
                            + " be an allowed driver - a detected one or configured via"
                            + " STIRLING_PKCS11_LIBRARIES.")
    private String pkcs11LibraryPath;

    @Schema(
            description =
                    "Optional PKCS#11 slot index. When omitted the first slot with a token is"
                            + " used.")
    private Integer pkcs11Slot;

    @Schema(
            description =
                    "Add a visible signature box on pageNumber showing the certificate's name, the"
                            + " date and the reason (if set).",
            defaultValue = "false",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean showSignature = false;

    @Schema(
            description = "The reason for signing the PDF. Omitted from the signature when not set",
            example = "Signed by SPDF")
    private String reason;

    @Schema(
            description =
                    "The location where the PDF is signed. Omitted from the signature when not set",
            example = "SPDF")
    private String location;

    @Schema(
            description =
                    "Signer name stored in the signature properties; the visible box shows the"
                            + " certificate's name instead. Omitted when not set.",
            example = "SPDF")
    private String name;

    @Schema(
            description =
                    "Page (1-based) for the visible signature. Only used when showSignature is"
                            + " true.",
            defaultValue = "1")
    private Integer pageNumber = 1;

    @Schema(
            description = "Whether to visually show a signature logo along with the signature",
            defaultValue = "true",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean showLogo = true;

    @Schema(
            description =
                    "Image drawn inside the signature box when showLogo is set, typically the"
                            + " signer's organisation logo. PNG or JPEG. Omit to keep the bundled"
                            + " mark.")
    private MultipartFile logoImage;

    @Schema(
            description =
                    "Where the logo sits inside the signature box. BEHIND draws it under the text"
                            + " as a watermark; the others give it a strip of its own. Sending it"
                            + " without a signature box is allowed and applies the same layout to"
                            + " the default box.",
            defaultValue = "LEFT")
    private SignatureLogoPosition logoPosition;

    @Schema(
            description =
                    "Distance in PDF points from the left edge of the page to the left edge of the"
                            + " signature box. Omit to keep the legacy bottom-left placement.")
    private Float signatureX;

    @Schema(
            description =
                    "Distance in PDF points from the bottom edge of the page to the bottom edge of"
                            + " the signature box. PDF user space, same convention as the crop"
                            + " endpoint: the origin is the bottom-left corner and y grows"
                            + " upwards.")
    private Float signatureY;

    @Schema(description = "Width of the signature box in PDF points.", defaultValue = "200")
    private Float signatureWidth;

    @Schema(description = "Height of the signature box in PDF points.", defaultValue = "50")
    private Float signatureHeight;

    @Schema(
            description =
                    "Certificate fields to draw inside the signature box, in the order given. Omit"
                            + " to keep the legacy content (signer name, date and reason). Fields"
                            + " the certificate does not carry are skipped rather than drawn"
                            + " blank.")
    private List<CertificateAttribute> visibleAttributes;

    @Schema(
            description =
                    "Labels to draw in front of each of visibleAttributes, in the same order. The"
                            + " application holds its own translations, and the signature is baked"
                            + " into the document, so the caller says what the reader should see."
                            + " Omit to get English.")
    private List<String> visibleAttributeLabels;

    @Schema(
            description =
                    "Repeat the signature's appearance on every page. A PDF signature has one"
                            + " widget on one page, so only the page given by pageNumber carries"
                            + " the actual signature; the other pages get a visual mark with the"
                            + " same content that is NOT a signature. Tell the user that, or the"
                            + " document looks signed in more places than it is.",
            defaultValue = "false")
    private Boolean markAllPages;
}
