// ⑥ 主播刷新不关播 E2E：
// A. 无推流 + 主播断线不回 → 30s后自动关播（兜底行为保留）
// B. 主播断线后30s内重连 → 不关播（模拟刷新浏览器回来）
// C. 推流存活(stream_status=1) + 主播断线 → 不关播（模拟OBS推流刷新浏览器）

const GATEWAY = 'http://localhost:38080/live/api'
async function api(path, token, { query = {}, body = null } = {}) {
  const qs = Object.entries(query).map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join('&')
  const res = await fetch(`${GATEWAY}${path}${qs ? '?' + qs : ''}`, {
    method: 'POST',
    headers: { 'token': token, 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : null
  })
  return res.json()
}
async function login(phone) {
  await api('/userLogin/sendLoginCode', '', { query: { phone } })
  const login = await api('/userLogin/login', '', { query: { phone, code: '123456' } })
  if (login.code !== 200) throw new Error('登录失败 ' + phone)
  return { token: login.data.token, userId: login.data.userId }
}
function sleep(ms) { return new Promise(r => setTimeout(r, ms)) }

async function roomStatus(roomId) {
  // 查询走 api 而非直连 DB（脚本环境无 mysql2）：list 按类型过滤，两类型都查再合并
  let inList = null
  for (const type of [1, 2]) {
    const res = await fetch(`${GATEWAY}/living/list?type=${type}&page=1&pageSize=100`, { method: 'POST' })
    const vo = await res.json()
    inList = inList || (vo.data?.list || []).find(r => Number(r.roomId ?? r.id) === Number(roomId))
  }
  return { status: inList ? 1 : 0, raw: inList || null }
}
async function wsJoin(cfg, token, userId, roomId) {
  const ws = new WebSocket(`ws://127.0.0.1:38115/${userId}/1001/${roomId}`)
  await new Promise(r => { ws.onopen = r; ws.onerror = () => r() })
  // 必须发送 1001 登录包完成鉴权+注册（握手不再携带 token 鉴权）
  const loginBody = JSON.stringify({ appId: 10001, userId, token: cfg.token })
  ws.send(JSON.stringify({ magic: 19231, code: 1001, len: loginBody.length, body: loginBody }))
  await sleep(800)
  return ws
}

let ok = true
const assert = (name, cond, extra = '') => {
  console.log(`${cond ? 'PASS' : 'FAIL'} | ${name}${extra ? ' | ' + extra : ''}`)
  if (!cond) ok = false
}

;(async () => {
  // 场景A：无推流，断线不回 → 30s后关播
  {
    const a = await login('13900999601')
    const room = await api('/living/startingLiving', a.token, { query: { type: 1, roomName: '断线测试A', covertImg: 'x' } })
    const roomId = room.data.roomId
    const cfg = await api('/im/getImConfig', a.token)
    const ws = await wsJoin(cfg.data, a.token, a.userId, roomId)
    ws.close() // 主播断线（不回来，模拟关浏览器且超时）
    console.log('场景A：已断线，等待35s宽限期判定...')
    await sleep(36000)
    const st = await roomStatus(roomId)
    assert('A 无推流断线超时 → 自动关播', Number(st.status) === 0, `status=${st.status}`)
  }

  // 场景B：断线后20s内重连 → 不关播
  {
    const a = await login('13900999602')
    const room = await api('/living/startingLiving', a.token, { query: { type: 1, roomName: '断线测试B', covertImg: 'x' } })
    const roomId = room.data.roomId
    const cfg = await api('/im/getImConfig', a.token)
    const ws1 = await wsJoin(cfg.data, a.token, a.userId, roomId)
    ws1.close() // 模拟刷新（断线）
    console.log('场景B：断线，20s后重连...')
    await sleep(20000)
    const ws2 = await wsJoin(cfg.data, a.token, a.userId, roomId) // 刷新回来重连
    await sleep(18000) // 越过30s判定点
    const st = await roomStatus(roomId)
    assert('B 30s内重连 → 直播继续', Number(st.status) === 1, `status=${st.status}`)
    // 清理：直接关播
    const my = await api('/living/myLivingRoom', a.token)
    assert('B myLivingRoom 返回roomId', Number(my.data) === Number(roomId), `data=${my.data}`)
    await api('/living/closeLiving', a.token, { query: { roomId } })
    ws2.close()
  }

  // 场景C：推流存活(OBS) + 主播断线 → 不关播
  {
    const a = await login('13900999603')
    const room = await api('/living/startingLiving', a.token, { query: { type: 2, roomName: '断线测试C', covertImg: 'x' } })
    const roomId = room.data.roomId
    // 模拟OBS推流中：直接置 stream_status=1
    const { execSync } = await import('node:child_process')
    execSync(`D:/Mysql/bin/mysql -uroot -p123456 -e "use qiyu_live_living; UPDATE t_living_room SET stream_status=1 WHERE id=${roomId}"`, { shell: true })
    const cfg = await api('/im/getImConfig', a.token)
    const ws = await wsJoin(cfg.data, a.token, a.userId, roomId)
    ws.close()
    console.log('场景C：推流中主播断线，等待35s判定...')
    await sleep(36000)
    const st = await roomStatus(roomId)
    assert('C 推流存活 → 直播继续', Number(st.status) === 1, `status=${st.status}`)
    // 清理
    await api('/living/closeLiving', a.token, { query: { roomId } })
  }

  console.log(ok ? '\n全部通过 ✅' : '\n存在失败 ❌')
  process.exit(ok ? 0 : 1)
})().catch(e => { console.error('E2E异常', e); process.exit(1) })
