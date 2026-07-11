import { useState } from 'react';
import { Button } from 'antd';
import { UploadOutlined } from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import { ImportWizardModal } from './ImportWizardModal';
import { taskKeys } from './taskQueries';

type Props = {
  /** 预选导入实体（如 sys.dept / sys.member / sys.dict）。 */
  entityCode: string;
  /** 按钮文案，默认「导入」。 */
  label?: string;
  size?: 'small' | 'middle' | 'large';
  /** 是否占满整行宽度（与「新增」按钮堆叠时保持一致）。 */
  block?: boolean;
  /** 导入完成后回调，列表页据此刷新自身数据。 */
  onImported?: () => void;
};

/**
 * 列表页「导入」入口：点开即进数据导入向导并预选好对象。向导按需挂载（每次打开都是
 * 新实例，预选实体经懒初始化生效），关闭即卸载。
 */
export function ImportEntryButton({
  entityCode,
  label = '导入',
  size = 'small',
  block = false,
  onImported,
}: Props) {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button size={size} block={block} icon={<UploadOutlined />} onClick={() => setOpen(true)}>
        {label}
      </Button>
      {open && (
        <ImportWizardModal
          open
          initialEntityCode={entityCode}
          onClose={() => setOpen(false)}
          onDone={() => {
            void qc.invalidateQueries({ queryKey: taskKeys.all });
            onImported?.();
          }}
        />
      )}
    </>
  );
}
