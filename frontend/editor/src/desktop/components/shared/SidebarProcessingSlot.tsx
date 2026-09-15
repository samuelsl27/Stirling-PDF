import { type SidebarProcessingSlotProps } from "@core/components/shared/SidebarProcessingSlot";
export { type SidebarProcessingSlotProps };

/**
 * Desktop: no Downloads offer. The proprietary wizard asks the server for its Downloads folder,
 * but the bundled backend is built without the proprietary controller and the cloud does not
 * serve it either, so every retry raised a "Cloud Processing Failed" toast. The server could
 * not see this machine's Downloads anyway; #7964 moves the lookup onto the OS.
 */
export function SidebarProcessingSlot(_props: SidebarProcessingSlotProps) {
  return null;
}
