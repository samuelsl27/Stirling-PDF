import { describe, expect, it } from "vitest";
import {
  buildCertSignFormData,
  certSignFromApiParams,
  certSignOperationConfig,
  certSignToApiParams,
} from "@editor/hooks/tools/certSign/useCertSignOperation";
import {
  defaultParameters,
  type CertSignParameters,
} from "@editor/hooks/tools/certSign/useCertSignParameters";
import {
  layoutStamp,
  stampDetails,
} from "@editor/services/signatureStampPreview";

const visible: CertSignParameters = {
  ...defaultParameters,
  signMode: "AUTO",
  showSignature: true,
};

describe("certificate signature extras", () => {
  it("sends nothing new while the extras are untouched", () => {
    const sent = certSignToApiParams(visible);

    expect(sent).not.toHaveProperty("graphicPosition");
    expect(sent).not.toHaveProperty("certificateFields");
    expect(sent).not.toHaveProperty("markAllPages");
  });

  it("survives the round trip through a stored step", () => {
    const parameters: CertSignParameters = {
      ...visible,
      graphicPosition: "RIGHT",
      certificateFields: ["SERIAL_NUMBER", "SUBJECT_ORGANISATION"],
      markAllPages: true,
    };

    const restored = certSignFromApiParams(certSignToApiParams(parameters));

    expect(restored.graphicPosition).toBe("RIGHT");
    expect(restored.certificateFields).toEqual(parameters.certificateFields);
    expect(restored.markAllPages).toBe(true);
  });

  it.each([
    { stampGraphic: "NAME", graphicPosition: "BEHIND" },
    { stampGraphic: "NONE", graphicPosition: "LEFT" },
  ] as const)(
    "drops $graphicPosition when the graphic is $stampGraphic",
    (choice) => {
      expect(certSignToApiParams({ ...visible, ...choice })).not.toHaveProperty(
        "graphicPosition",
      );
    },
  );

  it("adds one label per chosen field only when signing", () => {
    const parameters: CertSignParameters = {
      ...visible,
      certificateFields: ["SERIAL_NUMBER", "VALID_UNTIL"],
    };

    const form = buildCertSignFormData(parameters, new File([], "a.pdf"));

    expect(form.getAll("certificateFields")).toEqual([
      "SERIAL_NUMBER",
      "VALID_UNTIL",
    ]);
    expect(form.getAll("certificateFieldLabels")).toHaveLength(2);
    expect(certSignToApiParams(parameters)).not.toHaveProperty(
      "certificateFieldLabels",
    );
  });

  it.each(["WINDOWS_STORE", "MACOS_KEYCHAIN", "PKCS11"] as const)(
    "keeps a %s run on this machine",
    (certType) => {
      expect(
        certSignOperationConfig.requestConfig?.({
          ...defaultParameters,
          signMode: "DEVICE",
          certType,
        }),
      ).toEqual({ deviceLocal: true });
    },
  );

  it.each([
    { signMode: "MANUAL", certType: "PFX" },
    { signMode: "AUTO", certType: "PKCS11" },
  ] as const)("lets a $signMode $certType run go anywhere", (choice) => {
    expect(
      certSignOperationConfig.requestConfig?.({
        ...defaultParameters,
        ...choice,
      }),
    ).toEqual({});
  });

  it("puts the graphic on the chosen side of the preview", () => {
    const measure = (line: string) => line.length * 0.5;
    const place = (position: "LEFT" | "RIGHT" | "TOP" | "BOTTOM") =>
      layoutStamp(200, 100, null, 1, ["Ada"], measure, position);

    expect(place("LEFT").logo!.x).toBeLessThan(place("RIGHT").logo!.x);
    expect(place("TOP").logo!.y).toBeGreaterThan(place("BOTTOM").logo!.y);
  });

  it("writes the certificate fields under the signer, before the reason", () => {
    const lines = stampDetails(
      {
        signedByLabel: "By {name}",
        dateLabel: "Date:",
        reasonLabel: "Reason:",
        locationLabel: "Location:",
      },
      {
        name: "Ada",
        date: new Date(0),
        timeZone: "UTC",
        reason: "Approval",
        location: "",
        fields: ["Serial: 1"],
      },
    );

    expect(lines.slice(0, 3)).toEqual([
      "By Ada",
      "Serial: 1",
      "Reason: Approval",
    ]);
  });
});
