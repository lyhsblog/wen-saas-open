import { App, Upload } from 'antd';
import { CameraOutlined } from '@ant-design/icons';
import ImgCrop from 'antd-img-crop';
import { useMutation } from '@tanstack/react-query';
import { describeApiError } from '@wen-saas/api-client';
import { profileApi } from './profileApi';
import { useInvalidateProfile } from './profileQueries';

const AVATAR_MAX_BYTES = 1024 * 1024; // 1MB
const AVATAR_MIME = ['image/png', 'image/jpeg', 'image/jpg'];

/**
 * 独立「更换头像」入口（O5-1/O5-4）：即传即存（上传端点直接回写 avatar_file_id 落库），
 * 不再放在「编辑基本信息」内与保存/取消并列；点击进入裁剪框，限制 JPG/PNG ≤ 1MB。
 */
export function AvatarUploader({
  avatarUrl,
  fallback,
  size = 80,
}: {
  avatarUrl?: string | null | undefined;
  fallback: string;
  size?: number;
}) {
  const { message } = App.useApp();
  const invalidate = useInvalidateProfile();
  const uploadAvatar = useMutation({
    mutationFn: (file: File) => profileApi.uploadAvatar(file),
    onSuccess: async () => {
      message.success('头像已更新');
      await invalidate();
    },
    onError: (e) => message.error(describeApiError(e, '头像上传失败')),
  });

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
          message.error('头像仅支持 JPG / PNG 格式');
          return false;
        }
        if (file.size > AVATAR_MAX_BYTES) {
          message.error('头像文件大小不能超过 1MB');
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
        <div
          style={{
            position: 'relative',
            width: size,
            height: size,
            margin: '0 auto 12px',
            cursor: 'pointer',
          }}
          title="更换头像"
        >
          <div
            style={{
              width: size,
              height: size,
              borderRadius: '50%',
              background: avatarUrl ? `center/cover url(${avatarUrl})` : 'var(--brand)',
              color: '#fff',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 700,
              fontSize: size / 3,
            }}
          >
            {!avatarUrl && fallback}
          </div>
          <span
            style={{
              position: 'absolute',
              right: 0,
              bottom: 0,
              width: 26,
              height: 26,
              borderRadius: '50%',
              background: 'var(--brand)',
              color: '#fff',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 13,
              border: '2px solid var(--bg-elev, #fff)',
            }}
          >
            <CameraOutlined />
          </span>
        </div>
      </Upload>
    </ImgCrop>
  );
}
