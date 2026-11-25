/**
 * Axios config flag for a request that only means something on the user's own machine, such as
 * signing with a key held by a token plugged into it.
 *
 * Signing posts to `/api/v1/security/cert-sign` whether the key came from an uploaded keystore or
 * from the device, so the path alone cannot say which it is; only the chosen certificate type can.
 * Routing is decided from that type (`requiresBundledBackend`). The flag is for the code that must
 * not replay such a run or hand it to a server: the notification retry and server automations.
 */
export type DeviceLocalRequestConfig = {
  deviceLocal?: boolean;
};

/** Config marking a request as one that must run on the user's own machine. */
export const DEVICE_LOCAL_REQUEST: DeviceLocalRequestConfig = {
  deviceLocal: true,
};
