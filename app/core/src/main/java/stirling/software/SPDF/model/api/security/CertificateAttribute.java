package stirling.software.SPDF.model.api.security;

/**
 * A field of the signing certificate that a visible signature can add to its details. The signer's
 * name, the date, the reason and the location are not listed: the stamp already writes them.
 *
 * <p>The identifiers are part of the API, so renaming one is a breaking change.
 */
public enum CertificateAttribute {
    SUBJECT_ORGANISATION,
    SUBJECT_ORGANISATIONAL_UNIT,
    SUBJECT_COUNTRY,
    SUBJECT_EMAIL,
    ISSUER_COMMON_NAME,
    ISSUER_ORGANISATION,
    SERIAL_NUMBER,
    VALID_FROM,
    VALID_UNTIL,
    SIGNATURE_ALGORITHM
}
