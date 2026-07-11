import { Spin } from 'antd';

/** lazy 路由切换时的 fallback。撑满 main，避免 CLS。 */
export function RouteLoading() {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        height: '100%',
        minHeight: 240,
      }}
    >
      <Spin />
    </div>
  );
}
