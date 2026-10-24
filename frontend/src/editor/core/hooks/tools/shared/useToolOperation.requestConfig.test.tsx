import React from "react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { act, renderHook } from "@testing-library/react";
import { MantineProvider } from "@mantine/core";
import { I18nextProvider } from "react-i18next";
import i18n from "@editor/i18n/config";
import { useToolOperation } from "@editor/hooks/tools/shared/useToolOperation";
import { mergeOperationConfig } from "@editor/hooks/tools/merge/useMergeOperation";
import { defaultParameters as mergeParameters } from "@editor/hooks/tools/merge/useMergeParameters";
import { DEVICE_LOCAL_REQUEST } from "@editor/constants/deviceLocalEndpoints";
import {
  FileContextProvider,
  useFileActions,
} from "@editor/contexts/FileContext";
import { NavigationProvider } from "@editor/contexts/NavigationContext";
import { ToolRegistryProvider } from "@editor/contexts/ToolRegistryProvider";
import { PreferencesProvider } from "@editor/contexts/PreferencesContext";
import { createTestStirlingFile } from "@editor/tests/utils/testFileHelpers";
import { createNewStirlingFileStub } from "@editor/types/fileContext";

const mocks = vi.hoisted(() => ({ post: vi.fn(), stash: vi.fn() }));

vi.mock("@editor/services/apiClient", () => ({
  default: {
    post: mocks.post,
    get: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    interceptors: { response: { use: vi.fn() } },
  },
}));

vi.mock("@editor/services/fileStorage", () => ({
  onRecordUnreadable: () => () => {},
  fileStorage: {
    init: vi.fn().mockResolvedValue(undefined),
    storeStirlingFile: vi.fn().mockResolvedValue(undefined),
    persistVersionedOutputs: vi.fn().mockResolvedValue(undefined),
    getAllFileMetadata: vi.fn().mockResolvedValue([]),
    cleanup: vi.fn().mockResolvedValue(undefined),
  },
}));

vi.mock("@editor/services/thumbnailGenerationService", () => ({
  thumbnailGenerationService: {
    generateThumbnail: vi.fn().mockResolvedValue("data:image/png;base64,x"),
    cleanup: vi.fn(),
    destroy: vi.fn(),
  },
}));

// A build with a bell, which is the only kind that stashes a failure for retrying.
vi.mock("@editor/components/notifications/useNotificationsAvailable", () => ({
  useNotificationsAvailable: () => true,
}));

vi.mock("@editor/services/notificationRetry", async (importOriginal) => ({
  ...(await importOriginal<
    typeof import("@editor/services/notificationRetry")
  >()),
  stashRetryPayload: mocks.stash,
}));

vi.mock("@editor/services/failureReporting", async (importOriginal) => ({
  ...(await importOriginal<
    typeof import("@editor/services/failureReporting")
  >()),
  reportToolFailure: vi.fn().mockResolvedValue(undefined),
  errorCodeOf: vi.fn().mockResolvedValue(null),
}));

const TestWrapper = ({ children }: { children: React.ReactNode }) => (
  <MantineProvider>
    <I18nextProvider i18n={i18n}>
      <PreferencesProvider>
        <ToolRegistryProvider>
          <NavigationProvider>
            <FileContextProvider>{children}</FileContextProvider>
          </NavigationProvider>
        </ToolRegistryProvider>
      </PreferencesProvider>
    </I18nextProvider>
  </MantineProvider>
);

/** A batch tool marked the way certificate signing marks a key held on this machine. */
const deviceLocalMerge = {
  ...mergeOperationConfig,
  requestConfig: () => DEVICE_LOCAL_REQUEST,
};

/** Runs the batch against a backend that refuses it. */
async function runFailingBatch(): Promise<void> {
  const files = ["a.pdf", "b.pdf"].map((name) =>
    createTestStirlingFile(name, "%PDF-1.4", "application/pdf"),
  );
  const { result } = renderHook(
    () => ({
      operation: useToolOperation(deviceLocalMerge),
      actions: useFileActions(),
    }),
    { wrapper: TestWrapper },
  );
  await act(async () => {
    result.current.actions.dispatch({
      type: "ADD_FILES",
      payload: {
        stirlingFileStubs: files.map((file) =>
          createNewStirlingFileStub(file, file.fileId),
        ),
      },
    });
  });
  await act(async () => {
    await result.current.operation.executeOperation(mergeParameters, files);
  });
}

describe("a batch run that must stay on this machine", () => {
  beforeEach(() => {
    mocks.post.mockReset().mockRejectedValue(new Error("backend refused"));
    mocks.stash.mockReset().mockResolvedValue(undefined);
  });

  test("asks for it on the one request the batch makes", async () => {
    await runFailingBatch();

    expect(mocks.post).toHaveBeenCalledOnce();
    expect(mocks.post.mock.calls[0][2]).toMatchObject({
      deviceLocal: true,
      responseType: "blob",
    });
  });

  test("is stashed for the bell as device-local", async () => {
    await runFailingBatch();

    await vi.waitFor(() =>
      expect(mocks.stash).toHaveBeenCalledWith(
        expect.objectContaining({ multiFile: true, deviceLocal: true }),
      ),
    );
  });
});
