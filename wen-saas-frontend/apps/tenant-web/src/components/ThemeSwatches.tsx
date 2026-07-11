import { useState } from 'react';
import { Popover } from 'antd';
import { BgColorsOutlined, CheckOutlined } from '@ant-design/icons';
import { BRAND_KEYS, BRAND_MAP, type Brand } from '@wen-saas/design-system';
import { useUiPrefStore } from '../lib/uiPref';

const BRAND_LABEL: Record<Brand, string> = {
  blue: '蓝',
  indigo: '靛蓝',
  teal: '青绿',
  slate: '石板',
};

/** 主题色取色器：4 色板，点选即生效并持久化到本设备（uiPref store），语义色不跟随。 */
export function ThemeSwatches() {
  const brand = useUiPrefStore((s) => s.brand);
  const setBrand = useUiPrefStore((s) => s.setBrand);
  return (
    <div style={{ display: 'flex', gap: 14 }}>
      {BRAND_KEYS.map((b) => {
        const color = BRAND_MAP[b].primary;
        const selected = b === brand;
        return (
          <button
            key={b}
            type="button"
            title={BRAND_LABEL[b]}
            onClick={() => setBrand(b)}
            style={{
              border: 'none',
              background: 'transparent',
              padding: 0,
              cursor: 'pointer',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: 6,
              fontFamily: 'inherit',
            }}
          >
            <span
              style={{
                display: 'inline-flex',
                width: 40,
                height: 40,
                borderRadius: '50%',
                alignItems: 'center',
                justifyContent: 'center',
                boxShadow: selected ? `inset 0 0 0 2px #fff, 0 0 0 2px ${color}` : 'none',
                transition: 'transform .12s, box-shadow .12s',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.transform = 'scale(1.08)';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.transform = 'none';
              }}
            >
              <span
                style={{
                  width: 30,
                  height: 30,
                  borderRadius: '50%',
                  background: color,
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  boxShadow: selected ? 'none' : '0 1px 2px rgba(15,23,42,0.15)',
                }}
              >
                {selected && <CheckOutlined style={{ color: '#fff', fontSize: 14 }} />}
              </span>
            </span>
            <span
              style={{
                fontSize: 12,
                fontWeight: selected ? 600 : 400,
                color: selected ? 'var(--text)' : 'var(--text-tertiary)',
              }}
            >
              {BRAND_LABEL[b]}
            </span>
          </button>
        );
      })}
    </div>
  );
}

/**
 * 顶部栏调色盘快捷入口：32px 图标钮 + 点击浮层取色，一步换色（评审第二批 H1）。
 * 浮层打开时图标用品牌浅底激活态，与页签活跃态同视觉语言。
 */
export function ThemePaletteButton() {
  const [open, setOpen] = useState(false);
  const brand = useUiPrefStore((s) => s.brand);
  const setBrand = useUiPrefStore((s) => s.setBrand);
  return (
    <Popover
      trigger="click"
      placement="bottomRight"
      open={open}
      onOpenChange={setOpen}
      content={
        <div style={{ width: 232 }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: 10,
            }}
          >
            <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-secondary)' }}>
              主题色
            </span>
            <button
              type="button"
              disabled={brand === 'blue'}
              onClick={() => setBrand('blue')}
              style={{
                border: 'none',
                background: 'transparent',
                padding: 0,
                fontSize: 12,
                fontFamily: 'inherit',
                cursor: brand === 'blue' ? 'default' : 'pointer',
                color: brand === 'blue' ? 'var(--text-disabled)' : 'var(--brand)',
              }}
            >
              恢复默认
            </button>
          </div>
          <ThemeSwatches />
          <div style={{ fontSize: 11, color: 'var(--text-tertiary)', marginTop: 10 }}>
            即时生效，仅影响本设备；状态色不跟随
          </div>
        </div>
      }
    >
      <button
        type="button"
        title="主题色"
        style={{
          width: 32,
          height: 32,
          border: 'none',
          borderRadius: 'var(--r-md)',
          cursor: 'pointer',
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: open ? 'var(--brand)' : 'var(--text-secondary)',
          background: open ? 'var(--brand-bg)' : 'transparent',
          transition: 'background .15s, color .15s',
        }}
        onMouseEnter={(e) => {
          if (!open) e.currentTarget.style.background = 'var(--bg-muted)';
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.background = open ? 'var(--brand-bg)' : 'transparent';
        }}
      >
        <BgColorsOutlined style={{ fontSize: 16 }} />
      </button>
    </Popover>
  );
}
