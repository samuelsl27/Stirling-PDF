import i18n, { type TFunction } from "i18next";
import { useTranslation } from "react-i18next";
import {
  useToolOperation,
  defineSingleFileTool,
} from "@editor/hooks/tools/shared/useToolOperation";
import {
  objectToFormData,
  type FormDataFiles,
  type ToolApiParams,
  type ToolEndpoint,
} from "@editor/hooks/tools/shared/toolApiMapping";
import { createStandardErrorHandler } from "@editor/utils/toolErrorHandler";
import type { StampLabels } from "@editor/services/signatureStampPreview";
import { DEVICE_LOCAL_REQUEST } from "@editor/constants/deviceLocalEndpoints";
import {
  CERTIFICATE_FIELD_LABELS,
  type CertificateField,
  type GraphicPosition,
} from "@editor/constants/certSignConstants";
import {
  validateCertSignParameters,
  CertSignParameters,
  defaultParameters,
} from "@editor/hooks/tools/certSign/useCertSignParameters";

const ENDPOINT = "/api/v1/security/cert-sign" satisfies ToolEndpoint;
type CertSignApiParams = ToolApiParams[typeof ENDPOINT];

// Convert the tool's UI parameters into the cert-sign request body. The keystore
// uploads (privateKeyFile, certFile, p12File, jksFile) are actual File uploads
// and are appended separately (see buildCertSignFormData); only the scalar
// fields are serialized here.
export const certSignToApiParams = (
  parameters: CertSignParameters,
): CertSignApiParams => {
  // AUTO mode signs with the server certificate; no keystore/password is sent.
  if (parameters.signMode === "AUTO") {
    return withSignatureAppearance({ certType: "SERVER" }, parameters);
  }

  const apiParams: CertSignApiParams = {
    certType: parameters.certType as CertSignApiParams["certType"],
    password: parameters.password,
  };

  // Non-file identifiers depend on the chosen certificate type.
  switch (parameters.certType) {
    case "WINDOWS_STORE":
    case "MACOS_KEYCHAIN":
      if (parameters.alias) apiParams.alias = parameters.alias;
      break;
    case "PKCS11":
      if (parameters.pkcs11LibraryPath) {
        apiParams.pkcs11LibraryPath = parameters.pkcs11LibraryPath;
      }
      if (parameters.pkcs11Slot != null) {
        apiParams.pkcs11Slot = parameters.pkcs11Slot;
      }
      if (parameters.alias) apiParams.alias = parameters.alias;
      break;
  }

  return withSignatureAppearance(apiParams, parameters);
};

// Name, reason and location go into the signature dictionary, so they are sent
// for invisible signatures too; placement and logo only affect the visible stamp.
const withSignatureAppearance = (
  apiParams: CertSignApiParams,
  parameters: CertSignParameters,
): CertSignApiParams => {
  if (parameters.reason) apiParams.reason = parameters.reason;
  if (parameters.location) apiParams.location = parameters.location;
  if (parameters.name) apiParams.name = parameters.name;
  if (parameters.showSignature) {
    apiParams.showSignature = true;
    apiParams.stampGraphic = parameters.stampGraphic;
    apiParams.showWatermark = parameters.showWatermark;
    if (parameters.showCertificateDetails) {
      apiParams.showCertificateDetails = true;
    }
    const position = sentGraphicPosition(parameters);
    if (position) apiParams.graphicPosition = position;
    if (parameters.certificateFields?.length) {
      apiParams.certificateFields = parameters.certificateFields;
    }
    if (parameters.markAllPages) apiParams.markAllPages = true;
    if (parameters.fieldName) {
      apiParams.fieldName = parameters.fieldName;
    } else {
      apiParams.pageNumber = parameters.pageNumber;
      if (parameters.stampBox) {
        apiParams.x = parameters.stampBox.x;
        apiParams.y = parameters.stampBox.y;
        apiParams.width = parameters.stampBox.width;
        apiParams.height = parameters.stampBox.height;
      }
    }
  }
  return apiParams;
};

/** The position to ask for, or nothing when the server's own choice applies. */
export const sentGraphicPosition = ({
  stampGraphic,
  graphicPosition,
}: CertSignParameters): GraphicPosition | undefined => {
  if (stampGraphic === "NONE" || graphicPosition === "AUTO") return undefined;
  // The server rejects BEHIND without an image to put there.
  if (graphicPosition === "BEHIND" && stampGraphic !== "IMAGE") {
    return undefined;
  }
  return graphicPosition;
};

// Select the keystore File uploads for the chosen certificate type. AUTO mode
// (server certificate) uploads no keystore.
const certSignFiles = (parameters: CertSignParameters): FormDataFiles => ({
  ...keystoreFiles(parameters),
  ...(parameters.showSignature && parameters.stampGraphic === "IMAGE"
    ? { signatureImage: parameters.signatureImage }
    : {}),
});

const keystoreFiles = (parameters: CertSignParameters): FormDataFiles => {
  if (parameters.signMode === "AUTO") return {};

  switch (parameters.certType) {
    case "PEM":
      return {
        privateKeyFile: parameters.privateKeyFile,
        certFile: parameters.certFile,
      };
    case "PKCS12":
    case "PFX":
      return { p12File: parameters.p12File };
    case "JKS":
      return { jksFile: parameters.jksFile };
    default:
      return {};
  }
};

const signModeForCertType = (
  certType: CertSignApiParams["certType"],
): CertSignParameters["signMode"] => {
  switch (certType) {
    case "SERVER":
      return "AUTO";
    case "WINDOWS_STORE":
    case "MACOS_KEYCHAIN":
    case "PKCS11":
      return "DEVICE";
    default:
      return "MANUAL";
  }
};

// Reconstruct the tool's UI parameters from a cert-sign request body, so a stored
// or AI-authored step can be re-rendered in the settings UI. Uploaded keystore
// files cannot be recovered from the request model.
export const certSignFromApiParams = (
  apiParams: CertSignApiParams,
): Partial<CertSignParameters> => {
  const result: Partial<CertSignParameters> = {
    signMode: signModeForCertType(apiParams.certType),
    showSignature: apiParams.showSignature ?? defaultParameters.showSignature,
  };

  if (apiParams.certType !== "SERVER") {
    result.certType = apiParams.certType;
    result.password = apiParams.password ?? defaultParameters.password;
  }
  if (apiParams.alias !== undefined) result.alias = apiParams.alias;
  if (apiParams.pkcs11LibraryPath !== undefined) {
    result.pkcs11LibraryPath = apiParams.pkcs11LibraryPath;
  }
  if (apiParams.pkcs11Slot !== undefined) {
    result.pkcs11Slot = apiParams.pkcs11Slot;
  }
  if (apiParams.reason !== undefined) result.reason = apiParams.reason;
  if (apiParams.location !== undefined) result.location = apiParams.location;
  if (apiParams.name !== undefined) result.name = apiParams.name;
  if (apiParams.pageNumber !== undefined) {
    result.pageNumber = apiParams.pageNumber;
  }
  // An image graphic cannot be restored without its file, so it comes back as the name.
  if (apiParams.stampGraphic === "NONE") result.stampGraphic = "NONE";
  if (apiParams.showWatermark !== undefined) {
    result.showWatermark = apiParams.showWatermark;
  } else if (apiParams.showLogo !== undefined) {
    result.showWatermark = apiParams.showLogo;
  }
  if (apiParams.showCertificateDetails !== undefined) {
    result.showCertificateDetails = apiParams.showCertificateDetails;
  }
  if (apiParams.fieldName !== undefined) result.fieldName = apiParams.fieldName;
  if (apiParams.graphicPosition !== undefined) {
    result.graphicPosition = apiParams.graphicPosition;
  }
  if (apiParams.certificateFields !== undefined) {
    result.certificateFields = apiParams.certificateFields;
  }
  if (apiParams.markAllPages !== undefined) {
    result.markAllPages = apiParams.markAllPages;
  }
  if (
    apiParams.x !== undefined &&
    apiParams.y !== undefined &&
    apiParams.width !== undefined &&
    apiParams.height !== undefined
  ) {
    result.stampBox = {
      x: apiParams.x,
      y: apiParams.y,
      width: apiParams.width,
      height: apiParams.height,
    };
  }

  return result;
};

/** The stamp's labels in the UI language; the signed-by one keeps the {name} slot the server fills. */
export const stampLabels = (t: TFunction = i18n.t): StampLabels => ({
  signedByLabel: t("certSign.stamp.signedBy", "Digitally signed by {{name}}", {
    name: "{name}",
    interpolation: { escapeValue: false },
  }),
  dateLabel: t("certSign.stamp.date", "Date:"),
  reasonLabel: t("certSign.stamp.reason", "Reason:"),
  locationLabel: t("certSign.stamp.location", "Location:"),
});

/** The labels of the chosen certificate fields in the UI language, in the same order. */
export const certificateFieldLabels = (
  fields: CertificateField[],
  t: TFunction = i18n.t,
): string[] =>
  fields.map((field) => {
    const { key, english } = CERTIFICATE_FIELD_LABELS[field];
    return t(key, english);
  });

// The stamp's words are fixed when signing, in the signer's language and time zone, so they
// are added here rather than stored with the step: a saved automation signs in whoever runs it.
const withStampLanguage = (
  apiParams: CertSignApiParams,
  parameters: CertSignParameters,
): CertSignApiParams =>
  parameters.showSignature
    ? {
        ...apiParams,
        timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        ...stampLabels(),
        ...(parameters.certificateFields?.length
          ? {
              certificateFieldLabels: certificateFieldLabels(
                parameters.certificateFields,
              ),
            }
          : {}),
      }
    : apiParams;

// What the user reviewed belongs to this signing only: a saved step that pinned a fingerprint
// would fail once the server certificate is regenerated, so it is never stored with the step.
const withReviewedSigner = (
  apiParams: CertSignApiParams,
  parameters: CertSignParameters,
): CertSignApiParams => ({
  ...apiParams,
  ...(parameters.signMode !== "MANUAL" && parameters.expectedCertificateSha256
    ? { expectedCertificateSha256: parameters.expectedCertificateSha256 }
    : {}),
  ...(parameters.signMode === "DEVICE" &&
  parameters.certType === "MACOS_KEYCHAIN" &&
  parameters.requireToken
    ? { requireToken: true }
    : {}),
});

export const buildCertSignFormData = (
  parameters: CertSignParameters,
  file: File,
): FormData =>
  objectToFormData(
    withReviewedSigner(
      withStampLanguage(certSignToApiParams(parameters), parameters),
      parameters,
    ),
    {
      fileInput: file,
      ...certSignFiles(parameters),
    },
  );

// The path is the same whether the key is an uploaded file or lives on this machine, so only
// the certificate type says that a run must not be replayed or sent to a server.
const certSignRequestConfig = (parameters: CertSignParameters) =>
  parameters.signMode !== "AUTO" &&
  (parameters.certType === "WINDOWS_STORE" ||
    parameters.certType === "MACOS_KEYCHAIN" ||
    parameters.certType === "PKCS11")
    ? DEVICE_LOCAL_REQUEST
    : {};

// Static configuration object
export const certSignOperationConfig = defineSingleFileTool({
  validateParams: validateCertSignParameters,
  buildFormData: buildCertSignFormData,
  toApiParams: certSignToApiParams,
  fromApiParams: certSignFromApiParams,
  operationType: "certSign",
  endpoint: ENDPOINT,
  requestConfig: certSignRequestConfig,
  defaultParameters,
});

export const useCertSignOperation = () => {
  const { t } = useTranslation();

  return useToolOperation<CertSignParameters>({
    ...certSignOperationConfig,
    getErrorMessage: createStandardErrorHandler(
      t(
        "certSign.error.failed",
        "An error occurred while processing signatures.",
      ),
    ),
  });
};
