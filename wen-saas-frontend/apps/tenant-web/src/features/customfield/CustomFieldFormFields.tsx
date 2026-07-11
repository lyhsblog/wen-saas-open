import { DatePicker, Form, Input, InputNumber, Switch } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useCfFields } from './customFieldQueries';
import { EntityRefPicker } from './EntityRefPicker';
import { DictEnumSelect } from './DictEnumSelect';

/**
 * 自定义字段动态表单段（由 listFields 元数据驱动），放进父 antd Form 内，字段挂在 custom.* 下。
 * 控件：text→Input、long_text→TextArea、number→InputNumber、bool→Switch、
 * date→DatePicker（getValueProps/normalize 做 dayjs ↔ 字符串换算，表单值仍是 "YYYY-MM-DD" 字符串）、
 * enum→字典下拉(DictEnumSelect，按 refDictCode)、ref→对象选择器(EntityRefPicker)。
 * refLabels：编辑回显时的 ref 字段展示名（fieldCode→展示名，来自详情 extra 的 __label 衍生键），
 * 让 ref 选择器不必重新搜索即可显示名字而非 id。
 */
export function CustomFieldFormFields({
  objectCode,
  refLabels,
}: {
  objectCode: string;
  refLabels?: Record<string, string>;
}) {
  const { data } = useCfFields(objectCode);
  const defs = data ?? [];
  if (defs.length === 0) return null;

  return (
    <>
      {defs.map((d) => {
        const type = (d.type || '').toLowerCase();
        const name = ['custom', d.fieldCode];
        if (type === 'bool') {
          return (
            <Form.Item key={d.id} name={name} label={d.fieldName} valuePropName="checked">
              <Switch />
            </Form.Item>
          );
        }
        const rules = d.required ? [{ required: true, message: `${d.fieldName}必填` }] : [];
        let control: React.ReactNode;
        switch (type) {
          case 'long_text':
            control = <Input.TextArea rows={3} maxLength={4000} />;
            break;
          case 'number':
            control = <InputNumber style={{ width: '100%' }} />;
            break;
          case 'ref':
            control = d.refObjectCode ? (
              <EntityRefPicker
                objectCode={d.refObjectCode}
                style={{ width: '100%' }}
                {...(refLabels?.[d.fieldCode] ? { label: refLabels[d.fieldCode] } : {})}
              />
            ) : (
              <Input placeholder="关联对象 ID" />
            );
            break;
          case 'date':
            control = <DatePicker style={{ width: '100%' }} format="YYYY-MM-DD" />;
            break;
          case 'enum':
            control = d.refDictCode ? (
              <DictEnumSelect dictCode={d.refDictCode} style={{ width: '100%' }} allowClear />
            ) : (
              <Input placeholder="字典项值" />
            );
            break;
          default:
            control = <Input maxLength={255} />;
        }
        // date 的表单值保持 "YYYY-MM-DD" 字符串（对外契约不变），仅在 DatePicker 边界换算 dayjs
        const dateValueProps =
          type === 'date'
            ? {
                getValueProps: (v: unknown) => ({
                  value: v ? dayjs(v as string) : undefined,
                }),
                normalize: (v: Dayjs | null) => (v ? v.format('YYYY-MM-DD') : undefined),
              }
            : {};
        return (
          <Form.Item key={d.id} name={name} label={d.fieldName} rules={rules} {...dateValueProps}>
            {control}
          </Form.Item>
        );
      })}
    </>
  );
}
