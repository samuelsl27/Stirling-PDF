import type { SignPDFWithCertRequest } from "@editor/types/toolApiTypes";

export type CertificateField = NonNullable<
  SignPDFWithCertRequest["certificateFields"]
>[number];

export type GraphicPosition = NonNullable<
  SignPDFWithCertRequest["graphicPosition"]
>;

/**
 * The certificate fields a visible signature can add. The signer's name, the date, the reason and
 * the location are not here: the stamp already writes them.
 */
export const CERTIFICATE_FIELDS = [
  "SUBJECT_ORGANISATION",
  "SUBJECT_ORGANISATIONAL_UNIT",
  "SUBJECT_COUNTRY",
  "SUBJECT_EMAIL",
  "ISSUER_COMMON_NAME",
  "ISSUER_ORGANISATION",
  "SERIAL_NUMBER",
  "VALID_FROM",
  "VALID_UNTIL",
  "SIGNATURE_ALGORITHM",
] as const satisfies readonly CertificateField[];

/** Each field's translation key and English label, as the stamp writes it. */
export const CERTIFICATE_FIELD_LABELS: Record<
  CertificateField,
  { key: string; english: string }
> = {
  SUBJECT_ORGANISATION: {
    key: "certSign.stamp.organisation",
    english: "Organisation:",
  },
  SUBJECT_ORGANISATIONAL_UNIT: {
    key: "certSign.stamp.organisationalUnit",
    english: "Unit:",
  },
  SUBJECT_COUNTRY: { key: "certSign.stamp.country", english: "Country:" },
  SUBJECT_EMAIL: { key: "certSign.stamp.email", english: "Email:" },
  ISSUER_COMMON_NAME: { key: "certSign.stamp.issuedBy", english: "Issued by:" },
  ISSUER_ORGANISATION: {
    key: "certSign.stamp.issuerOrganisation",
    english: "Issuer organisation:",
  },
  SERIAL_NUMBER: { key: "certSign.stamp.serial", english: "Serial:" },
  VALID_FROM: { key: "certSign.stamp.validFrom", english: "Valid from:" },
  VALID_UNTIL: { key: "certSign.stamp.validUntil", english: "Valid until:" },
  SIGNATURE_ALGORITHM: {
    key: "certSign.stamp.algorithm",
    english: "Algorithm:",
  },
};
