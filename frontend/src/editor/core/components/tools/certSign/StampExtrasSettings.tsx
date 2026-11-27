import { useState } from "react";
import { Checkbox, Stack, Text } from "@mantine/core";
import { useTranslation } from "react-i18next";
import type { CertSignParameters } from "@editor/hooks/tools/certSign/useCertSignParameters";
import {
  CERTIFICATE_FIELDS,
  CERTIFICATE_FIELD_LABELS,
  type CertificateField,
  type GraphicPosition,
} from "@editor/constants/certSignConstants";
import { certificateFieldLabels } from "@editor/hooks/tools/certSign/useCertSignOperation";
import { Button } from "@editor/ui/Button";
import { Icon } from "@editor/ui/Icon";
import { Select } from "@editor/ui/Select";
import { ToggleSwitch } from "@editor/ui/ToggleSwitch";

interface SectionProps {
  parameters: CertSignParameters;
  onParameterChange: <K extends keyof CertSignParameters>(
    key: K,
    value: CertSignParameters[K],
  ) => void;
  disabled: boolean;
}

/** Which side of the box the name or image takes. */
export const GraphicPositionChoice = ({
  parameters,
  onParameterChange,
  disabled,
}: SectionProps) => {
  const { t } = useTranslation();
  if (parameters.stampGraphic === "NONE") return null;

  const options: { value: GraphicPosition; label: string }[] = [
    {
      value: "AUTO",
      label: t("certSign.appearance.position.auto", "Automatic"),
    },
    { value: "LEFT", label: t("certSign.appearance.position.left", "Left") },
    { value: "RIGHT", label: t("certSign.appearance.position.right", "Right") },
    { value: "TOP", label: t("certSign.appearance.position.top", "Above") },
    {
      value: "BOTTOM",
      label: t("certSign.appearance.position.bottom", "Below"),
    },
    ...(parameters.stampGraphic === "IMAGE"
      ? [
          {
            value: "BEHIND" as const,
            label: t("certSign.appearance.position.behind", "Behind the text"),
          },
        ]
      : []),
  ];
  const chosen = parameters.graphicPosition ?? "AUTO";
  const title = t("certSign.appearance.position.title", "Position");

  return (
    <Stack gap={4}>
      <Text size="sm" fw={500}>
        {title}
      </Text>
      <Select
        aria-label={title}
        options={options}
        // BEHIND stays chosen while the name shows, so the image gets it back.
        value={
          options.some((option) => option.value === chosen) ? chosen : "AUTO"
        }
        onChange={(value) =>
          onParameterChange("graphicPosition", value as GraphicPosition)
        }
        allowDeselect={false}
        disabled={disabled}
      />
    </Stack>
  );
};

/** Certificate fields to add under the signer, folded away until asked for. */
export const CertificateFieldChoice = ({
  parameters,
  onParameterChange,
  disabled,
}: SectionProps) => {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const selected = parameters.certificateFields ?? [];
  const labels = certificateFieldLabels([...CERTIFICATE_FIELDS], t);

  const toggle = (field: CertificateField, checked: boolean) =>
    onParameterChange(
      "certificateFields",
      // The order is the drawing order, so a newly ticked field goes last.
      checked
        ? [...selected, field]
        : selected.filter((other) => other !== field),
    );

  return (
    <Stack gap="xs">
      <Button
        variant="tertiary"
        accent="neutral"
        leftSection={
          <Icon name={open ? "chevron-up" : "chevron-down"} size={14} />
        }
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
        disabled={disabled}
      >
        {t("certSign.appearance.fields.title", "Certificate fields")} (
        {selected.length})
      </Button>
      {/* Unmounted when folded, so hidden boxes never take keyboard focus. */}
      {open && (
        <Stack gap={6}>
          <Text size="xs" c="dimmed">
            {t(
              "certSign.appearance.fields.hint",
              "Fields your certificate does not contain are left out.",
            )}
          </Text>
          {CERTIFICATE_FIELDS.map((field, index) => (
            <Checkbox
              key={CERTIFICATE_FIELD_LABELS[field].key}
              size="xs"
              label={labels[index].replace(/\s*[:：]\s*$/, "")}
              checked={selected.includes(field)}
              onChange={(event) => toggle(field, event.currentTarget.checked)}
              disabled={disabled}
            />
          ))}
        </Stack>
      )}
    </Stack>
  );
};

/** Repeats the stamp's look on the other pages, which signs nothing there. */
export const RepeatOnPagesChoice = ({
  parameters,
  onParameterChange,
  disabled,
}: SectionProps) => {
  const { t } = useTranslation();
  return (
    <Stack gap={4}>
      <ToggleSwitch
        checked={parameters.markAllPages ?? false}
        onChange={(checked) => onParameterChange("markAllPages", checked)}
        label={t("certSign.appearance.repeat.label", "Repeat on every page")}
        disabled={disabled}
      />
      {parameters.markAllPages && (
        <Text size="xs" c="dimmed">
          {t(
            "certSign.appearance.repeat.hint",
            "Only one page carries the signature. The others get a copy of its look, which is not a signature. A document that is already signed gets no copies.",
          )}
        </Text>
      )}
    </Stack>
  );
};
