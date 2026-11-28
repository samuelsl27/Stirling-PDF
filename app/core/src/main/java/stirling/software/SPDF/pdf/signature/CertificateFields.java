package stirling.software.SPDF.pdf.signature;

import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;

import javax.security.auth.x500.X500Principal;

import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.x500.AttributeTypeAndValue;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;

import stirling.software.SPDF.model.api.security.CertificateAttribute;
import stirling.software.common.util.ExceptionUtils;

/** Values read off a signing certificate, as a visible signature writes them. */
public final class CertificateFields {

    private static final int MAX_LABEL_LENGTH = 200;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private CertificateFields() {}

    /** The subject's common name as written, or null when the certificate has none. */
    public static String commonName(X509Certificate certificate) {
        return value(certificate.getSubjectX500Principal(), BCStyle.CN);
    }

    /**
     * One {@code label value} line per chosen field, in the order chosen. A field the certificate
     * does not carry gets no line.
     *
     * @param fields the fields to write, or null for none; repeats are written once
     * @param labels null for the English labels, or one per entry of {@code fields} in the same
     *     order, where a blank one keeps its English label
     * @param zone the time zone the validity dates are written in
     * @throws IllegalArgumentException when {@code labels} does not match {@code fields} or one is
     *     over 200 characters
     */
    public static List<String> lines(
            X509Certificate certificate,
            List<CertificateAttribute> fields,
            List<String> labels,
            ZoneId zone) {
        if (fields == null || fields.isEmpty()) {
            return List.of();
        }
        checkLabels(fields, labels);
        List<String> lines = new ArrayList<>();
        for (CertificateAttribute field : new LinkedHashSet<>(fields)) {
            String value = field == null ? null : read(certificate, field, zone);
            if (StringUtils.isBlank(value)) {
                continue;
            }
            String label = labels == null ? null : labels.get(fields.indexOf(field));
            lines.add(
                    (StringUtils.isBlank(label) ? defaultLabel(field) : label.strip())
                            + " "
                            + value.strip());
        }
        return lines;
    }

    private static void checkLabels(List<CertificateAttribute> fields, List<String> labels) {
        if (labels == null) {
            return;
        }
        if (labels.size() != fields.size()) {
            throw invalid("certificateFieldLabels - give one per entry of certificateFields");
        }
        if (labels.stream().anyMatch(label -> label != null && label.length() > MAX_LABEL_LENGTH)) {
            throw invalid("certificateFieldLabels - at most 200 characters each");
        }
    }

    private static String read(
            X509Certificate certificate, CertificateAttribute field, ZoneId zone) {
        X500Principal subject = certificate.getSubjectX500Principal();
        X500Principal issuer = certificate.getIssuerX500Principal();
        return switch (field) {
            case SUBJECT_ORGANISATION -> value(subject, BCStyle.O);
            case SUBJECT_ORGANISATIONAL_UNIT -> value(subject, BCStyle.OU);
            case SUBJECT_COUNTRY -> value(subject, BCStyle.C);
            case SUBJECT_EMAIL -> value(subject, BCStyle.E);
            case ISSUER_COMMON_NAME -> value(issuer, BCStyle.CN);
            case ISSUER_ORGANISATION -> value(issuer, BCStyle.O);
            case SERIAL_NUMBER -> certificate.getSerialNumber().toString(16);
            case VALID_FROM -> day(certificate.getNotBefore(), zone);
            case VALID_UNTIL -> day(certificate.getNotAfter(), zone);
            case SIGNATURE_ALGORITHM -> certificate.getSigAlgName();
        };
    }

    private static String defaultLabel(CertificateAttribute field) {
        return switch (field) {
            case SUBJECT_ORGANISATION -> "Organisation:";
            case SUBJECT_ORGANISATIONAL_UNIT -> "Unit:";
            case SUBJECT_COUNTRY -> "Country:";
            case SUBJECT_EMAIL -> "Email:";
            case ISSUER_COMMON_NAME -> "Issued by:";
            case ISSUER_ORGANISATION -> "Issuer organisation:";
            case SERIAL_NUMBER -> "Serial:";
            case VALID_FROM -> "Valid from:";
            case VALID_UNTIL -> "Valid until:";
            case SIGNATURE_ALGORITHM -> "Algorithm:";
        };
    }

    /**
     * The value of {@code type} as written: a printed name escapes commas and the like, and the
     * escapes would be drawn into the signature. The attribute may share its RDN with others, as in
     * {@code SERIALNUMBER=...+CN=...}. When the type repeats, the one taken is the last in the
     * encoding, which is the one RFC 4514 prints first.
     */
    private static String value(X500Principal principal, ASN1ObjectIdentifier type) {
        RDN[] rdns = X500Name.getInstance(principal.getEncoded()).getRDNs(type);
        for (int index = rdns.length - 1; index >= 0; index--) {
            for (AttributeTypeAndValue attribute : rdns[index].getTypesAndValues()) {
                if (type.equals(attribute.getType())) {
                    return attribute.getValue() instanceof ASN1String text
                            ? text.getString()
                            : IETFUtils.valueToString(attribute.getValue());
                }
            }
        }
        return null;
    }

    private static String day(Date date, ZoneId zone) {
        return DAY.format(date.toInstant().atZone(zone));
    }

    private static IllegalArgumentException invalid(String detail) {
        return ExceptionUtils.createIllegalArgumentException(
                "error.invalidArgument", "Invalid argument: {0}", detail);
    }
}
