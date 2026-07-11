import { App, Avatar, Upload } from 'antd';
import { CameraOutlined, UserOutlined } from '@ant-design/icons';
import ImgCrop from 'antd-img-crop';
import { useUploadAvatar } from '../profileQueries';

const AVATAR_MAX_BYTES = 1024 * 1024; // 1MB
const AVATAR_MIME = ['image/png', 'image/jpeg', 'image/jpg'];

/**
 * 独立「更换头像」入口（O5-1/O5-4）：即传即存（上传端点直接回写 avatar_file_id 落库），
 * 不再放在「编辑基本信息」弹窗内与保存/取消并列；点击进入裁剪框，限制 JPG/PNG ≤ 1MB。
 */
export function AvatarUploader({
  avatarUrl,
  initial,
  size = 72,
}: {
  avatarUrl?: string | null | undefined;
  initial: string;
  size?: number;
}) {
  const { message } = App.useApp();
  const uploadAvatar = useUploadAvatar();

  return (
    <ImgCrop
      rotationSlider
      cropShape="round"
      aspect={1}
      quality={0.9}
      modalTitle="裁剪头像"
      modalOk="确认"
      modalCancel="取消"
      beforeCrop={(file) => {
        if (!AVATAR_MIME.includes(file.type)) {
          void message.error('头像仅支持 JPG / PNG 格式');
          return false;
        }
        if (file.size > AVATAR_MAX_BYTES) {
          void message.error('头像文件大小不能超过 1MB');
          return false;
        }
        return true;
      }}
    >
      <Upload
        showUploadList={false}
        accept="image/png,image/jpeg"
        disabled={uploadAvatar.isPending}
        customRequest={({ file, onSuccess, onError }) => {
          void (async () => {
            try {
              await uploadAvatar.mutateAsync(file as File);
              onSuccess?.({});
            } catch (e) {
              onError?.(e as Error);
            }
          })();
        }}
      >
        <div style={{ position: 'relative', cursor: 'pointer', lineHeight: 0 }} title="更换头像">
          <Avatar
            size={size}
            src={avatarUrl ?? undefined}
            icon={!avatarUrl ? <UserOutlined /> : undefined}
            style={{ background: 'var(--brand-bg)', color: 'var(--brand)', fontSize: size / 2.6 }}
          >
            {!avatarUrl ? initial : null}
          </Avatar>
          <span
            style={{
              position: 'absolute',
              right: 0,
              bottom: 0,
              width: 24,
              height: 24,
              borderRadius: '50%',
              background: 'var(--brand)',
              color: '#fff',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 12,
              border: '2px solid var(--bg-elev)',
            }}
          >
            <CameraOutlined />
          </span>
        </div>
      </Upload>
    </ImgCrop>
  );
}
