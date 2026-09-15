import { act, render } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

const { apiGetMock } = vi.hoisted(() => ({ apiGetMock: vi.fn() }));

vi.mock("@app/services/apiClient", () => ({
  default: { get: apiGetMock },
}));

import { SidebarProcessingSlot } from "@app/components/shared/SidebarProcessingSlot";

describe("SidebarProcessingSlot (desktop)", () => {
  it.each([false, true])(
    "renders nothing and asks no server for Downloads (collapsed=%s)",
    async (collapsed) => {
      const { container } = render(
        <SidebarProcessingSlot collapsed={collapsed} />,
      );
      await act(async () => {});
      expect(container.innerHTML).toBe("");
      expect(apiGetMock).not.toHaveBeenCalled();
    },
  );
});
