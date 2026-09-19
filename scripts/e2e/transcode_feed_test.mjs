/**
 * 批次四 E2E：FFmpeg 异步转码 + 沉浸式 Feed 游标分页 + 完播上报
 * 1 发布 mpeg4/mov 视频（走重编码路径）→ 处理中不可见 → 完成后可见且 codec=h264
 * 2 发布 H.264 mp4（走 remux 快速路径）→ duration/size 修正 + 无封面时自动抽封面
 * 3 /video/feed 热度排序 + 游标分页（两页无交集）
 * 4 /video/playReport 完播上报落库（≥90% 记完播）
 * 用法: node scripts/e2e/transcode_feed_test.mjs
 */
const GATEWAY = 'http://localhost:38080/live/api'
const { execSync } = await import('node:child_process')
const fs = await import('node:fs')

function log(step, ok, detail = '') {
  console.log(`${ok ? '✅' : '❌'} [${step}] ${detail}`)
  if (!ok) process.exitCode = 1
}

async function api(path, token, { query = {}, body = null } = {}) {
  const qs = Object.entries(query).map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join('&')
  const res = await fetch(`${GATEWAY}${path}${qs ? '?' + qs : ''}`, {
    method: 'POST',
    headers: { 'token': token, 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : null
  })
  return res.json()
}

async function uploadVideo(token, filePath, filename) {
  const buf = fs.readFileSync(filePath)
  const form = new FormData()
  form.append('file', new Blob([buf]), filename)
  const res = await fetch(`${GATEWAY}/video/uploadVideo`, {
    method: 'POST',
    headers: { 'token': token },
    body: form
  })
  return res.json()
}

async function login(phone) {
  await api('/userLogin/sendLoginCode', '', { query: { phone } })
  const vo = await api('/userLogin/login', '', { query: { phone, code: '123456' } })
  if (vo.code !== 200) throw new Error(`登录失败: ${vo.msg}`)
  return { token: vo.data.token, userId: vo.data.userId }
}

const sleep = (ms) => new Promise(r => setTimeout(r, ms))

async function waitTranscode(token, videoId, timeoutMs = 180000) {
  const start = Date.now()
  while (Date.now() - start < timeoutMs) {
    const d = await api('/video/detail', token, { query: { id: videoId } })
    if (d.code === 200 && d.data?.item) {
      const it = d.data.item
      if (it.transcodeStatus === 1) return it
      if (it.transcodeStatus === 2) throw new Error(`转码失败 videoId=${videoId}`)
    }
    await sleep(3000)
  }
  throw new Error(`转码超时 videoId=${videoId}`)
}

function codecOf(file) {
  const out = execSync(`ffprobe -v quiet -print_format json -show_streams "${file}"`, { encoding: 'utf-8' })
  const streams = JSON.parse(out).streams || []
  return streams.find(s => s.codec_type === 'video')?.codec_name
}

const run = async () => {
  const A = await login('13800138000')
  log('login', true, `A=${A.userId}`)
  const tmp = fs.mkdtempSync('video-e2e-')

  // ---- 1. 重编码路径：mpeg4 编码 .mov ----
  execSync('ffmpeg -y -f lavfi -i testsrc=duration=5:size=320x240:rate=15 ' +
    '-f lavfi -i sine=duration=5 -c:v mpeg4 -c:a mp2 -pix_fmt yuv420p video-e2e-reenc.mov', { cwd: process.cwd() })
  const up1 = await uploadVideo(A.token, 'video-e2e-reenc.mov', 'reenc.mov')
  log('上传mov', up1.code === 200, up1.code === 200 ? 'ok' : JSON.stringify(up1))
  const pub1 = await api('/video/publish', A.token, { body: { title: '批次四重编码测试', videoUrl: up1.data } })
  const v1 = pub1.data
  log('发布mov立即返回', pub1.code === 200 && !!v1, `videoId=${v1}`)
  const d0 = await api('/video/detail', A.token, { query: { id: v1 } })
  log('发布后详情不可见(处理中)', d0.code !== 200 || !d0.data?.item, `code=${d0.code}`)
  const t1 = await waitTranscode(A.token, v1)
  // 产物在 MinIO：先下载到本地再 ffprobe 探测编码
  const dlRes = await fetch(t1.videoUrl.replace('http://192.168.31.252:3000/minio', 'http://127.0.0.1:9000'))
  fs.writeFileSync('video-e2e-out.mp4', Buffer.from(await dlRes.arrayBuffer()))
  const out = execSync('ffprobe -v quiet -print_format json -show_streams video-e2e-out.mp4', { encoding: 'utf-8' })
  const outCodec = (JSON.parse(out).streams || []).find(s => s.codec_type === 'video')?.codec_name
  log('重编码产物为h264', outCodec === 'h264', `codec=${outCodec}`)
  log('重编码视频可见且封面已抽', t1.transcodeStatus === 1 && !!t1.coverUrl, `cover=${!!t1.coverUrl}`)
  fs.unlinkSync('video-e2e-out.mp4')

  // ---- 2. remux 快速路径：H.264+AAC mp4，带用户封面（duration 前端上报故意错，ffprobe 修正）----
  execSync('ffmpeg -y -f lavfi -i testsrc=duration=6:size=320x240:rate=15 ' +
    '-f lavfi -i sine=duration=6 -c:v libx264 -preset veryfast -c:a aac -pix_fmt yuv420p video-e2e-remux.mp4', { cwd: process.cwd() })
  const up2 = await uploadVideo(A.token, 'video-e2e-remux.mp4', 'remux.mp4')
  const pub2 = await api('/video/publish', A.token, {
    body: { title: '批次四remux测试', videoUrl: up2.data, duration: 999, size: 1 }
  })
  const v2 = pub2.data
  const t2 = await waitTranscode(A.token, v2)
  log('remux后duration被修正', t2.duration === 6, `duration=${t2.duration}（原上报999）`)
  log('remux后size被修正', t2.size > 1000, `size=${t2.size}`)

  // ---- 审核流：发布默认审核中(2)，admin 通过后进 feed ----
  const AL = await fetch('http://localhost:38100/live/admin/auth/login?username=admin&password=admin123', { method: 'POST' }).then(r => r.json())
  const adminToken = AL.data?.token || AL.data
  const rev = async (vid, pass) => fetch(`http://localhost:38100/live/admin/video/review?id=${vid}&pass=${pass}`,
    { method: 'POST', headers: { adminToken } }).then(r => r.json())
  await rev(v1, true)
  await rev(v2, true)

  // ---- 3. feed 热度排序 + 游标分页 ----
  const page1 = await api('/video/feed', A.token, { query: { size: 8 } })
  const p1 = page1.data || []
  log('feed首页返回', page1.code === 200 && p1.length === 8, `count=${p1.length}`)
  const lastId = p1[p1.length - 1].id
  const page2 = await api('/video/feed', A.token, { query: { lastId, size: 8 } })
  const p2 = page2.data || []
  const overlap = p2.filter(v => p1.some(x => x.id === v.id))
  log('feed游标分页无重叠', p2.length > 0 && overlap.length === 0, `page2=${p2.length} overlap=${overlap.length}`)
  const feedHasNew = [...p1, ...p2].some(v => Number(v.id) === Number(v2))
  log('feed包含刚转码视频', feedHasNew, '')

  // ---- 4. 完播上报 ----
  await api('/video/playReport', A.token, { query: { videoId: v2, watchedSeconds: 6, duration: 6 } })
  await api('/video/playReport', A.token, { query: { videoId: v1, watchedSeconds: 2, duration: 5 } })
  await sleep(800)
  const rows = execSync(
    `D:/Mysql/bin/mysql -uroot -p123456 -N -e "use qiyu_live_video; SELECT video_id, is_complete FROM t_video_play_log WHERE user_id=${A.userId} ORDER BY id DESC LIMIT 2"`,
    { shell: true, encoding: 'utf-8' }).trim().split('\n')
  const complete = rows.some(r => r.startsWith(v2 + '\t1'))
  const incomplete = rows.some(r => r.startsWith(v1 + '\t0'))
  log('完播上报落库(6/6完播, 2/5未完播)', complete && incomplete, rows.join(' | '))

  // 清理临时文件
  fs.unlinkSync('video-e2e-reenc.mov')
  fs.unlinkSync('video-e2e-remux.mp4')
  fs.rmSync(tmp, { recursive: true, force: true })
  console.log('\n批次四 E2E 结束')
}

run().catch(e => { console.error('❌ 测试异常:', e); process.exitCode = 1 })
