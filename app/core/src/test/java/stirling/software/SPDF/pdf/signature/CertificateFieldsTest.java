package stirling.software.SPDF.pdf.signature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import stirling.software.SPDF.model.api.security.CertificateAttribute;

class CertificateFieldsTest {

    private static KeyPair keyPair;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    private static X509Certificate certificate(String subject, String issuer) throws Exception {
        long now = 1_700_000_000_000L;
        JcaX509v3CertificateBuilder builder =
                new JcaX509v3CertificateBuilder(
                        new X500Name(issuer),
                        BigInteger.valueOf(4886718345L),
                        new Date(now),
                        new Date(now + 86_400_000L),
                        new X500Name(subject),
                        keyPair.getPublic());
        ContentSigner signer =
                new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    private static List<String> lines(
            X509Certificate certificate, List<CertificateAttribute> fields) {
        return CertificateFields.lines(certificate, fields, null, ZoneOffset.UTC);
    }

    @Test
    void writesEachChosenFieldInTheOrderChosen() throws Exception {
        X509Certificate cert =
                certificate(
                        "CN=Samuel Saez,O=IMGA,OU=Desarrollo,C=ES,E=samuel@example.org",
                        "CN=Autoridad de Prueba,O=FNMT");

        assertEquals(
                List.of(
                        "Issued by: Autoridad de Prueba",
                        "Organisation: IMGA",
                        "Unit: Desarrollo",
                        "Country: ES",
                        "Email: samuel@example.org",
                        "Issuer organisation: FNMT",
                        "Serial: 123456789",
                        "Valid from: 2023.11.14",
                        "Valid until: 2023.11.15"),
                lines(
                        cert,
                        List.of(
                                CertificateAttribute.ISSUER_COMMON_NAME,
                                CertificateAttribute.SUBJECT_ORGANISATION,
                                CertificateAttribute.SUBJECT_ORGANISATIONAL_UNIT,
                                CertificateAttribute.SUBJECT_COUNTRY,
                                CertificateAttribute.SUBJECT_EMAIL,
                                CertificateAttribute.ISSUER_ORGANISATION,
                                CertificateAttribute.SERIAL_NUMBER,
                                CertificateAttribute.VALID_FROM,
                                CertificateAttribute.VALID_UNTIL)));
    }

    @Test
    void skipsAbsentFieldsAndRepeats() throws Exception {
        X509Certificate cert = certificate("CN=Solo Nombre,O=IMGA", "CN=Emisor");

        assertEquals(
                List.of("Organisation: IMGA"),
                lines(
                        cert,
                        List.of(
                                CertificateAttribute.SUBJECT_EMAIL,
                                CertificateAttribute.SUBJECT_ORGANISATION,
                                CertificateAttribute.SUBJECT_ORGANISATION)));
        assertEquals(List.of(), lines(cert, null));
    }

    @Test
    void usesTheCallersLabelsAndKeepsEnglishForBlankOnes() throws Exception {
        X509Certificate cert = certificate("CN=Samuel Saez,O=IMGA,C=ES", "CN=Emisor");

        assertEquals(
                List.of("Organización: IMGA", "Country: ES"),
                CertificateFields.lines(
                        cert,
                        List.of(
                                CertificateAttribute.SUBJECT_ORGANISATION,
                                CertificateAttribute.SUBJECT_COUNTRY),
                        Arrays.asList("Organización:", " "),
                        ZoneOffset.UTC));
    }

    @Test
    void rejectsLabelsThatDoNotMatchTheFields() throws Exception {
        X509Certificate cert = certificate("CN=Nombre,O=IMGA", "CN=Emisor");
        List<CertificateAttribute> fields = List.of(CertificateAttribute.SUBJECT_ORGANISATION);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CertificateFields.lines(
                                cert, fields, List.of("One:", "Two:"), ZoneOffset.UTC));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CertificateFields.lines(
                                cert, fields, List.of("x".repeat(201)), ZoneOffset.UTC));
    }

    @Test
    void readsNamesAsWritten() throws Exception {
        X509Certificate cert = certificate("CN=Doe\\, John,O=Acme\\, Inc.", "CN=Emisor");

        assertEquals("Doe, John", CertificateFields.commonName(cert));
        assertEquals(
                List.of("Organisation: Acme, Inc."),
                lines(cert, List.of(CertificateAttribute.SUBJECT_ORGANISATION)));
    }

    @Test
    void takesTheCommonNameOutOfAMultiValuedRdn() throws Exception {
        assertEquals(
                "GARCIA PEREZ JUAN",
                CertificateFields.commonName(
                        certificate("SERIALNUMBER=12345678Z+CN=GARCIA PEREZ JUAN", "CN=Emisor")));
    }

    @Test
    void aCertificateWithoutACommonNameHasNone() throws Exception {
        assertNull(CertificateFields.commonName(certificate("O=Acme,C=ES", "CN=Emisor")));
    }
}
