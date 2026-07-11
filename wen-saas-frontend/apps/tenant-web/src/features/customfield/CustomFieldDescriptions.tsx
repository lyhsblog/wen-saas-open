import { Descriptions } from 'antd';
import { useCfFields } from './customFieldQueries';
import { renderCfValue } from './cfRender';

/** 详情页「扩展信息」区块（自定义字段，metadata 驱动）。enum/ref 显示 __label。 */
export function CustomFieldDescriptions({
  objectCode,
  extra,
}: {
  objectCode: string;
  extra: Record<string, unknown> | null | undefined;
}) {
  const { data } = useCfFields(objectCode);
  const defs = data ?? [];
  if (defs.length === 0) return null;

  return (
    <Descriptions title="扩展信息" column={1} size="small" bordered style={{ marginTop: 16 }}>
      {defs.map((d) => (
        <Descriptions.Item key={d.id} label={d.fieldName}>
          {renderCfValue((d.type || '').toLowerCase(), d.fieldCode, extra ?? {})}
        </Descriptions.Item>
      ))}
    </Descriptions>
  );
}
