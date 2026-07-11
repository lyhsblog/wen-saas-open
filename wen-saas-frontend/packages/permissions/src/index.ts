export {
  type Capabilities,
  type MenuNode,
  emptyCapabilities,
  useCapabilitiesStore,
  useCapabilities,
  useCapabilitiesLoading,
  useCapabilitiesError,
  useHasPermission,
  useHasModule,
  useMenus,
  useIsReadonly,
  useImpersonation,
  useScreenshotWatermark,
  loadCapabilities,
} from './capabilities';
export { WatermarkLayer, type WatermarkLayerProps } from './WatermarkLayer';
export { PermissionGate, type PermissionGateProps } from './PermissionGate';
export { PackageGate, type PackageGateProps } from './PackageGate';
export { ReadonlyBanner, type ReadonlyBannerProps } from './ReadonlyBanner';
export { ReadonlyButton, type ReadonlyButtonProps } from './ReadonlyButton';
