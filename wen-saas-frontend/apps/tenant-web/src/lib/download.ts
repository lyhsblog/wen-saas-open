/** 触发浏览器下载一个 Blob（错误明细 / 模板 / 结果文件等 xlsx 用）。 */
export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  // 释放放到下一帧，避免部分浏览器尚未开始下载就被回收。
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
