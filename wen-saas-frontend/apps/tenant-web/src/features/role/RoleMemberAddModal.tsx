import { useState } from 'react';
import { App, Modal, Select, Spin } from 'antd';
import { describeApiError } from '@wen-saas/api-client';
import { memberApi } from '../member/memberApi';
import { useAddRoleMembers } from './roleQueries';

type Props = {
  open: boolean;
  roleId: string;
  onClose: () => void;
};

type Opt = { value: string; label: string };

/** 添加成员到角色：按姓名/账号检索（T3② 成员搜索）。 */
export function RoleMemberAddModal({ open, roleId, onClose }: Props) {
  const { message } = App.useApp();
  const [options, setOptions] = useState<Opt[]>([]);
  const [selected, setSelected] = useState<string[]>([]);
  const [searching, setSearching] = useState(false);
  const addMembers = useAddRoleMembers(roleId);

  const search = async (kw: string) => {
    setSearching(true);
    try {
      const items = await memberApi.search(kw, 20);
      setOptions(
        items.map((m) => ({
          value: m.id,
          label: `${m.realName ?? m.username}（${m.username}）${m.mainDeptName ? ' · ' + m.mainDeptName : ''}`,
        })),
      );
    } catch {
      // 静默：检索失败不打断
    } finally {
      setSearching(false);
    }
  };

  const submit = async () => {
    if (selected.length === 0) {
      onClose();
      return;
    }
    try {
      // userId 为雪花字符串；后端 Long 解析，沿用字符串避免精度丢失
      await addMembers.mutateAsync({ userIds: selected as unknown as number[] });
      message.success(`已添加 ${selected.length} 名成员`);
      setSelected([]);
      setOptions([]);
      onClose();
    } catch (err) {
      message.error(describeApiError(err, '添加失败'));
    }
  };

  return (
    <Modal
      open={open}
      title="添加成员到此角色"
      okText="添加"
      cancelText="取消"
      confirmLoading={addMembers.isPending}
      onOk={() => void submit()}
      onCancel={onClose}
      destroyOnHidden
      width={480}
    >
      <Select
        mode="multiple"
        value={selected}
        onChange={setSelected}
        onSearch={(v) => void search(v)}
        filterOption={false}
        options={options}
        placeholder="输入姓名 / 账号检索"
        style={{ width: '100%' }}
        notFoundContent={searching ? <Spin size="small" /> : '输入关键字检索成员'}
        maxTagCount="responsive"
      />
    </Modal>
  );
}
